package com.ylports.dkc1recomp;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Uniform touch overlay. Editing is transactional: Save commits; Cancel restores. */
public final class TouchControls extends View {
    private final PadModel pad=new PadModel();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AppSettings settings;
    private final Runnable menu,editDone;
    private boolean visibleButtons=true,editing;
    private int safeLeft,safeTop,safeRight,safeBottom,dragPointer=-1,dragMask;
    private float dragDx,dragDy;
    private final RectF save=new RectF(),cancel=new RectF(),reset=new RectF();
    public TouchControls(Context c,AppSettings settings,Runnable menu,Runnable editDone){
        super(c);this.settings=settings;this.menu=menu;this.editDone=editDone;
        setFocusable(false);setContentDescription("Mando SNES. B salta; Y corre. II abre los ajustes.");
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
    }
    public void applySettings(){visibleButtons=settings.flag("touch_visible",true);layoutPad();releaseAll();}
    public void setButtonsVisible(boolean value){visibleButtons=value;releaseAll();}
    public void setSafeInsets(int l,int t,int r,int b){
        if(safeLeft==l&&safeTop==t&&safeRight==r&&safeBottom==b)return;
        safeLeft=Math.max(0,l);safeTop=Math.max(0,t);safeRight=Math.max(0,r);safeBottom=Math.max(0,b);
        layoutPad();invalidate();
    }
    public void beginEdit(){releaseAll();editing=true;dragPointer=-1;invalidate();}
    public boolean isEditing(){return editing;}
    public void finishEdit(boolean commit){
        if(!editing)return;
        if(commit){
            writePosition("dpad",pad.dpadX,pad.dpadY);
            for(PadModel.Button b:pad.buttons)writePosition("b"+b.mask,b.x,b.y);
            if(!settings.save())Ui.message(getContext(),"Controles","No se pudo guardar la distribución.");
        }
        editing=false;dragPointer=-1;layoutPad();releaseAll();editDone.run();
    }
    public void resetLayout(){settings.clearPositions();settings.save();layoutPad();releaseAll();}
    public void releaseAll(){pad.clear();GameActivity.nativeSetTouchMask(0);invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){layoutPad();releaseAll();}
    @Override protected void onDetachedFromWindow(){releaseAll();super.onDetachedFromWindow();}
    private String key(String name,String axis){return "pad3_"+name+"_"+axis;}
    private void layoutPad(){layoutPad(true);}
    private void layoutPad(boolean saved){
        pad.layout(getWidth(),getHeight(),settings.get("touch_size",100,70,130)/100f,safeLeft,safeTop,safeRight,safeBottom);
        int w=getWidth()-safeLeft-safeRight,h=getHeight()-safeTop-safeBottom;if(w<=0||h<=0)return;
        if(saved){
            pad.dpadX=safeLeft+settings.position(key("dpad","x"),(pad.dpadX-safeLeft)/w)*w;
            pad.dpadY=safeTop+settings.position(key("dpad","y"),(pad.dpadY-safeTop)/h)*h;
            for(PadModel.Button b:pad.buttons){b.x=safeLeft+settings.position(key("b"+b.mask,"x"),(b.x-safeLeft)/w)*w;b.y=safeTop+settings.position(key("b"+b.mask,"y"),(b.y-safeTop)/h)*h;clamp(b);}
        }
        clampDpad();
    }
    private float clampX(float x,float r){float min=safeLeft+r,max=getWidth()-safeRight-r;return max<min?getWidth()/2f:Math.max(min,Math.min(max,x));}
    private float clampY(float y,float r){float min=safeTop+r,max=getHeight()-safeBottom-r;return max<min?getHeight()/2f:Math.max(min,Math.min(max,y));}
    private void clamp(PadModel.Button b){b.x=clampX(b.x,b.radius);b.y=clampY(b.y,b.radius);}
    private void clampDpad(){pad.dpadX=clampX(pad.dpadX,pad.dpadRadius);pad.dpadY=clampY(pad.dpadY,pad.dpadRadius);}
    private void writePosition(String id,float x,float y){int w=getWidth()-safeLeft-safeRight,h=getHeight()-safeTop-safeBottom;if(w>0&&h>0){settings.setPosition(key(id,"x"),(x-safeLeft)/w);settings.setPosition(key(id,"y"),(y-safeTop)/h);}}
    private PadModel.Button find(int mask){for(PadModel.Button b:pad.buttons)if(b.mask==mask)return b;return null;}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(editing)return editTouch(e);
        int action=e.getActionMasked();if(action==MotionEvent.ACTION_CANCEL){releaseAll();return true;}
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){int i=e.getActionIndex();
            if((pad.hit(e.getX(i),e.getY(i))&PadModel.MENU)!=0){releaseAll();menu.run();return true;}}
        if(visibleButtons){
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
                int i=e.getActionIndex();pad.touch(e.getPointerId(i),e.getX(i),e.getY(i));
            }else if(action==MotionEvent.ACTION_MOVE){
                for(int i=0;i<e.getPointerCount();i++)pad.touch(e.getPointerId(i),e.getX(i),e.getY(i));
            }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){
                pad.release(e.getPointerId(e.getActionIndex()));
            }
        }else pad.clear();
        GameActivity.nativeSetTouchMask(pad.state());invalidate();if(action==MotionEvent.ACTION_UP)performClick();return true;
    }
    private boolean editTouch(MotionEvent e){
        int action=e.getActionMasked(),index=e.getActionIndex();
        if(action==MotionEvent.ACTION_CANCEL){dragPointer=-1;dragMask=0;invalidate();return true;}
        if(action==MotionEvent.ACTION_DOWN){float x=e.getX(index),y=e.getY(index);toolbar();
            if(save.contains(x,y)){finishEdit(true);return true;}if(cancel.contains(x,y)){finishEdit(false);return true;}
            if(reset.contains(x,y)){layoutPad(false);invalidate();return true;}
            dragPointer=e.getPointerId(index);PadModel.Button b=pad.buttonAt(x,y);
            if(b!=null){dragMask=b.mask;dragDx=x-b.x;dragDy=y-b.y;}
            else{float dx=x-pad.dpadX,dy=y-pad.dpadY;if(dx*dx+dy*dy<=pad.dpadRadius*pad.dpadRadius){dragMask=-1;dragDx=dx;dragDy=dy;}else{dragPointer=-1;dragMask=0;}}
            invalidate();return true;
        }
        int pointer=dragPointer<0?-1:e.findPointerIndex(dragPointer);
        if(action==MotionEvent.ACTION_MOVE&&pointer>=0){float x=e.getX(pointer)-dragDx,y=e.getY(pointer)-dragDy;
            if(dragMask==-1){pad.dpadX=x;pad.dpadY=y;clampDpad();}else{PadModel.Button b=find(dragMask);if(b!=null){b.x=x;b.y=y;clamp(b);}}invalidate();}
        if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&pointer>=0&&e.getPointerId(index)==dragPointer){dragPointer=-1;dragMask=0;invalidate();performClick();}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private void label(Canvas c,String text,float x,float y,float size,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(size);paint.setTextAlign(Paint.Align.CENTER);c.drawText(text,x,y-(paint.ascent()+paint.descent())/2,paint);}
    private void circle(Canvas c,PadModel.Button b,int state){
        boolean pressed=(state&b.mask)!=0;int opacity=editing?80:settings.get("touch_opacity",55,20,90);
        paint.setStyle(Paint.Style.FILL);paint.setColor(pressed?0xc98cb6f1:((opacity*255/100)<<24)|0x20242b);c.drawCircle(b.x,b.y,b.radius,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,1.4f*pad.scale));paint.setColor(editing?Ui.ACCENT:0x99c1c9d7);c.drawCircle(b.x,b.y,b.radius,paint);
        label(c,b.label,b.x,b.y,b.label.length()>2?10.5f*pad.scale:23*pad.scale,pressed?Ui.BG:Ui.TEXT);
    }
    private void drawDpad(Canvas c,int state){
        float x=pad.dpadX,y=pad.dpadY,r=pad.dpadRadius,w=r*.34f;
        int a=(editing?70:settings.get("touch_opacity",55,20,90))*255/100;
        paint.setStyle(Paint.Style.FILL);paint.setColor(((a/3)<<24)|0x20242b);c.drawCircle(x,y,r,paint);
        paint.setColor((a<<24)|0x20242b);
        c.drawRoundRect(x-w,y-r,x+w,y+r,r*.1f,r*.1f,paint);c.drawRoundRect(x-r,y-w,x+r,y+w,r*.1f,r*.1f,paint);
        paint.setColor(0xff8cb6f1);
        if((state&PadModel.UP)!=0)c.drawRoundRect(x-w,y-r,x+w,y-w,r*.1f,r*.1f,paint);
        if((state&PadModel.DOWN)!=0)c.drawRoundRect(x-w,y+w,x+w,y+r,r*.1f,r*.1f,paint);
        if((state&PadModel.LEFT)!=0)c.drawRoundRect(x-r,y-w,x-w,y+w,r*.1f,r*.1f,paint);
        if((state&PadModel.RIGHT)!=0)c.drawRoundRect(x+w,y-w,x+r,y+w,r*.1f,r*.1f,paint);
        float d=r*.67f,s=18*pad.scale;label(c,"▲",x,y-d,s,Ui.TEXT);label(c,"▼",x,y+d,s,Ui.TEXT);label(c,"◀",x-d,y,s,Ui.TEXT);label(c,"▶",x+d,y,s,Ui.TEXT);
        paint.setColor(0xffa9b0be);c.drawCircle(x,y,6*pad.scale,paint);
    }
    private void toolbar(){
        float unit=Math.min(getWidth()/960f,getHeight()/540f),gap=10*unit,h=42*unit,w=116*unit;
        float cx=getWidth()/2f,top=safeTop+78*unit;
        reset.set(cx-1.5f*w-gap,top,cx-.5f*w-gap,top+h);cancel.set(cx-.5f*w,top,cx+.5f*w,top+h);save.set(cx+.5f*w+gap,top,cx+1.5f*w+gap,top+h);
    }
    private void toolbarButton(Canvas c,RectF r,String title,boolean primary){paint.setStyle(Paint.Style.FILL);paint.setColor(primary?Ui.ACCENT:Ui.CARD);c.drawRoundRect(r,8*pad.scale,8*pad.scale,paint);label(c,title,r.centerX(),r.centerY(),14*Math.min(getWidth()/960f,getHeight()/540f),primary?Ui.BG:Ui.TEXT);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);int state=pad.state();if(visibleButtons||editing)drawDpad(c,state);
        for(PadModel.Button b:pad.buttons)if(visibleButtons||editing||b.mask==PadModel.MENU)circle(c,b,state);
        if(editing){toolbar();paint.setColor(0xe61c1d21);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(reset.left-8,reset.top-30*pad.scale,save.right+8,save.bottom+8,10,10,paint);
            label(c,"Mueve la cruceta o cada botón. Guarda al terminar.",getWidth()/2f,reset.top-16*pad.scale,12*pad.scale,Ui.TEXT);
            toolbarButton(c,reset,"Restablecer",false);toolbarButton(c,cancel,"Cancelar",false);toolbarButton(c,save,"Guardar",true);}
    }
}