package com.ylports.dkc1recomp;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.Toast;
import java.io.File;
import org.libsdl.app.SDLActivity;

/** UI publishes requests; the SDL thread remains the sole owner of game/audio/saves. */
public final class GameActivity extends SDLActivity implements OptionsPanel.Host {
    public static final int SAVE=1,LOAD=2,QUIT=4,REWIND=16;
    public static native void nativeSetTouchMask(int mask);
    public static native void nativeSetMenuPaused(boolean paused);
    public static native void nativeSetLifecyclePaused(boolean paused);
    public static native void nativeRequest(int command);
    public static native void nativeSetMuted(boolean muted);
    public static native void nativeConfigure(int[] options);
    private AppSettings settings;private TouchControls controls;private Dialog dialog;private OptionsPanel panel;
    private String modKey="";
    private boolean exiting,compatibility;private boolean musicSession;private int currentRenderWidth=256;private String currentAspect="4:3",notice="Partida en curso";
    @Override protected String[] getLibraries(){return new String[]{"SDL2","main"};}
    @Override protected String[] getArguments(){
        File dir=AppSettings.gameDirectory(this);
        return new String[]{"--rom",new File(dir,"dkc1.sfc").getAbsolutePath(),"--state-dir",dir.getAbsolutePath(),
            "--aspect",currentAspect,"--render-width",Integer.toString(currentRenderWidth),"--resume",getIntent().getBooleanExtra("resume",false)?"1":"0",
            "--compatibility",compatibility?"1":"0",
            "--native-module",getIntent().getStringExtra("native_path")==null?"":getIntent().getStringExtra("native_path"),
            "--native-module-sha",getIntent().getStringExtra("native_sha")==null?"":getIntent().getStringExtra("native_sha"),
            "--music-dir",musicSession?new File(getFilesDir(),"music/current").getAbsolutePath():"",
            "--aquatic",settings.flag("aquatic",false)?"1":"0",
            "--mod-key",modKey,"--mod-plan",getIntent().getStringExtra("mod_plan")==null?"":getIntent().getStringExtra("mod_plan"),
            "--mod-plan-sha",getIntent().getStringExtra("mod_sha")==null?"":getIntent().getStringExtra("mod_sha"),
            "--runtime-file",getIntent().getStringExtra("runtime_path")==null?"":getIntent().getStringExtra("runtime_path"),
            "--runtime-sha",getIntent().getStringExtra("runtime_sha")==null?"":getIntent().getStringExtra("runtime_sha"),
            "--runtime-options",getIntent().getStringExtra("runtime_options")==null?"":getIntent().getStringExtra("runtime_options")};
    }
    /** The pinned SDL HID manager uses this Activity as its Context. Keep its
     * app-private USB permission receiver non-exported on modern Android. */
    @Override public android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver,android.content.IntentFilter filter){
        if(Build.VERSION.SDK_INT>=33&&filter!=null&&filter.hasAction("org.libsdl.app.USB_PERMISSION"))
            return super.registerReceiver(receiver,filter,android.content.Context.RECEIVER_NOT_EXPORTED);
        return super.registerReceiver(receiver,filter);
    }
    @Override protected void onCreate(Bundle state){
        modKey=getIntent().getStringExtra("mod_key");if(modKey==null)modKey="";if(!modKey.isEmpty()&&!modKey.matches("[0-9a-f]{16}")){finish();return;}
        compatibility=getIntent().getBooleanExtra("compatibility",false);
        settings=new AppSettings(this);musicSession=!compatibility&&(getIntent().getStringExtra("native_path")==null||getIntent().getStringExtra("native_path").isEmpty())&&settings.usesMusic(this);String aspect=getIntent().getStringExtra("aspect");
        for(String candidate:AppSettings.ASPECTS)if(candidate.equals(aspect))currentAspect=candidate;
        if(compatibility){currentAspect="4:3";currentRenderWidth=256;notice="Romhack · compatibilidad SNES · música propia";}
        else{android.util.DisplayMetrics dm=getResources().getDisplayMetrics();currentRenderWidth=RenderWidth.forAspect(currentAspect,dm.widthPixels,dm.heightPixels);}
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams p=getWindow().getAttributes();p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(p);}
        super.onCreate(state);if(mBrokenLibraries||mLayout==null)return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);Ui.immersive(this);mLayout.setPadding(0,0,0,0);
        controls=new TouchControls(this,settings,()->showMenu(0),()->showMenu(3));
        mLayout.addView(controls,new ViewGroup.LayoutParams(-1,-1));controls.applySettings();changed();
        mLayout.setOnApplyWindowInsetsListener((v,i)->{
            int l=0,t=0,r=0,b=0;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=i.getInsets(WindowInsets.Type.displayCutout()|WindowInsets.Type.systemGestures());l=safe.left;t=safe.top;r=safe.right;b=safe.bottom;}
            else if(Build.VERSION.SDK_INT>=28&&i.getDisplayCutout()!=null){l=i.getDisplayCutout().getSafeInsetLeft();r=i.getDisplayCutout().getSafeInsetRight();t=i.getDisplayCutout().getSafeInsetTop();b=i.getDisplayCutout().getSafeInsetBottom();}
            controls.setSafeInsets(l,t,r,b);return i;
        });mLayout.requestApplyInsets();
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
    }
    @Override protected void onPause(){if(!mBrokenLibraries){if(controls!=null)controls.releaseAll();nativeSetLifecyclePaused(true);}if(settings!=null)settings.save();super.onPause();}
    @Override protected void onResume(){super.onResume();Ui.immersive(this);if(!mBrokenLibraries)nativeSetLifecyclePaused(false);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&dialog==null)Ui.immersive(this);}
    @Override public void onBackPressed(){if(mBrokenLibraries)super.onBackPressed();else back();}
    private void back(){if(controls!=null&&controls.isEditing())controls.finishEdit(false);else if(dialog!=null)dialog.dismiss();else showMenu(0);}
    public void openPauseFromNative(){runOnUiThread(()->showMenu(0));}
    public void onNativeStomp(){
        if(settings==null||!settings.flag("haptics",true))return;
        try{
            android.os.Vibrator vibrator=(android.os.Vibrator)getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if(vibrator==null||!vibrator.hasVibrator())return;
            if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(android.os.VibrationEffect.createOneShot(38,96));
            else vibrator.vibrate(38);
        }catch(RuntimeException ignored){}
    }
    public void onNativeStatus(String value){runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;notice=value;Toast.makeText(this,value,Toast.LENGTH_SHORT).show();if(panel!=null&&dialog!=null)panel.refresh();
    });}
    private void showMenu(int page){
        if(mBrokenLibraries||exiting||isFinishing()||dialog!=null||controls==null||controls.isEditing())return;
        nativeSetMenuPaused(true);controls.releaseAll();
        dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        panel=new OptionsPanel(this,settings,this,page);dialog.setContentView(panel);
        dialog.setOnDismissListener(d->{dialog=null;panel=null;settings.save();controls.applySettings();Ui.immersive(this);if(!exiting&&!controls.isEditing())nativeSetMenuPaused(false);});
        dialog.show();Window w=dialog.getWindow();if(w!=null){
            w.setBackgroundDrawable(Ui.bg(Ui.BG,Ui.dp(this,12)));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(.65f);
            int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;
            w.setLayout(Math.min(width-Ui.dp(this,16),Ui.dp(this,1000)),height-Ui.dp(this,16));
        }
    }
    @Override public boolean inGame(){return true;}
    @Override public boolean romReady(){return true;}
    @Override public boolean busy(){return false;}
    @Override public String activeAspect(){return currentAspect;}
    @Override public String saveKey(){return currentAspect.replace(':','x')+(musicSession?"-msu1":"")+(modKey.isEmpty()?"":"-mod-"+modKey);}
    @Override public String status(){return "Formato actual: "+("device".equals(currentAspect)?"pantalla del dispositivo":currentAspect)+" · "+currentRenderWidth+"×224 · "+notice;}
    @Override public void changed(){
        if(mBrokenLibraries)return;
        nativeConfigure(new int[]{settings.get("volume",100,0,100),settings.get("sampling",0,0,2),settings.get("palette",0,0,3),settings.get("scanlines",0,0,60),settings.slot(),settings.get("deadzone",24,5,50),settings.get("pad_mapping",0,0,1),settings.flag("autosave",true)?1:0,settings.get("edge",3,0,3),settings.flag("muted",false)?1:0,settings.flag("rewind",false)?1:0,settings.get("speed",0,0,1),settings.flag("haptics",true)?1:0});
        if(controls!=null&&!controls.isEditing())controls.applySettings();
    }
    @Override public void action(String name){
        switch(name){
            case "rewind":nativeRequest(REWIND);break;
            case "music_page":if(panel!=null)panel.showPage(6);break;
            case "remove_mod":settings.removeMod();changed();if(panel!=null)panel.refresh();break;
            case "licenses":Ui.licenses(this);break;
            case "resume":if(dialog!=null)dialog.dismiss();break;
            case "save":
                File slot=new File(AppSettings.gameDirectory(this),"quicksave-"+saveKey()+(settings.slot()==0?"":"-slot"+(settings.slot()+1))+".state");
                if(slot.isFile())new AlertDialog.Builder(this).setTitle("Reemplazar ranura "+(settings.slot()+1)+"?").setMessage("Se guardará la partida actual en esta ranura.")
                    .setPositiveButton("Guardar",(d,w)->nativeRequest(SAVE)).setNegativeButton("Cancelar",null).show();else nativeRequest(SAVE);break;
            case "load":new AlertDialog.Builder(this).setTitle("Cargar ranura "+(settings.slot()+1)+"?").setMessage("Se reemplazará el estado de la sesión actual.")
                .setPositiveButton("Cargar",(d,w)->nativeRequest(LOAD)).setNegativeButton("Cancelar",null).show();break;
            case "quit":exiting=true;settings.save();nativeRequest(QUIT);if(dialog!=null)dialog.dismiss();break;
            case "edit_controls":controls.beginEdit();if(dialog!=null)dialog.dismiss();break;
            case "reset_controls":new AlertDialog.Builder(this).setTitle("Restablecer controles")
                .setMessage("Volver al tamaño y la distribución estándar?").setPositiveButton("Restablecer",(d,w)->{settings.set("touch_size",100);controls.resetLayout();changed();if(panel!=null)panel.refresh();}).setNegativeButton("Cancelar",null).show();break;
            default:break;
        }
    }
    @Override protected void onDestroy(){
        exiting=true;if(!mBrokenLibraries){nativeSetTouchMask(0);nativeRequest(QUIT);}if(settings!=null)settings.save();
        if(dialog!=null)dialog.dismiss();super.onDestroy();
        // Only the isolated game process exits; next launch has clean core globals.
        if(isFinishing())android.os.Process.killProcess(android.os.Process.myPid());
    }
}