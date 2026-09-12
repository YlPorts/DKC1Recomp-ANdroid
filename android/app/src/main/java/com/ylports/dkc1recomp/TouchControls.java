package com.ylports.dkc1recomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public final class TouchControls extends View {
    private final PadModel pad=new PadModel();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable menu;
    private boolean visibleButtons=true;
    public TouchControls(Context context, Runnable menu) {
        super(context); this.menu=menu;
        setFocusable(false);
        setContentDescription("Cruceta y botones de Donkey Kong Country. Y corre, B salta.");
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
    }
    public void setButtonsVisible(boolean visible) {
        visibleButtons=visible; releaseAll(); invalidate();
    }
    public void releaseAll() {
        pad.clear(); GameActivity.nativeSetTouchMask(0); invalidate();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        pad.layout(w,h); releaseAll();
    }
    @Override protected void onDetachedFromWindow() {
        releaseAll(); super.onDetachedFromWindow();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if (action==MotionEvent.ACTION_CANCEL) { releaseAll(); return true; }
        if (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            int index=event.getActionIndex();
            if ((pad.hit(event.getX(index),event.getY(index))&PadModel.MENU)!=0) {
                releaseAll(); menu.run(); return true;
            }
        }
        // Rebuild the complete live pointer set; never leave a departed pointer held.
        pad.clear();
        if (visibleButtons && action!=MotionEvent.ACTION_UP) {
            for (int i=0;i<event.getPointerCount();i++) {
                if (action==MotionEvent.ACTION_POINTER_UP && i==event.getActionIndex()) continue;
                pad.touch(event.getPointerId(i),event.getX(i),event.getY(i));
            }
        }
        GameActivity.nativeSetTouchMask(pad.state());
        invalidate();
        if (action==MotionEvent.ACTION_UP) performClick();
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private void circle(Canvas c,float x,float y,float radius,boolean pressed,String label,float textSize) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? 0xc5b5e37b : 0x69212827);
        c.drawCircle(x,y,radius,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.6f*pad.scale);
        paint.setColor(0xaaddede3); c.drawCircle(x,y,radius,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? Color.BLACK : Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(textSize);
        c.drawText(label,x,y-(paint.ascent()+paint.descent())/2,paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int state=pad.state();
        if (visibleButtons) {
            float d=pad.dpadRadius*0.57f, r=pad.dpadRadius*0.40f;
            circle(canvas,pad.dpadX-d,pad.dpadY,r,(state&PadModel.LEFT)!=0,"◀",20*pad.scale);
            circle(canvas,pad.dpadX+d,pad.dpadY,r,(state&PadModel.RIGHT)!=0,"▶",20*pad.scale);
            circle(canvas,pad.dpadX,pad.dpadY-d,r,(state&PadModel.UP)!=0,"▲",20*pad.scale);
            circle(canvas,pad.dpadX,pad.dpadY+d,r,(state&PadModel.DOWN)!=0,"▼",20*pad.scale);
        }
        for(PadModel.Button b:pad.buttons) {
            if (!visibleButtons && b.mask!=PadModel.MENU) continue;
            circle(canvas,b.x,b.y,b.radius,(state&b.mask)!=0,b.label,
                (b.label.length()>2 ? 12 : 25)*pad.scale);
        }
    }
}
