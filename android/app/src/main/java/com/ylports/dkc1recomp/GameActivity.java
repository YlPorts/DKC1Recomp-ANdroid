package com.ylports.dkc1recomp;

import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.File;
import org.libsdl.app.SDLActivity;

/** SDL owns the game thread. UI/JNI methods only publish atomic requests. */
public final class GameActivity extends SDLActivity {
    public static final int SAVE=1, LOAD=2, QUIT=4;
    public static native void nativeSetTouchMask(int mask);
    public static native void nativeSetMenuPaused(boolean paused);
    public static native void nativeSetLifecyclePaused(boolean paused);
    public static native void nativeRequest(int command);
    public static native void nativeSetMuted(boolean muted);
    private TouchControls controls;
    private AlertDialog menu;
    private boolean exiting,buttons=true,muted;
    @Override protected String[] getLibraries(){return new String[]{"SDL2","main"};}
    @Override protected String[] getArguments(){
        File dir=LauncherActivity.gameDirectory(this);
        String aspect=getIntent().getStringExtra("aspect");
        if(!"16:10".equals(aspect)&&!"16:9".equals(aspect))aspect="4:3";
        return new String[]{"--rom",new File(dir,"dkc1.sfc").getAbsolutePath(),
            "--state-dir",dir.getAbsolutePath(),"--aspect",aspect};
    }
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        if(mBrokenLibraries||mLayout==null)return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        controls=new TouchControls(this,this::showPauseMenu);
        mLayout.addView(controls,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mLayout.setOnApplyWindowInsetsListener((v,insets)->{
            int left,right,top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                left=safe.left;right=safe.right;top=safe.top;bottom=safe.bottom;
            }else{
                left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();
                top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();
                if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){
                    left=Math.max(left,insets.getDisplayCutout().getSafeInsetLeft());
                    right=Math.max(right,insets.getDisplayCutout().getSafeInsetRight());
                    top=Math.max(top,insets.getDisplayCutout().getSafeInsetTop());
                    bottom=Math.max(bottom,insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
            v.setPadding(left,top,right,bottom);return insets;
        });
        mLayout.requestApplyInsets();
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::showPauseMenu);
    }
    @Override protected void onPause(){
        if(!mBrokenLibraries){if(controls!=null)controls.releaseAll();nativeSetLifecyclePaused(true);}
        super.onPause();
    }
    @Override protected void onResume(){
        super.onResume();if(!mBrokenLibraries)nativeSetLifecyclePaused(false);
    }
    @Override public void onBackPressed(){if(mBrokenLibraries)super.onBackPressed();else showPauseMenu();}
    public void openPauseFromNative(){runOnUiThread(this::showPauseMenu);}
    public void onNativeStatus(String message){
        runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())Toast.makeText(this,message,Toast.LENGTH_LONG).show();});
    }
    private void showPauseMenu(){
        if(mBrokenLibraries||exiting||isFinishing()||(menu!=null&&menu.isShowing()))return;
        nativeSetMenuPaused(true);if(controls!=null)controls.releaseAll();
        String[] items={"Continuar", "Guardar estado rápido", "Cargar estado rápido",
            muted?"Activar sonido":"Silenciar",buttons?"Ocultar controles":"Mostrar controles", "Salir al inicio"};
        menu=new AlertDialog.Builder(this).setTitle("DKC1Recomp · pausa")
            .setItems(items,(dialog,which)->{
                switch(which){
                    case 1:nativeRequest(SAVE);break;
                    case 2:nativeRequest(LOAD);break;
                    case 3:muted=!muted;nativeSetMuted(muted);break;
                    case 4:buttons=!buttons;if(controls!=null)controls.setButtonsVisible(buttons);break;
                    case 5:exiting=true;nativeRequest(QUIT);break;
                    default:break;
                }
            }).create();
        menu.setOnDismissListener(dialog->{menu=null;if(!exiting)nativeSetMenuPaused(false);});
        menu.show();
    }
    @Override protected void onDestroy(){
        // Request a normal native exit first. SDL's onDestroy joins its thread.
        exiting=true;
        if(!mBrokenLibraries){nativeSetTouchMask(0);nativeRequest(QUIT);}
        super.onDestroy();
        // Only this private :game process is terminated, never the launcher.
        // A fresh process is essential until upstream session-reset is validated.
        if(isFinishing())Process.killProcess(Process.myPid());
    }
}
