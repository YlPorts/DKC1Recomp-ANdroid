package com.ylports.dkc1recomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.AtomicFile;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LauncherActivity extends Activity {
    private static final int PICK_ROM=100, EXPORT_LOG=101;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private TextView status;
    private Button choose,play,exportLog;
    private Spinner aspect;
    private boolean busy;
    private int requestSerial;
    static File gameDirectory(Activity a) { return new File(a.getFilesDir(),"game"); }
    private File rom() { return new File(gameDirectory(this),"dkc1.sfc"); }
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(0,dp(9),0,dp(9));return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26),dp(35),dp(26),dp(24));scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(dp(26)+insets.getSystemWindowInsetLeft(),dp(28)+insets.getSystemWindowInsetTop(),
                dp(26)+insets.getSystemWindowInsetRight(),dp(24)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.addView(text("DKC1Recomp",32,Color.WHITE));
        root.addView(text("ANDROID · 0.1.0 DEV",13,0xffb5e37b));
        root.addView(text("Importa tu ROM de Donkey Kong Country USA v1.0. "
            + "El archivo se valida y queda únicamente en el almacenamiento privado de esta app.",16,0xffd6ded8));
        status=text("Comprobando archivos…",15,0xffb5e37b);root.addView(status);
        choose=new Button(this);choose.setText("Seleccionar ROM .sfc / .smc");root.addView(choose);
        choose.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try { startActivityForResult(i,PICK_ROM); }
            catch(android.content.ActivityNotFoundException e) { error("No hay selector de documentos disponible."); }
        });
        root.addView(text("Imagen",16,Color.WHITE));
        aspect=new Spinner(this);
        String[] choices={"4:3 · original", "16:10 · experimental", "16:9 · experimental"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);aspect.setAdapter(adapter);
        aspect.setSelection(Math.max(0,Math.min(2,getPreferences(MODE_PRIVATE).getInt("aspect",0))));root.addView(aspect);
        play=new Button(this);play.setText("Jugar");play.setEnabled(false);root.addView(play);
        play.setOnClickListener(v->{
            int choice=aspect.getSelectedItemPosition();getPreferences(MODE_PRIVATE).edit().putInt("aspect",choice).apply();
            String selected=choice==1 ? "16:10" : choice==2 ? "16:9" : "4:3";
            startActivity(new Intent(this,GameActivity.class).putExtra("aspect",selected));
        });
        exportLog=new Button(this);exportLog.setText("Exportar diagnóstico");root.addView(exportLog);
        exportLog.setOnClickListener(v->{
            if(!new File(gameDirectory(this),"android.log").isFile()){
                error("Todavía no hay un registro nativo. Se crea al intentar iniciar el juego.");return;
            }
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE,"DKC1Recomp-android.log.txt");
            try{startActivityForResult(i,EXPORT_LOG);}
            catch(android.content.ActivityNotFoundException e){error("No hay selector de documentos disponible.");}
        });
        root.addView(text("Y: correr / rodar / agarrar · B: saltar\n"
            + "SELECT y START disponibles en la parte inferior.\n"
            + "II o Atrás: menú, guardado y carga rápidos.",14,0xffaeb9b2));
        root.addView(text("Versión de desarrollo. Widescreen, audio, rendimiento y ciclo de vida "
            + "requieren validación en un teléfono. No incluye ROM ni recursos del juego.",13,0xffaeb9b2));
    }
    @Override protected void onResume() { super.onResume(); if (!busy) checkInstalled(); }
    private void busy(boolean value) {
        busy=value;choose.setEnabled(!value);play.setEnabled(false);if(exportLog!=null)exportLog.setEnabled(!value);
    }
    private void checkInstalled() {
        final int ticket=++requestSerial;
        busy(true);
        final File selected=rom();
        IO.execute(()->{
            String result="Selecciona tu ROM para comenzar.";boolean valid=false;
            try(InputStream in=new FileInputStream(selected)) {
                RomVerifier.readVerified(in);valid=true;result="ROM compatible instalada.";
            } catch(IOException e) { if(selected.exists()) result=e.getMessage(); }
            final boolean ready=valid;final String message=result;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||ticket!=requestSerial)return;
                busy(false);play.setEnabled(ready);status.setText(message);
                File lastError=new File(gameDirectory(this),"last-error.txt");
                if(lastError.isFile()) {
                    try(FileInputStream stream=new FileInputStream(lastError)) {
                        byte[] data=new byte[4096];int count=stream.read(data);
                        if(count>0) error(new String(data,0,count,StandardCharsets.UTF_8));
                        lastError.delete();
                    }catch(IOException ignored){}
                }
            });
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==EXPORT_LOG){exportDiagnostic(data.getData());return;}
        if(request!=PICK_ROM)return;
        final Uri uri=data.getData();final int ticket=++requestSerial;
        busy(true);status.setText("Validando SHA-256 e importando…");
        // Capture application context rather than accessing an Activity from the I/O thread.
        final android.content.Context app=getApplicationContext();
        final File dir=gameDirectory(this),target=rom();
        IO.execute(()->{
            String failure=null;
            try(InputStream in=app.getContentResolver().openInputStream(uri)) {
                byte[] payload=RomVerifier.readVerified(in);
                if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("No se pudo crear el directorio privado.");
                AtomicFile output=new AtomicFile(target);FileOutputStream stream=null;
                try {
                    stream=output.startWrite();stream.write(payload);output.finishWrite(stream);stream=null;
                }catch(IOException e){if(stream!=null)output.failWrite(stream);throw e;}
                try(InputStream verify=new FileInputStream(target)){RomVerifier.readVerified(verify);}
            }catch(IOException|SecurityException e){failure=e.getMessage();}
            final String problem=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||ticket!=requestSerial)return;
                busy(false);
                if(problem!=null)error(problem);
                checkInstalled();
            });
        });
    }
    private void exportDiagnostic(Uri uri){
        final File source=new File(gameDirectory(this),"android.log");
        final android.content.Context app=getApplicationContext();
        final int ticket=++requestSerial;busy(true);status.setText("Exportando diagnóstico…");
        IO.execute(()->{
            String failure=null;
            try(java.io.RandomAccessFile in=new java.io.RandomAccessFile(source,"r");
                java.io.OutputStream out=app.getContentResolver().openOutputStream(uri,"w")){
                if(out==null)throw new IOException("No se pudo abrir el archivo de destino.");
                // Bound export size to the last 8 MiB; no ROM or save data is exported.
                long length=in.length(),start=Math.max(0,length-8L*1024*1024);
                if(start>0)out.write("[Registro recortado: ultimos 8 MiB]\n".getBytes(StandardCharsets.UTF_8));
                in.seek(start);long remaining=length-start;byte[] buffer=new byte[8192];
                while(remaining>0){int n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));
                    if(n<0)break;out.write(buffer,0,n);remaining-=n;}
                out.flush();
            }catch(IOException|SecurityException e){failure=e.getMessage();}
            final String problem=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||ticket!=requestSerial)return;
                busy(false);
                if(problem!=null)error(problem);
                else android.widget.Toast.makeText(this,"Diagnóstico exportado.",android.widget.Toast.LENGTH_LONG).show();
                checkInstalled();
            });
        });
    }
    private void error(String message) {
        new AlertDialog.Builder(this).setTitle("DKC1Recomp").setMessage(message)
            .setPositiveButton("Entendido",null).show();
    }
}
