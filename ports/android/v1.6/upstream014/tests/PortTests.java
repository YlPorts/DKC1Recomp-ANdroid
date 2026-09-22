package com.ylports.dkc1recomp;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class PortTests {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Check "+checks);}
    private interface IOAction{void run()throws IOException;}
    private static void reject(IOAction action){try{action.run();throw new AssertionError("Unexpected acceptance");}catch(IOException expected){checks++;}}
    private static PadModel.Button find(PadModel p,int mask){for(PadModel.Button b:p.buttons)if(b.mask==mask)return b;throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        PadModel pad=new PadModel();pad.layout(960,540);
        PadModel.Button jump=find(pad,PadModel.B),run=find(pad,PadModel.Y);
        pad.touch(3,pad.dpadX+pad.dpadRadius*0.55f,pad.dpadY);
        pad.touch(11,jump.x,jump.y);pad.touch(27,run.x,run.y);
        check(pad.state()==(PadModel.RIGHT|PadModel.B|PadModel.Y));
        pad.release(11);check(pad.state()==(PadModel.RIGHT|PadModel.Y));
        pad.touch(27,run.x+500,run.y);check(pad.state()==(PadModel.RIGHT|PadModel.Y));
        // One thumb may roll from Y onto B while Y remains held for running.
        pad.touch(27,jump.x,jump.y);check(pad.state()==(PadModel.RIGHT|PadModel.Y|PadModel.B));
        pad.touch(27,run.x,run.y);check(pad.state()==(PadModel.RIGHT|PadModel.Y));
        pad.release(27);check(pad.state()==PadModel.RIGHT);
        pad.clear();check(pad.state()==0);
        // Lifting one of two fingers on the same button must not release the other.
        pad.touch(7,jump.x,jump.y);pad.touch(8,jump.x,jump.y);
        pad.release(7);check(pad.state()==PadModel.B);pad.release(8);check(pad.state()==0);
        pad.touch(1,pad.dpadX+60,pad.dpadY-60);
        check(pad.state()==(PadModel.RIGHT|PadModel.UP));
        pad.touch(2,pad.dpadX-60,pad.dpadY+60);check(pad.state()==0);
        pad.layout(2400,1080);check(pad.state()==0);
        for(PadModel.Button button:pad.buttons){
            check((pad.hit(button.x,button.y)&button.mask)!=0);
            pad.touch(5,button.x,button.y);
            check(pad.state()==(button.mask&4095));pad.clear();
        }
        check(pad.hit(Float.NaN,20)==0);check(pad.hit(Float.POSITIVE_INFINITY,20)==0);
        for(int scale=1;scale<=4;scale++){
            pad.layout(480*scale,270*scale);jump=find(pad,PadModel.B);
            for(int id=0;id<16;id++)pad.touch(id,jump.x,jump.y);
            check(pad.state()==PadModel.B);
            for(int id=0;id<15;id++)pad.release(id);
            check(pad.state()==PadModel.B);pad.clear();check(pad.state()==0);
        }
        pad.layout(0,0);check(pad.hit(0,0)==0);
        check(RenderWidth.forAspect("4:3",2868,1320)==256);
        check(RenderWidth.forAspect("16:10",2868,1320)==308);
        check(RenderWidth.forAspect("16:9",2868,1320)==342);
        check(RenderWidth.forAspect("21:9",2868,1320)==448);
        check(RenderWidth.forAspect("device",2868,1320)==418);
        check(RenderWidth.forDisplay(2622,1206)>=256&&RenderWidth.forDisplay(2622,1206)<=448);
        check((RenderWidth.forDisplay(2622,1206)&1)==0);
        byte[] abc="abc".getBytes(StandardCharsets.US_ASCII);
        check(RomVerifier.sha256(abc,0,3).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        check(RomVerifier.sha256(new byte[0],0,0).equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        reject(()->RomVerifier.verify(new byte[0]));
        reject(()->RomVerifier.verify(new byte[RomVerifier.ROM_SIZE-1]));
        reject(()->RomVerifier.verify(new byte[RomVerifier.ROM_SIZE+1]));
        reject(()->RomVerifier.verify(new byte[RomVerifier.ROM_SIZE+513]));
        reject(()->RomVerifier.verify(new byte[RomVerifier.ROM_SIZE]));
        reject(()->RomVerifier.verify(new byte[RomVerifier.ROM_SIZE+512]));
        reject(()->RomVerifier.readVerified(null));
        reject(()->RomVerifier.readVerified(new ByteArrayInputStream(abc)));
        InputStream oversized=new InputStream(){
            public int read(){return 0;}
            public int read(byte[] b,int off,int len){Arrays.fill(b,off,off+len,(byte)0);return len;}
        };
        reject(()->RomVerifier.readVerified(oversized));
        System.out.println("Java control/ROM tests: "+checks+" assertions passed");
        System.out.println("No retail ROM was supplied: successful game import and gameplay are NOT tested.");
    }
}