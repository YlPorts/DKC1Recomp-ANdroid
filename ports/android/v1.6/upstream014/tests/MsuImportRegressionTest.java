package com.ylports.dkc1recomp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Regression coverage for the v1.5 false "pack too large" folder/ZIP bug. */
public final class MsuImportRegressionTest {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}
    private static byte[] pcm(){byte[] b=new byte[40];b[0]='M';b[1]='S';b[2]='U';b[3]='1';return b;}
    private static final class LargePcm extends InputStream {
        private long remaining;private long offset;
        LargePcm(long size){remaining=size;}
        @Override public int read(byte[] b,int off,int len){
            if(remaining==0)return -1;int n=(int)Math.min(remaining,len);Arrays.fill(b,off,off+n,(byte)0);
            byte[] h={'M','S','U','1',0,0,0,0};
            if(offset<8){int start=(int)offset,copy=Math.min(n,8-start);System.arraycopy(h,start,b,off,copy);}
            offset+=n;remaining-=n;return n;
        }
        @Override public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}
    }
    private static byte[] zipPack()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            for(int i=1;i<=27;i++){
                String name=i==1?"01 - Jungle Hijinxs.pcm":i==2?"Donkey Kong Country HD - 02.pcm":i==27?"DKC1_27.pcm":"track-"+i+".pcm";
                zip.putNextEntry(new ZipEntry(name));zip.write(pcm());zip.closeEntry();
            }
        }return out.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("dkc-msu-v16-");
        try{
            MusicPack.fromZip(root.toFile(),new ByteArrayInputStream(zipPack()));
            check(MusicPack.isComplete(root.resolve("music/current").toFile()));
            Path stage=Files.createDirectory(root.resolve("large"));
            MusicPack.Writer writer=new MusicPack.Writer(stage.toFile());
            long size=9L*1024*1024;writer.add("01 - Jungle Hijinxs.pcm",new LargePcm(size));
            File track=stage.resolve("track-1.pcm").toFile();check(track.isFile());check(track.length()==size);MusicPack.validatePcm(track);check(true);
            boolean rejected=false;try{writer.add("music.pcm",new ByteArrayInputStream(pcm()));}catch(IOException e){rejected=e.getMessage().contains("número de pista");}check(rejected);
            rejected=false;try{writer.add("DKC 1994.pcm",new ByteArrayInputStream(pcm()));}catch(IOException e){rejected=e.getMessage().contains("número de pista");}check(rejected);
            System.out.println("MSU-1 v1.6 regression tests passed: "+checks);
        }finally{MusicPack.delete(root.toFile());}
    }
}