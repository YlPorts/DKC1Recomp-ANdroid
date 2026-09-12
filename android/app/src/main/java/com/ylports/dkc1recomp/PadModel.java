package com.ylports.dkc1recomp;

import java.util.HashMap;
import java.util.Map;

/** Device-independent touch layout and multi-finger state, tested without Android. */
public final class PadModel {
    public static final int B=1, Y=2, SELECT=4, START=8, UP=16, DOWN=32,
        LEFT=64, RIGHT=128, A=256, X=512, L=1024, R=2048, MENU=65536;
    public static final class Button {
        public final String label;
        public final int mask;
        public final float x, y, radius;
        Button(String label, int mask, float x, float y, float radius) {
            this.label=label; this.mask=mask; this.x=x; this.y=y; this.radius=radius;
        }
        public boolean contains(float px, float py) {
            float dx=px-x, dy=py-y;
            return dx*dx+dy*dy <= radius*radius;
        }
    }
    public Button[] buttons = new Button[0];
    public float dpadX, dpadY, dpadRadius, scale=1;
    private final Map<Integer, Integer> fingers = new HashMap<>();
    public void layout(int width, int height) {
        clear();
        if (width<=0 || height<=0) { buttons=new Button[0]; dpadRadius=0; return; }
        scale=Math.min(width/960f, height/540f);
        dpadX=123*scale; dpadY=height-140*scale; dpadRadius=96*scale;
        buttons=new Button[] {
            new Button("B", B, width-128*scale, height-72*scale, 43*scale),
            new Button("Y", Y, width-206*scale, height-147*scale, 43*scale),
            new Button("A", A, width-51*scale, height-147*scale, 37*scale),
            new Button("X", X, width-128*scale, height-220*scale, 37*scale),
            new Button("L", L, 65*scale, 53*scale, 29*scale),
            new Button("R", R, width-130*scale, 53*scale, 29*scale),
            new Button("SEL", SELECT, width/2f-58*scale, height-48*scale, 29*scale),
            new Button("START", START, width/2f+37*scale, height-48*scale, 34*scale),
            new Button("II", MENU, width-46*scale, 53*scale, 29*scale)
        };
    }
    public int hit(float x, float y) {
        if (Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) || Float.isInfinite(y)) return 0;
        int mask=0;
        for (Button b:buttons) if (b.contains(x,y)) mask|=b.mask;
        float dx=x-dpadX, dy=y-dpadY;
        if (dpadRadius>0 && dx*dx+dy*dy<=dpadRadius*dpadRadius) {
            float dead=0.24f*dpadRadius;
            if (dx < -dead) mask|=LEFT;
            if (dx > dead) mask|=RIGHT;
            if (dy < -dead) mask|=UP;
            if (dy > dead) mask|=DOWN;
        }
        return mask;
    }
    public void touch(int id, float x, float y) {
        if (fingers.containsKey(id) || fingers.size()<16) fingers.put(id,hit(x,y)&4095);
    }
    public void release(int id) { fingers.remove(id); }
    public void clear() { fingers.clear(); }
    public int state() {
        int result=0;
        for (int value:fingers.values()) result|=value;
        if ((result&(UP|DOWN))==(UP|DOWN)) result&=~(UP|DOWN);
        if ((result&(LEFT|RIGHT))==(LEFT|RIGHT)) result&=~(LEFT|RIGHT);
        return result;
    }
}
