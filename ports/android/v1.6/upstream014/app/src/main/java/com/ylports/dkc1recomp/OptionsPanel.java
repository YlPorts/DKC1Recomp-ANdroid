package com.ylports.dkc1recomp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.DateFormat;
import java.util.Date;

/** Same dark, sectioned approach as the PC host; only implemented mobile options are exposed. */
public final class OptionsPanel extends LinearLayout {
    public interface Host {
        boolean inGame();boolean romReady();boolean busy();String status();
        String activeAspect();String saveKey();void action(String name);void changed();
    }
    private final AppSettings settings;private final Host host;
    private final LinearLayout content,tabs;
    private final String[] pages={"Juego","Gráficos","Audio","Controles","Partidas","Mods","Música","Extras"};
    private final Button[] tabButtons=new Button[pages.length];
    private int page;
    public OptionsPanel(Context c,AppSettings settings,Host host,int initialPage){
        super(c);this.settings=settings;this.host=host;setOrientation(VERTICAL);setBackgroundColor(Ui.BG);
        LinearLayout header=new LinearLayout(c);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(Ui.dp(c,18),Ui.dp(c,6),Ui.dp(c,14),Ui.dp(c,6));
        LinearLayout heading=Ui.column(c);heading.addView(Ui.title(c,"DKC1 Android",20));heading.addView(Ui.text(c,host.inGame()?"Pausa":"v1.6",11,Ui.MUTED));
        ImageView icon=new ImageView(c);icon.setImageResource(R.drawable.brand_icon);icon.setContentDescription("DKC1 Android");LayoutParams imageParams=new LayoutParams(Ui.dp(c,48),Ui.dp(c,48));imageParams.rightMargin=Ui.dp(c,12);header.addView(icon,imageParams);header.addView(heading,new LayoutParams(0,-2,1));
        if(host.inGame()){Button resume=Ui.button(c,"Continuar",()->host.action("resume"));Ui.primary(resume);header.addView(resume,new LayoutParams(-2,-2));}
        addView(header,new LayoutParams(-1,-2));
        LinearLayout body=new LinearLayout(c);addView(body,new LayoutParams(-1,0,1));
        ScrollView side=new ScrollView(c);side.setFillViewport(false);side.setBackgroundColor(Ui.PANEL);
        tabs=Ui.column(c);tabs.setPadding(Ui.dp(c,7),Ui.dp(c,8),Ui.dp(c,7),Ui.dp(c,8));side.addView(tabs);
        boolean wide=c.getResources().getDisplayMetrics().widthPixels/c.getResources().getDisplayMetrics().density>=700;
        body.addView(side,new LayoutParams(Ui.dp(c,wide?152:112),-1));
        for(int i=0;i<pages.length;i++){final int n=i;Button b=Ui.button(c,pages[i],()->showPage(n));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setTextSize(wide?14:12);tabButtons[i]=b;tabs.addView(b,new LayoutParams(-1,Ui.dp(c,49)));}
        ScrollView main=new ScrollView(c);main.setFillViewport(true);body.addView(main,new LayoutParams(0,-1,1));
        content=Ui.column(c);content.setPadding(Ui.dp(c,wide?22:12),Ui.dp(c,12),Ui.dp(c,wide?22:12),Ui.dp(c,20));main.addView(content);
        showPage(initialPage);
    }
    public int currentPage(){return page;}
    public void refresh(){showPage(page);}
    public void showPage(int n){
        page=Math.max(0,Math.min(pages.length-1,n));content.removeAllViews();
        for(int i=0;i<pages.length;i++){tabButtons[i].setTextColor(i==page?Ui.ACCENT:Ui.MUTED);tabButtons[i].setBackgroundTintList(ColorStateList.valueOf(i==page?0xff343e50:Ui.PANEL));}
        content.addView(Ui.title(getContext(),pages[page],23));
        switch(page){case 0:game();break;case 1:graphics();break;case 2:audio();break;case 3:controls();break;case 4:saves();break;case 5:mods();break;case 6:music();break;default:extras();}
    }
    private void changed(){if(!settings.save())Ui.message(getContext(),"Configuración","No se pudo guardar el ajuste.");host.changed();}
    private LinearLayout card(String title,String help){return Ui.card(content,title,help);}
    private Button action(LinearLayout parent,String title,String command,boolean enabled,boolean primary){Button b=Ui.button(getContext(),title,()->host.action(command));b.setEnabled(enabled);if(primary)Ui.primary(b);parent.addView(b,new LayoutParams(-1,-2));return b;}
    private void choice(LinearLayout parent,String label,String key,String[] choices,int fallback){
        parent.addView(Ui.text(getContext(),label,13,Ui.MUTED));Spinner s=new Spinner(getContext());
        ArrayAdapter<String> a=new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_item,choices);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);
        int current=settings.get(key,fallback,0,choices.length-1);s.setSelection(current,false);
        s.setMinimumHeight(Ui.dp(getContext(),48));parent.addView(s,new LayoutParams(-1,Ui.dp(getContext(),48)));
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(settings.get(key,fallback,0,choices.length-1)!=pos){settings.set(key,pos);changed();}}});
    }
    private void toggle(LinearLayout parent,String label,String key,boolean fallback){
        Switch s=new Switch(getContext());s.setText(label);s.setTextColor(Ui.TEXT);s.setTextSize(14);s.setMinHeight(Ui.dp(getContext(),52));s.setPadding(0,Ui.dp(getContext(),6),0,Ui.dp(getContext(),6));s.setChecked(settings.flag(key,fallback));
        s.setOnCheckedChangeListener((v,on)->{settings.set(key,on);changed();});parent.addView(s,new LayoutParams(-1,-2));
    }
    private void slider(LinearLayout parent,String label,String key,int fallback,int min,int max,String unit){
        TextView value=Ui.text(getContext(),label+" · "+settings.get(key,fallback,min,max)+unit,14,Ui.TEXT);parent.addView(value);
        SeekBar s=new SeekBar(getContext());s.setMax(max-min);s.setProgress(settings.get(key,fallback,min,max)-min);s.setMinimumHeight(Ui.dp(getContext(),44));parent.addView(s,new LayoutParams(-1,Ui.dp(getContext(),44)));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){changed();}public void onProgressChanged(SeekBar b,int p,boolean user){value.setText(label+" · "+(p+min)+unit);if(user){settings.set(key,p+min);host.changed();}}});
    }
    private File slotFile(int slot){return new File(AppSettings.gameDirectory(getContext()),"quicksave-"+host.saveKey()+(slot==0?"":"-slot"+(slot+1))+".state");}
    private File autoFile(){return new File(AppSettings.gameDirectory(getContext()),"autosave-"+host.saveKey()+".state");}
    private String modified(File f){return f.isFile()?DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(f.lastModified())):"Vacía";}
    private void game(){
        LinearLayout c=card(host.inGame()?"Partida en curso":"Donkey Kong Country",host.busy()?host.status():null);
        if(host.inGame()){
            action(c,"Continuar","resume",true,true);
            action(c,"Guardar partida","save",true,false);
            action(c,"Cargar partida","load",slotFile(settings.slot()).isFile(),false);
            if(settings.flag("rewind",false))action(c,"Retroceder 2 segundos","rewind",true,false);
            action(c,"Salir al menú","quit",true,false);
        }else{
            boolean ready=host.romReady()&&!host.busy(),auto=autoFile().isFile();
            action(c,auto?"Continuar":"Jugar","play",ready,true);
            if(auto)action(c,"Desde el título","fresh",ready,false);
            if(!host.romReady())action(c,"Seleccionar ROM","rom",!host.busy(),false);
            if(!ready&&!host.busy())c.addView(Ui.text(getContext(),host.status(),12,Ui.MUTED));
        }
        LinearLayout tools=card("Asistencia",null);
        choice(tools,"Velocidad","speed",new String[]{"Normal","2× (sin sonido)"},0);
        toggle(tools,"Rebobinado","rewind",false);
    }
    private void graphics(){
        LinearLayout c=card("Pantalla",host.inGame()?"El formato se aplica al reiniciar la sesión.":null);
        choice(c,"Formato","aspect",new String[]{"4:3","16:10 · experimental","16:9 · experimental","21:9 · experimental","Pantalla del dispositivo"},0);
        choice(c,"Escalado","sampling",new String[]{"Nearest","Bilinear","Sharp Bilinear"},0);
        choice(c,"Color","palette",new String[]{"Original","CRT","Composite","Trinitron"},0);
        LinearLayout crt=card("Presentación",null);
        slider(crt,"Líneas de barrido","scanlines",0,0,60," %");
        choice(crt,"Borde del nivel","edge",new String[]{"Reflejo","Barras negras","Desplazamiento","Glide"},3);
        toggle(crt,"Arreglos acuáticos (experimental; reinicio)","aquatic",false);
    }
    private void audio(){
        LinearLayout c=card("Salida",null);
        toggle(c,"Silenciar","muted",false);slider(c,"Volumen","volume",100,0,100," %");
        toggle(c,"Vibración al pisar enemigos","haptics",true);
        action(c,"Configurar música","music_page",true,false);
    }
    private void controls(){
        LinearLayout c=card("Pantalla táctil",null);
        toggle(c,"Mostrar controles","touch_visible",true);
        slider(c,"Tamaño","touch_size",100,70,130," %");
        slider(c,"Opacidad","touch_opacity",55,20,90," %");
        action(c,"Editar distribución","edit_controls",host.inGame(),true);
        if(!host.inGame())c.addView(Ui.text(getContext(),"Disponible desde la pausa del juego.",12,Ui.MUTED));
        action(c,"Restablecer distribución","reset_controls",true,false);
        LinearLayout g=card("Mandos",null);
        slider(g,"Zona muerta","deadzone",24,5,50," %");
        choice(g,"Botones","pad_mapping",new String[]{"Posición SNES","Intercambiar A/B y X/Y"},0);
    }
    private void saves(){
        LinearLayout c=card("Ranuras",null);
        for(int i=0;i<5;i++){final int slot=i;Button b=Ui.button(getContext(),(settings.slot()==i?"✓  ":"")+"Ranura "+(i+1)+"   ·   "+modified(slotFile(i)),()->{settings.set("slot",slot);changed();refresh();});b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);c.addView(b,new LayoutParams(-1,-2));}
        if(host.inGame()){action(c,"Guardar","save",true,true);action(c,"Cargar","load",slotFile(settings.slot()).isFile(),false);}
        LinearLayout a=card("Automático",null);toggle(a,"Guardar al pausar y salir","autosave",true);
        a.addView(Ui.text(getContext(),modified(autoFile()),12,Ui.MUTED));
        LinearLayout b=card("Copia de seguridad",host.inGame()?"Disponible en el menú inicial.":null);
        action(b,"Exportar partidas y ajustes","export_backup",!host.inGame()&&!host.busy(),false);
        action(b,"Restaurar copia","import_backup",!host.inGame()&&!host.busy(),false);
    }
    private void mods(){
        boolean editable=!host.inGame()&&!host.busy();
        LinearLayout header=card("Mods",host.inGame()?"Activa o desactiva los mods desde el inicio.":"Importa un mod y pulsa Activar. Las actualizaciones compatibles conservan su selección y sus partidas.");
        action(header,"Importar mod (.dkcmod)","import_mod",editable&&host.romReady(),true);
        action(header,"Desactivar todos los paquetes","disable_content",editable,false);
        try{
            java.util.Set<String> active=ContentMods.active(getContext().getFilesDir());
            java.util.List<ContentMods.Info> installed=ContentMods.installed(getContext().getFilesDir());
            if(installed.isEmpty())header.addView(Ui.text(getContext(),"Sin paquetes instalados",13,Ui.MUTED));
            for(ContentMods.Info info:installed){boolean on=active.contains(info.digest),legacy=info.runtime!=null&&!info.runtime.scripted();
                LinearLayout c=card(info.name+" · "+info.version,info.description);
                if(info.romhack)c.addView(Ui.text(getContext(),"Compatibilidad SNES · 4:3 · música propia · se ejecuta sin otros mods",13,Ui.MUTED));
                if(info.nativeModule!=null)c.addView(Ui.text(getContext(),"Mod nativo · formato de pantalla seleccionable · música propia",13,Ui.MUTED));
                c.addView(Ui.text(getContext(),(on?"ACTIVO":"Desactivado")+(info.locale.isEmpty()?"":" · "+info.locale),12,on?Ui.ACCENT:Ui.MUTED));
                if(legacy)c.addView(Ui.text(getContext(),"Importa la versión independiente actualizada de este mod para jugar. Este paquete anterior permanece guardado.",13,Ui.MUTED));
                if(!info.choices.isEmpty()){
                    java.util.Map<String,String> selected=ContentMods.selected(getContext().getFilesDir(),info);
                    for(ContentMods.Choice choice:info.choices){
                        c.addView(Ui.text(getContext(),choice.label,14,Ui.TEXT));
                        for(ContentMods.Option option:choice.options){boolean checked=option.id.equals(selected.get(choice.id));
                            Button b=action(c,(checked?"✓  ":"")+option.label,"select_content:"+info.digest+":"+choice.id+":"+option.id,editable&&!legacy&&!checked,checked);
                            b.setContentDescription(choice.label+": "+option.label+(checked?", seleccionado":""));
                        }
                    }
                    c.addView(Ui.text(getContext(),host.inGame()?"Cambia la selección desde el menú inicial.":"La selección se recuerda y usa sus propias partidas al iniciar.",12,Ui.MUTED));
                }
                action(c,on?"Desactivar":"Activar","toggle_content:"+info.digest,editable&&(!legacy||on),false);
                action(c,"Quitar paquete","delete_content:"+info.digest,editable&&!on,false);
            }
        }catch(java.io.IOException e){header.addView(Ui.text(getContext(),"Error en un paquete. Desactiva los mods para volver al juego original.",13,Ui.MUTED));}
        LinearLayout visual=card("Perfil de imagen",null);String name=settings.text("mod_name","");
        visual.addView(Ui.text(getContext(),name.isEmpty()?"Original":name,14,Ui.TEXT));
        action(visual,"Desactivar perfil visual","remove_mod",!name.isEmpty(),false);
    }
    private void music(){
        LinearLayout c=card("Banda sonora",host.inGame()?"Los cambios se aplican en la próxima sesión.":null);
        boolean pack=MusicPack.isComplete(new File(getContext().getFilesDir(),"music/current"));
        if(pack){toggle(c,"Usar pack MSU-1","music_enabled",false);c.addView(Ui.text(getContext(),settings.text("music_name","Pack instalado"),14,Ui.ACCENT));}
        else c.addView(Ui.text(getContext(),"Original de SNES",14,Ui.TEXT));
        action(c,pack?"Cambiar pack ZIP / MSU1":"Importar pack ZIP / MSU1","music_zip",!host.inGame()&&!host.busy(),true);
        action(c,"Importar carpeta de música","music_folder",!host.inGame()&&!host.busy(),false);
        if(pack)action(c,"Quitar pack","music_remove",!host.inGame()&&!host.busy(),false);
        c.addView(Ui.text(getContext(),"PCM MSU-1 estéreo · 44,1 kHz · pistas 1–27.",12,Ui.MUTED));
    }
    private void extras(){
        LinearLayout c=card("Archivos",null);
        action(c,"Cambiar ROM","rom",!host.inGame()&&!host.busy(),false);
        action(c,"Exportar diagnóstico","export_log",!host.inGame()&&!host.busy(),false);
        action(c,"Licencias","licenses",true,false);
    }
}