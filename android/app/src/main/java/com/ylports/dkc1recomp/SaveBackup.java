package com.ylports.dkc1recomp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Private save backups. All entries are allow-listed; ROMs and executable content cannot be imported. */
public final class SaveBackup {
    private static final String MARKER="DKC1Recomp save backup v1\n";
    private static final long MAX_FILE=16L*1024*1024,MAX_TOTAL=96L*1024*1024;
    private SaveBackup(){}
    public static boolean allowed(String name){
        return name.equals("game/dkc1.srm")||name.equals("config/settings-v3.json")||
            name.matches("game/(autosave|quicksave)-(4x3|16x10|16x9|21x9)(-slot[2-5])?\\.state");
    }
    private static void copy(InputStream in,OutputStream out,long limit)throws IOException{
        byte[] buffer=new byte[16384];long total=0;int n;
        while((n=in.read(buffer))!=-1){if(n==0)continue;total+=n;if(total>limit)throw new IOException("La copia supera el tamaño permitido.");out.write(buffer,0,n);}
    }
    public static void exportTo(File root,OutputStream output)throws IOException{
        if(output==null)throw new IOException("No se pudo abrir el destino.");
        try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(output))){
            out.putNextEntry(new ZipEntry("FORMAT.txt"));out.write(MARKER.getBytes(StandardCharsets.UTF_8));out.closeEntry();
            long total=0;
            for(String sub:new String[]{"game","config"}){
                File[] list=new File(root,sub).listFiles();if(list==null)continue;
                Arrays.sort(list,(a,b)->a.getName().compareTo(b.getName()));
                for(File f:list){String name=sub+"/"+f.getName();if(!f.isFile()||!allowed(name))continue;
                    long size=f.length();total+=size;if(size>MAX_FILE||total>MAX_TOTAL)throw new IOException("Las partidas son demasiado grandes.");
                    ZipEntry entry=new ZipEntry(name);entry.setTime(f.lastModified());out.putNextEntry(entry);
                    try(InputStream in=new FileInputStream(f)){copy(in,out,MAX_FILE);}out.closeEntry();
                }
            }
        }
    }
    private static void remove(File file){File[] list=file.listFiles();if(list!=null)for(File f:list)remove(f);file.delete();}
    public static int importFrom(File root,File cache,InputStream input)throws IOException{
        if(input==null)throw new IOException("No se pudo abrir la copia.");
        File stage=new File(cache,"restore-"+UUID.randomUUID());if(!stage.mkdirs())throw new IOException("No se pudo preparar la restauración.");
        Set<String> names=new LinkedHashSet<>();boolean marker=false;long total=0;int count=0;
        try{
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))){
                ZipEntry e;
                while((e=zip.getNextEntry())!=null){
                    String name=e.getName();if(++count>40||e.isDirectory())throw new IOException("Formato de copia no válido.");
                    if(name.equals("FORMAT.txt")){
                        if(marker)throw new IOException("Cabecera duplicada.");ByteArrayOutputStream bytes=new ByteArrayOutputStream();copy(zip,bytes,128);
                        if(!MARKER.equals(new String(bytes.toByteArray(),StandardCharsets.UTF_8)))throw new IOException("Esta no es una copia de DKC1Recomp.");marker=true;
                    }else{
                        if(!allowed(name)||!names.add(name))throw new IOException("Archivo no permitido o duplicado en la copia: "+name);
                        File f=new File(stage,name);if(!f.getParentFile().isDirectory()&&!f.getParentFile().mkdirs())throw new IOException("No se pudo crear la carpeta temporal.");
                        long cap=name.endsWith(".json")?65536:name.endsWith(".srm")?131072:MAX_FILE;
                        try(FileOutputStream out=new FileOutputStream(f)){copy(zip,out,cap);out.getFD().sync();}
                        total+=f.length();if(f.length()==0||total>MAX_TOTAL)throw new IOException("Tamaño de copia no válido.");
                    }
                    zip.closeEntry();
                }
            }
            if(!marker||names.isEmpty())throw new IOException("La copia está vacía o incompleta.");
            // Only commit after every entry, CRC and size check passed.
            List<String> moved=new ArrayList<>(),installed=new ArrayList<>();
            try{
                for(String name:names){File target=new File(root,name),old=new File(stage,"previous/"+name),fresh=new File(stage,name);
                    if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("No se pudo crear la carpeta de destino.");
                    if(target.exists()){
                        old.getParentFile().mkdirs();if(!target.renameTo(old))throw new IOException("No se pudo preservar la partida anterior.");moved.add(name);
                    }
                    if(!fresh.renameTo(target))throw new IOException("No se pudo instalar la partida.");installed.add(name);
                }
            }catch(IOException failure){
                boolean rollback=true;
                for(String name:installed)rollback&=new File(root,name).delete();
                for(String name:moved)rollback&=new File(stage,"previous/"+name).renameTo(new File(root,name));
                if(!rollback)throw new IOException("Restauración interrumpida; copias anteriores conservadas en "+stage,failure);
                throw failure;
            }
            remove(stage);return names.size();
        }catch(IOException failure){
            // Preserve rollback material after a filesystem failure; harmless staging is removable.
            if(!new File(stage,"previous").exists())remove(stage);
            throw failure;
        }
    }
}
