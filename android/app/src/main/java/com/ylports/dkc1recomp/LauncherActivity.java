package com.ylports.dkc1recomp;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;

public final class LauncherActivity extends Activity implements OptionsPanel.Host {
    private static final int PICK_ROM=100,EXPORT_LOG=101,EXPORT_BACKUP=102,IMPORT_BACKUP=103,PICK_ROM3=104;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private AppSettings settings;private OptionsPanel panel;
    private boolean working,ready;private int serial;private String message="Comprobando la ROM instalada…";
    static File gameDirectory(Activity a){return AppSettings.gameDirectory(a);}
    private File rom(){return new File(gameDirectory(this),"dkc1.sfc");}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);settings=new AppSettings(this);panel=new OptionsPanel(this,settings,this,0);setContentView(panel);
        panel.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        panel.requestApplyInsets();
    }
    @Override protected void onResume(){super.onResume();settings.reload();if(!working)checkInstalled();}
    @Override protected void onPause(){settings.save();super.onPause();}
    @Override public boolean inGame(){return false;}
    @Override public boolean romReady(){return ready;}
    @Override public boolean busy(){return working;}
    @Override public String activeAspect(){return settings.aspect();}
    @Override public String status(){return message;}
    @Override public void changed(){}
    private void refresh(){if(panel!=null)panel.refresh();}
    @Override public void action(String name){
        if(name.equals("reset_controls")){new AlertDialog.Builder(this).setTitle("Restablecer controles")
            .setMessage("Volver a la distribución estándar sin cambiar partidas ni otros ajustes.")
            .setPositiveButton("Restablecer",(d,w)->{settings.clearPositions();settings.set("touch_size",100);settings.save();refresh();}).setNegativeButton("Cancelar",null).show();return;}
        if(working)return;
        switch(name){
            case "play":case "fresh":
                if(!ready)return;settings.save();startActivity(new Intent(this,GameActivity.class).putExtra("aspect",settings.aspect()).putExtra("resume",name.equals("play")&&settings.autoState(this).isFile()));break;
            case "rom":openDocument(PICK_ROM);break;
            case "rom3":openDocument(PICK_ROM3);break;
            case "export_backup":createDocument(EXPORT_BACKUP,"application/zip","DKC1-partidas.zip");break;
            case "import_backup":
                new AlertDialog.Builder(this).setTitle("Restaurar partidas y ajustes")
                    .setMessage("Los archivos presentes en la copia reemplazarán las mismas ranuras. Exporta antes tus partidas actuales. La ROM instalada no se cambia.")
                    .setPositiveButton("Elegir copia",(d,w)->openDocument(IMPORT_BACKUP)).setNegativeButton("Cancelar",null).show();break;
            case "export_log":
                if(!new File(gameDirectory(this),"android.log").isFile()){error("Aún no hay un diagnóstico. Se crea al iniciar el juego.");break;}
                createDocument(EXPORT_LOG,"text/plain","DKC1Recomp-android.log.txt");break;
            default:break;
        }
    }
    private void openDocument(int request){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),request);}catch(ActivityNotFoundException e){error("No hay selector de archivos disponible.");}}
    private void createDocument(int request,String mime,String name){try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name),request);}catch(ActivityNotFoundException e){error("No hay selector de archivos disponible.");}}
    private void checkInstalled(){
        final int ticket=++serial;working=true;message="Comprobando la copia privada…";refresh();File source=rom();
        IO.execute(()->{
            boolean valid=false;String status="Selecciona tu ROM USA v1.0 una vez para comenzar.";
            try(InputStream in=new AtomicFile(source).openRead()){RomVerifier.readVerified(in);valid=true;status="USA v1.0 · ROM instalada y verificada";}
            catch(IOException e){if(source.exists())status="La copia instalada no es válida. Cámbiala desde Extras.";}
            final boolean ok=valid;final String text=status;
            runOnUiThread(()->{if(isFinishing()||isDestroyed()||ticket!=serial)return;working=false;ready=ok;message=text;refresh();readLastError();});
        });
    }
    private void readLastError(){
        File f=new File(gameDirectory(this),"last-error.txt");if(!f.isFile())return;
        try(InputStream in=new FileInputStream(f)){byte[] b=new byte[4096];int n=in.read(b);if(n>0)error(new String(b,0,n,StandardCharsets.UTF_8));f.delete();}catch(IOException ignored){}
    }
    private interface Job{void run()throws Exception;}
    private void job(String status,String success,Job action){
        int ticket=++serial;working=true;message=status;refresh();
        IO.execute(()->{String problem=null;try{action.run();}catch(Exception e){problem=e.getMessage()==null?e.toString():e.getMessage();}final String failure=problem;
            runOnUiThread(()->{if(isFinishing()||isDestroyed()||ticket!=serial)return;working=false;settings.reload();if(failure!=null)error(failure);else Toast.makeText(this,success,Toast.LENGTH_LONG).show();checkInstalled();});});
    }
    private static void atomicWrite(File target,byte[] bytes)throws IOException{
        File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("No se pudo crear el almacenamiento privado.");
        AtomicFile file=new AtomicFile(target);FileOutputStream out=null;
        try{out=file.startWrite();out.write(bytes);file.finishWrite(out);out=null;}finally{if(out!=null)file.failWrite(out);}
    }
    private static byte[] verifyDkc3(InputStream source)throws IOException{
        if(source==null)throw new IOException("No se pudo abrir DKC3.");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;
        while((n=source.read(b))!=-1){if(out.size()+n>RomVerifier.ROM_SIZE+512)throw new IOException("DKC3 debe ser una ROM de 4 MiB.");out.write(b,0,n);}
        byte[] raw=out.toByteArray();int off=raw.length==RomVerifier.ROM_SIZE+512?512:0;
        if(raw.length-off!=RomVerifier.ROM_SIZE||!RomVerifier.sha256(raw,off,RomVerifier.ROM_SIZE).equals("2277a2d8dddb01fe5cb0ae9a0fa225d42b3a11adccaeafa18e3c339b3794a32b"))throw new IOException("Se necesita la revisión exacta DKC3 USA compatible con Baby Kong. El archivo anterior se conserva.");
        return off==0?raw:Arrays.copyOfRange(raw,off,raw.length);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();Context app=getApplicationContext();File dir=AppSettings.gameDirectory(app);
        if(request==PICK_ROM||request==PICK_ROM3){
            boolean third=request==PICK_ROM3;
            job("Importando y verificando…",third?"ROM de DKC3 instalada. Baby Kong se activa en Extras.":"ROM guardada. A partir de ahora, pulsa Jugar.",()->{
                try(InputStream in=app.getContentResolver().openInputStream(uri)){byte[] payload=third?verifyDkc3(in):RomVerifier.readVerified(in);atomicWrite(new File(dir,third?"dkc3.sfc":"dkc1.sfc"),payload);}
            });
        }else if(request==EXPORT_BACKUP){settings.save();job("Exportando partidas…","Copia de seguridad guardada.",()->{try(OutputStream out=app.getContentResolver().openOutputStream(uri,"w")){SaveBackup.exportTo(app.getFilesDir(),out);}});
        }else if(request==IMPORT_BACKUP){job("Validando la copia antes de restaurar…","Partidas y configuración restauradas.",()->{try(InputStream in=app.getContentResolver().openInputStream(uri)){SaveBackup.importFrom(app.getFilesDir(),app.getCacheDir(),in);}});
        }else if(request==EXPORT_LOG){job("Exportando diagnóstico…","Diagnóstico guardado.",()->{
            try(RandomAccessFile in=new RandomAccessFile(new File(dir,"android.log"),"r");OutputStream out=app.getContentResolver().openOutputStream(uri,"w")){
                if(out==null)throw new IOException("No se pudo abrir el destino.");long start=Math.max(0,in.length()-8L*1024*1024);in.seek(start);byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            }});
        }
    }
    private void error(String text){Ui.message(this,"DKC1Recomp",text);}
}
