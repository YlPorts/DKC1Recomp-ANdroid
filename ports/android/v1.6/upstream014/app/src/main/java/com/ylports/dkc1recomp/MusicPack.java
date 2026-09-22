package com.ylports.dkc1recomp;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * Transactional MSU-1 importer.
 *
 * v1.6 keeps the existing on-disk format (music/current/track-N.pcm), but
 * accepts normal MSU-1 filenames and streams large PCM files instead of
 * applying the old 8 MiB auxiliary-file ceiling to misclassified tracks.
 */
public final class MusicPack {
    public static final long MAX_TRACK=2L*1024*1024*1024;
    public static final long MAX_TOTAL=16L*1024*1024*1024;
    private static final long MAX_AUXILIARY=64L*1024*1024;
    private static final long SPACE_RESERVE=32L*1024*1024;

    /*
     * Accept the last 1-3 digit group before the .pcm suffix while allowing
     * human-readable text after the number. Examples:
     *   track-1.pcm, DKC1_27.pcm, Donkey Kong Country HD - 02.pcm,
     *   01 - Jungle Hijinxs.pcm.
     * Four-digit years such as 1994 are deliberately not treated as tracks.
     */
    private static final Pattern TRACK=Pattern.compile("(?:^|\\D)(\\d{1,3})(?:\\D*)\\.pcm$",Pattern.CASE_INSENSITIVE);

    public interface Source {void copy(Writer writer)throws IOException;}

    public static final class Writer {
        private final File stage;
        private final Set<Integer> found=new HashSet<>();
        private long total;
        private int entries;
        Writer(File stage){this.stage=stage;}

        public void add(String name,InputStream in)throws IOException{
            if(in==null)throw new IOException("No se pudo abrir un archivo del pack.");
            if(++entries>512)throw new IOException("Demasiados archivos en el pack.");
            String clean=name==null?"":name.replace('\\','/');
            if(clean.isEmpty())return;
            if(clean.startsWith("/")||clean.indexOf(':')>=0||clean.indexOf('\0')>=0)
                throw new IOException("Ruta no válida en el pack.");
            for(String part:clean.split("/"))if(part.equals(".."))
                throw new IOException("Ruta no válida en el pack.");
            if(clean.endsWith("/"))return;

            String base=clean.substring(clean.lastIndexOf('/')+1);
            Matcher matcher=TRACK.matcher(base);
            boolean pcm=matcher.find();
            if(!pcm&&base.toLowerCase(Locale.ROOT).endsWith(".pcm"))
                throw new IOException("No se pudo detectar el número de pista MSU-1 en: "+base);

            int id=pcm?Integer.parseInt(matcher.group(1)):0;
            if(pcm&&(id<1||id>32))
                throw new IOException("Pista MSU-1 fuera de rango (1-32): "+base);
            if(pcm&&!found.add(id))
                throw new IOException("Pista MSU-1 duplicada: "+id+" ("+base+")");

            File target=pcm?new File(stage,"track-"+id+".pcm"):null;
            long count=0,sinceSpaceCheck=0;
            byte[] buffer=new byte[64*1024];
            int n;
            try(OutputStream out=pcm
                    ?new BufferedOutputStream(new FileOutputStream(target),256*1024)
                    :new OutputStream(){@Override public void write(int b){} @Override public void write(byte[] b,int o,int l){}}){
                while((n=in.read(buffer))!=-1){
                    if(n==0)continue;
                    count+=n;total+=n;
                    long perFileLimit=pcm?MAX_TRACK:MAX_AUXILIARY;
                    if(count>perFileLimit)
                        throw new IOException(pcm?"La pista MSU-1 es demasiado grande: "+base:"Archivo auxiliar demasiado grande dentro del pack: "+base);
                    if(total>MAX_TOTAL)throw new IOException("El pack MSU-1 supera el máximo de seguridad de 16 GiB.");
                    if(pcm){
                        sinceSpaceCheck+=n;
                        if(sinceSpaceCheck>=8L*1024*1024){
                            sinceSpaceCheck=0;long usable=stage.getUsableSpace();
                            if(usable>0&&usable<SPACE_RESERVE)
                                throw new IOException("No hay espacio libre suficiente para importar el pack MSU-1.");
                        }
                    }
                    out.write(buffer,0,n);
                }
            }catch(IOException error){if(target!=null&&target.exists())target.delete();throw error;}
            if(pcm)validatePcm(target);
        }

        void complete()throws IOException{
            List<Integer> missing=new ArrayList<>();for(int i=1;i<=27;i++)if(!found.contains(i))missing.add(i);
            if(!missing.isEmpty())throw new IOException("Faltan pistas MSU-1: "+missing+". Se conserva el pack anterior.");
        }
    }

    public static void validatePcm(File file)throws IOException{
        long size=file.length();if(size<16||(size-8)%4!=0||size>MAX_TRACK)throw new IOException("PCM inválido: "+file.getName());
        byte[] h=new byte[8];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(h);}
        if(h[0]!='M'||h[1]!='S'||h[2]!='U'||h[3]!='1')throw new IOException("No es PCM MSU-1: "+file.getName());
        long loop=(h[4]&255L)|((h[5]&255L)<<8)|((h[6]&255L)<<16)|((h[7]&255L)<<24);
        if(loop>=(size-8)/4&&loop!=0xffffffffL)throw new IOException("Punto de bucle inválido: "+file.getName());
    }

    public static synchronized void install(File files,Source source)throws IOException{
        File root=new File(files,"music");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("No se pudo crear la carpeta de música.");
        File current=new File(root,"current"),old=new File(root,"previous");recover(root);
        File stage=new File(root,"stage-"+UUID.randomUUID());if(!stage.mkdir())throw new IOException("No se pudo preparar el pack.");
        boolean movedOld=false;
        try{
            Writer writer=new Writer(stage);source.copy(writer);writer.complete();
            if(current.exists()){if(old.exists())delete(old);if(!current.renameTo(old))throw new IOException("No se pudo conservar el pack anterior.");movedOld=true;}
            if(!stage.renameTo(current)){if(movedOld)old.renameTo(current);throw new IOException("No se pudo instalar el pack.");}
            if(old.exists())delete(old);
        }finally{if(stage.exists())delete(stage);}
    }

    public static void fromZip(File files,InputStream input)throws IOException{
        if(input==null)throw new IOException("No se pudo abrir el pack.");
        install(files,writer->{try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input,256*1024))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){writer.add(entry.getName(),zip);zip.closeEntry();}}});
    }
    public static void recover(File root)throws IOException{File current=new File(root,"current"),old=new File(root,"previous");if(!current.exists()&&old.exists()&&!old.renameTo(current))throw new IOException("No se pudo recuperar el pack anterior.");}
    public static boolean isComplete(File dir){if(!dir.isDirectory())return false;for(int i=1;i<=27;i++)if(!new File(dir,"track-"+i+".pcm").isFile())return false;return true;}
    public static synchronized void remove(File files)throws IOException{File root=new File(files,"music");recover(root);File current=new File(root,"current");if(current.exists())delete(current);}
    static void delete(File file)throws IOException{File[] children=file.listFiles();if(children!=null)for(File f:children)delete(f);if(file.exists()&&!file.delete())throw new IOException("No se pudo eliminar "+file.getName());}
    private MusicPack(){}
}