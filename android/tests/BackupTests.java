package com.ylports.dkc1recomp;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
public final class BackupTests {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Backup/layout check "+checks);}
    private static byte[] zip(String... pairs)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(bytes)) {for(int i=0;i<pairs.length;i+=2){z.putNextEntry(new ZipEntry(pairs[i]));z.write(pairs[i+1].getBytes(StandardCharsets.UTF_8));z.closeEntry();}}
        return bytes.toByteArray();
    }
    private static void reject(File root,File cache,byte[] data)throws IOException {
        byte[] before=Files.readAllBytes(new File(root,"game/dkc1.srm").toPath());
        try{SaveBackup.importFrom(root,cache,new ByteArrayInputStream(data));throw new AssertionError("Invalid ZIP accepted");}catch(IOException expected){checks++;}
        check(Arrays.equals(before,Files.readAllBytes(new File(root,"game/dkc1.srm").toPath())));
    }
    private static void remove(File f){File[] files=f.listFiles();if(files!=null)for(File child:files)remove(child);f.delete();}
    public static void main(String[] args)throws Exception {
        File work=Files.createTempDirectory("dkc1-backup-test-").toFile();
        try {
            File root=new File(work,"files"),cache=new File(work,"cache"),dest=new File(work,"restored");cache.mkdirs();new File(root,"game").mkdirs();new File(root,"config").mkdirs();
            String[] names={"game/dkc1.srm","game/quicksave-4x3.state","game/quicksave-21x9-slot5.state","game/autosave-16x9.state","config/settings-v3.json"};
            for(String n:names)Files.write(new File(root,n).toPath(),("TEST:"+n).getBytes(StandardCharsets.UTF_8));
            Files.write(new File(root,"game/dkc1.sfc").toPath(),new byte[13]);
            ByteArrayOutputStream out=new ByteArrayOutputStream();SaveBackup.exportTo(root,out);
            try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))){ZipEntry e;int count=0;while((e=in.getNextEntry())!=null){check(!e.getName().endsWith(".sfc"));check(e.getName().equals("FORMAT.txt")||SaveBackup.allowed(e.getName()));count++;}check(count==names.length+1);}
            check(SaveBackup.importFrom(dest,cache,new ByteArrayInputStream(out.toByteArray()))==names.length);
            for(String n:names)check(Arrays.equals(Files.readAllBytes(new File(root,n).toPath()),Files.readAllBytes(new File(dest,n).toPath())));
            final String marker="DKC1Recomp save backup v1\n";
            for(String bad:new String[]{"../escape","/absolute","game/../escape","game/dkc1.sfc","game/quicksave-4x3-slot6.state","game/quicksave-4x3.state/../x","config/settings-v3.json.bak","game/a.dll"}){
                check(!SaveBackup.allowed(bad));reject(root,cache,zip("FORMAT.txt",marker,"game/dkc1.srm","MUST NOT REPLACE",bad,"BAD"));
            }
            reject(root,cache,zip("game/dkc1.srm","No marker"));
            reject(root,cache,zip("FORMAT.txt","wrong marker","game/dkc1.srm","NO"));
            reject(root,cache,zip("FORMAT.txt",marker));
            reject(root,cache,zip("FORMAT.txt",marker,"game/dkc1.srm",""));
            reject(root,cache,zip("FORMAT.txt",marker,"config/settings-v3.json","x".repeat(65537)));
            reject(root,cache,zip("FORMAT.txt",marker,"game/dkc1.srm","x".repeat(131073)));
            check(new File(root,"game/dkc1.sfc").length()==13);
            // Every supported landscape has equal face radii, equal auxiliaries,
            // symmetric action positions and a non-overlapping default layout.
            for(int[] screen:new int[][]{{960,540},{1509,709},{2400,1080},{720,540},{480,270},{2560,1080}})for(float size:new float[]{.7f,1,1.3f}){
                PadModel p=new PadModel();p.layout(screen[0],screen[1],size,24,0,24,16);
                Map<Integer,PadModel.Button> m=new HashMap<>();for(PadModel.Button b:p.buttons)m.put(b.mask,b);
                float r=m.get(PadModel.B).radius;for(int id:new int[]{PadModel.A,PadModel.X,PadModel.Y})check(m.get(id).radius==r);
                float ar=m.get(PadModel.L).radius;for(int id:new int[]{PadModel.R,PadModel.SELECT,PadModel.START,PadModel.MENU})check(m.get(id).radius==ar);
                check(Math.abs(m.get(PadModel.B).x-m.get(PadModel.X).x)<.001);
                check(Math.abs(m.get(PadModel.Y).y-m.get(PadModel.A).y)<.001);
                for(PadModel.Button b:p.buttons){
                    check(b.x-b.radius>=23.99&&b.x+b.radius<=screen[0]-23.99);
                    check(b.y-b.radius>=-.01&&b.y+b.radius<=screen[1]-15.99);
                    check(p.hit(b.x,b.y)==b.mask);
                    for(PadModel.Button o:p.buttons)if(o!=b){float dx=o.x-b.x,dy=o.y-b.y;check(dx*dx+dy*dy>=(b.radius+o.radius)*(b.radius+o.radius));}
                }
                p.touch(0,Float.NaN,3);check(p.state()==0);
            }
            System.out.println("Backup security/roundtrip and uniform control geometry: "+checks+" assertions passed");
        }finally{remove(work);}
    }
}
