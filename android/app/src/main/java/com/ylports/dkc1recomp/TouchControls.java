package com.ylports.dkc1recomp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Touch controller overlay with persistent, user-editable button positions. */
public final class TouchControls extends View {
    private static final String PREFS="dkc1-touch-layout-v2";
    private final PadModel pad=new PadModel();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable menu,editDone;
    private final SharedPreferences prefs;
    private boolean visibleButtons=true,editing;
    private int safeLeft,safeTop,safeRight,safeBottom,dragPointer=-1,dragMask;
    private float dragDx,dragDy;
    private final RectF done=new RectF();

    public TouchControls(Context context, Runnable menu, Runnable editDone) {
        super(context); this.menu=menu; this.editDone=editDone;
        prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        setFocusable(false);
        setContentDescription("Cruceta y botones de Donkey Kong Country. Y corre, B salta.");
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
    }
    public void setButtonsVisible(boolean visible) {
        visibleButtons=visible; releaseAll(); invalidate();
    }
    public void setSafeInsets(int left,int top,int right,int bottom) {
        safeLeft=Math.max(0,left);safeTop=Math.max(0,top);safeRight=Math.max(0,right);safeBottom=Math.max(0,bottom);
        if(getWidth()>0&&getHeight()>0){layoutPad();invalidate();}
    }
    public void beginEdit() { releaseAll();editing=true;visibleButtons=true;invalidate(); }
    public boolean isEditing(){return editing;}
    public void resetLayout(){prefs.edit().clear().apply();layoutPad();invalidate();}
    public void releaseAll() {
        pad.clear(); GameActivity.nativeSetTouchMask(0); invalidate();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { layoutPad();releaseAll(); }
    private void layoutPad(){
        pad.layout(getWidth(),getHeight());
        if(getWidth()<=0||getHeight()<=0)return;
        pad.dpadX=load("dpad_x",pad.dpadX/getWidth())*getWidth();
        pad.dpadY=load("dpad_y",pad.dpadY/getHeight())*getHeight();
        clampDpad();
        for(PadModel.Button b:pad.buttons){
            String k=key(b.mask);
            b.x=load(k+"_x",b.x/getWidth())*getWidth();
            b.y=load(k+"_y",b.y/getHeight())*getHeight();
            clamp(b);
        }
    }
    private float load(String key,float fallback){return prefs.contains(key)?prefs.getFloat(key,fallback):fallback;}
    private static String key(int mask){return "b"+mask;}
    private void clamp(PadModel.Button b){
        float l=safeLeft+b.radius,r=getWidth()-safeRight-b.radius,t=safeTop+b.radius,bot=getHeight()-safeBottom-b.radius;
        if(r<l){l=b.radius;r=getWidth()-b.radius;} if(bot<t){t=b.radius;bot=getHeight()-b.radius;}
        b.x=Math.max(l,Math.min(r,b.x));b.y=Math.max(t,Math.min(bot,b.y));
    }
    private void clampDpad(){
        float r=pad.dpadRadius,l=safeLeft+r,rr=getWidth()-safeRight-r,t=safeTop+r,b=getHeight()-safeBottom-r;
        if(rr<l){l=r;rr=getWidth()-r;} if(b<t){t=r;b=getHeight()-r;}
        pad.dpadX=Math.max(l,Math.min(rr,pad.dpadX));pad.dpadY=Math.max(t,Math.min(b,pad.dpadY));
    }
    private void saveButton(PadModel.Button b){
        if(getWidth()<=0||getHeight()<=0)return;
        prefs.edit().putFloat(key(b.mask)+"_x",b.x/getWidth()).putFloat(key(b.mask)+"_y",b.y/getHeight()).apply();
    }
    private void saveDpad(){
        if(getWidth()<=0||getHeight()<=0)return;
        prefs.edit().putFloat("dpad_x",pad.dpadX/getWidth()).putFloat("dpad_y",pad.dpadY/getHeight()).apply();
    }
    @Override protected void onDetachedFromWindow() { releaseAll(); super.onDetachedFromWindow(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(editing)return editTouch(event);
        int action=event.getActionMasked();
        if (action==MotionEvent.ACTION_CANCEL) { releaseAll(); return true; }
        if (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            int index=event.getActionIndex();
            if ((pad.hit(event.getX(index),event.getY(index))&PadModel.MENU)!=0) {
                releaseAll(); menu.run(); return true;
            }
        }
        pad.clear();
        if (visibleButtons && action!=MotionEvent.ACTION_UP) {
            for (int i=0;i<event.getPointerCount();i++) {
                if (action==MotionEvent.ACTION_POINTER_UP && i==event.getActionIndex()) continue;
                pad.touch(event.getPointerId(i),event.getX(i),event.getY(i));
            }
        }
        GameActivity.nativeSetTouchMask(pad.state());invalidate();
        if (action==MotionEvent.ACTION_UP) performClick();
        return true;
    }
    private boolean editTouch(MotionEvent e){
        int action=e.getActionMasked(),idx=e.getActionIndex();
        if(action==MotionEvent.ACTION_CANCEL){dragPointer=-1;dragMask=0;return true;}
        if(action==MotionEvent.ACTION_DOWN){
            float x=e.getX(idx),y=e.getY(idx);
            updateDoneRect();
            if(done.contains(x,y)){editing=false;dragPointer=-1;invalidate();if(editDone!=null)editDone.run();return true;}
            dragPointer=e.getPointerId(idx);
            PadModel.Button button=pad.buttonAt(x,y);
            if(button!=null){dragMask=button.mask;dragDx=x-button.x;dragDy=y-button.y;}
            else {float dx=x-pad.dpadX,dy=y-pad.dpadY;if(dx*dx+dy*dy<=pad.dpadRadius*pad.dpadRadius){dragMask=-1;dragDx=dx;dragDy=dy;}else{dragPointer=-1;dragMask=0;}}
            invalidate();return true;
        }
        int pi=dragPointer<0?-1:e.findPointerIndex(dragPointer);
        if(action==MotionEvent.ACTION_MOVE&&pi>=0){
            float x=e.getX(pi)-dragDx,y=e.getY(pi)-dragDy;
            if(dragMask==-1){pad.dpadX=x;pad.dpadY=y;clampDpad();}
            else {PadModel.Button b=find(dragMask);if(b!=null){b.x=x;b.y=y;clamp(b);}}
            invalidate();return true;
        }
        if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&pi>=0&&e.getPointerId(idx)==dragPointer){
            if(dragMask==-1)saveDpad();else {PadModel.Button b=find(dragMask);if(b!=null)saveButton(b);}
            dragPointer=-1;dragMask=0;invalidate();performClick();return true;
        }
        return true;
    }
    private PadModel.Button find(int mask){for(PadModel.Button b:pad.buttons)if(b.mask==mask)return b;return null;}
    @Override public boolean performClick() { super.performClick(); return true; }
    private void circle(Canvas c,float x,float y,float radius,boolean pressed,String label,float textSize) {
        paint.setStyle(Paint.Style.FILL);paint.setColor(pressed ? 0xc5b5e37b : 0x69212827);c.drawCircle(x,y,radius,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.6f*pad.scale);paint.setColor(editing?0xffb5e37b:0xaaddede3); c.drawCircle(x,y,radius,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(pressed ? Color.BLACK : Color.WHITE);paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(textSize);
        c.drawText(label,x,y-(paint.ascent()+paint.descent())/2,paint);
    }
    private void updateDoneRect(){
        float w=Math.max(120*pad.scale,100),h=Math.max(46*pad.scale,42);float cx=getWidth()/2f,top=safeTop+12*pad.scale;
        done.set(cx-w/2,top,cx+w/2,top+h);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);int state=pad.state();
        if (visibleButtons) {
            float d=pad.dpadRadius*0.57f, r=pad.dpadRadius*0.40f;
            circle(canvas,pad.dpadX-d,pad.dpadY,r,(state&PadModel.LEFT)!=0,"◀",20*pad.scale);
            circle(canvas,pad.dpadX+d,pad.dpadY,r,(state&PadModel.RIGHT)!=0,"▶",20*pad.scale);
            circle(canvas,pad.dpadX,pad.dpadY-d,r,(state&PadModel.UP)!=0,"▲",20*pad.scale);
            circle(canvas,pad.dpadX,pad.dpadY+d,r,(state&PadModel.DOWN)!=0,"▼",20*pad.scale);
        }
        for(PadModel.Button b:pad.buttons) {
            if (!visibleButtons && b.mask!=PadModel.MENU) continue;
            circle(canvas,b.x,b.y,b.radius,(state&b.mask)!=0,b.label,(b.label.length()>2 ? 12 : 25)*pad.scale);
        }
        if(editing){
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xaa000000);canvas.drawRect(0,0,getWidth(),Math.max(62*pad.scale,safeTop+58*pad.scale),paint);
            paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.max(14*pad.scale,14));
            canvas.drawText("Arrastra los botones · la posición se guarda automáticamente",getWidth()/2f,safeTop+18*pad.scale,paint);
            updateDoneRect();paint.setColor(0xffb5e37b);canvas.drawRoundRect(done,12*pad.scale,12*pad.scale,paint);paint.setColor(Color.BLACK);paint.setTextSize(Math.max(15*pad.scale,15));
            canvas.drawText("LISTO",done.centerX(),done.centerY()-(paint.ascent()+paint.descent())/2,paint);
        }
    }
}
