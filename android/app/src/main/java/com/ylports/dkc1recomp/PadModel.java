package com.ylports.dkc1recomp;
import java.util.HashMap;
import java.util.Map;

/** One geometry for drawing and hit-testing. Uniform face buttons and symmetric SNES diamond. */
public final class PadModel {
    public static final int B=1,Y=2,SELECT=4,START=8,UP=16,DOWN=32,LEFT=64,RIGHT=128,A=256,X=512,L=1024,R=2048,MENU=65536;
    public static final class Button {
        public final String label;public final int mask;public float x,y;public final float radius;
        Button(String label,int mask,float x,float y,float radius){this.label=label;this.mask=mask;this.x=x;this.y=y;this.radius=radius;}
        public boolean contains(float px,float py){float dx=px-x,dy=py-y;return dx*dx+dy*dy<=radius*radius;}
    }
    public Button[] buttons=new Button[0];public float dpadX,dpadY,dpadRadius,scale=1;
    private final Map<Integer,Integer> fingers=new HashMap<>();
    public void layout(int width,int height){layout(width,height,1f,0,0,0,0);}
    public void layout(int width,int height,float size,int left,int top,int right,int bottom){
        clear();buttons=new Button[0];dpadRadius=0;
        if(width<=0||height<=0)return;
        int w=Math.max(1,width-Math.max(0,left)-Math.max(0,right)),h=Math.max(1,height-Math.max(0,top)-Math.max(0,bottom));
        if((Float.isNaN(size)||Float.isInfinite(size)))size=1;size=Math.max(.7f,Math.min(1.3f,size));
        float base=Math.min(w/960f,h/540f);scale=base*size;
        float r=38*scale,aux=27*scale,gap=82*scale,margin=18*base;
        float cx=left+w-margin-r-gap,cy=top+h-margin-r-gap;
        dpadRadius=96*scale;dpadX=left+margin+dpadRadius;dpadY=top+h-margin-dpadRadius;
        buttons=new Button[]{new Button("B",B,cx,cy+gap,r),new Button("Y",Y,cx-gap,cy,r),
            new Button("A",A,cx+gap,cy,r),new Button("X",X,cx,cy-gap,r),
            new Button("L",L,left+margin+aux,top+margin+aux,aux),
            new Button("R",R,left+w-margin-aux,top+margin+aux,aux),
            new Button("SEL",SELECT,left+w/2f-40*scale,top+h-margin-aux,aux),
            new Button("START",START,left+w/2f+40*scale,top+h-margin-aux,aux),
            new Button("II",MENU,left+w/2f,top+margin+aux,aux)};
    }
    public Button buttonAt(float x,float y){for(Button b:buttons)if(b.contains(x,y))return b;return null;}
    public int hit(float x,float y){
        if(Float.isNaN(x)||Float.isInfinite(x)||Float.isNaN(y)||Float.isInfinite(y))return 0;int mask=0;
        for(Button b:buttons)if(b.contains(x,y))mask|=b.mask;
        float dx=x-dpadX,dy=y-dpadY;
        if(dpadRadius>0&&dx*dx+dy*dy<=dpadRadius*dpadRadius){
            float dead=.24f*dpadRadius;if(dx< -dead)mask|=LEFT;if(dx>dead)mask|=RIGHT;if(dy< -dead)mask|=UP;if(dy>dead)mask|=DOWN;}
        return mask;
    }
    public void touch(int id,float x,float y){if(fingers.containsKey(id)||fingers.size()<16)fingers.put(id,hit(x,y)&4095);}
    public void release(int id){fingers.remove(id);}public void clear(){fingers.clear();}
    public int state(){int n=0;for(int m:fingers.values())n|=m;if((n&(UP|DOWN))==(UP|DOWN))n&=~(UP|DOWN);if((n&(LEFT|RIGHT))==(LEFT|RIGHT))n&=~(LEFT|RIGHT);return n;}
}
