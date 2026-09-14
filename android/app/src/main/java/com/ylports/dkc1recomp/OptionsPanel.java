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
        String activeAspect();void action(String name);void changed();
    }
    private final AppSettings settings;private final Host host;
    private final LinearLayout content,tabs;
    private final String[] pages={"Juego","Gráficos","Audio","Controles","Partidas","Extras","Créditos"};
    private final Button[] tabButtons=new Button[pages.length];
    private int page;
    public OptionsPanel(Context c,AppSettings settings,Host host,int initialPage){
        super(c);this.settings=settings;this.host=host;setOrientation(VERTICAL);setBackgroundColor(Ui.BG);
        LinearLayout header=new LinearLayout(c);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(Ui.dp(c,18),Ui.dp(c,6),Ui.dp(c,14),Ui.dp(c,6));
        LinearLayout heading=Ui.column(c);heading.addView(Ui.title(c,"DKC1Recomp",20));heading.addView(Ui.text(c,host.inGame()?"PAUSA  /  CONFIGURACIÓN":"ANDROID  /  0.3.0-dev",10,Ui.MUTED));
        header.addView(heading,new LayoutParams(0,-2,1));
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
        switch(page){case 0:game();break;case 1:graphics();break;case 2:audio();break;case 3:controls();break;case 4:saves();break;case 5:extras();break;default:credits();}
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
    private File slotFile(int slot){return new File(AppSettings.gameDirectory(getContext()),"quicksave-"+host.activeAspect().replace(':','x')+(slot==0?"":"-slot"+(slot+1))+".state");}
    private File autoFile(){return new File(AppSettings.gameDirectory(getContext()),"autosave-"+host.activeAspect().replace(':','x')+".state");}
    private String modified(File f){return f.isFile()?DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(f.lastModified())):"Vacía";}
    private void game(){
        LinearLayout c=card(host.inGame()?"Partida en curso":"Donkey Kong Country",host.status());
        if(host.inGame()){
            action(c,"Volver al juego","resume",true,true);
            action(c,"Guardar en ranura "+(settings.slot()+1),"save",true,false);
            action(c,"Cargar ranura "+(settings.slot()+1),"load",slotFile(settings.slot()).isFile(),false);
            action(c,"Salir al inicio","quit",true,false);
        }else{
            boolean ready=host.romReady()&&!host.busy(),auto=autoFile().isFile();
            action(c,auto?"Continuar partida":"Jugar","play",ready,true);
            if(auto)action(c,"Iniciar desde el título","fresh",ready,false);
            if(!host.romReady())action(c,"Seleccionar mi ROM","rom",!host.busy(),false);
            else c.addView(Ui.text(getContext(),"ROM recordada · no necesitas volver a seleccionarla.",12,Ui.ACCENT));
        }
        LinearLayout info=card("Tu configuración",null);info.addView(Ui.text(getContext(),settings.aspect()+"  ·  Ranura "+(settings.slot()+1)+"  ·  "+(settings.flag("muted",false)?"Silencio":"Audio "+settings.get("volume",100,0,100)+" %"),14,Ui.TEXT));
        info.addView(Ui.text(getContext(),"B: saltar   ·   Y: correr / rodar / agarrar\nII o Atrás: abrir este panel.",13,Ui.MUTED));
    }
    private void graphics(){
        LinearLayout c=card("Pantalla",host.inGame()?"El formato se guarda para el próximo inicio. Los demás ajustes visuales se aplican al continuar.":"La imagen conserva sus proporciones. Los modos panorámicos siguen siendo experimentales.");
        choice(c,"Formato de imagen","aspect",new String[]{"4:3 · original","16:10 · experimental","16:9 · experimental","21:9 · ultrawide experimental"},0);
        choice(c,"Escalado","sampling",new String[]{"Píxel nítido (Nearest)","Suave (Bilinear)"},0);
        choice(c,"Color · mismos modelos que PC","palette",new String[]{"Original (Raw)","CRT","Composite","Trinitron"},0);
        LinearLayout crt=card("CRT ligero para Android","Las líneas de barrido son un efecto de presentación. No modifican los sprites ni la partida.");
        slider(crt,"Intensidad de las líneas","scanlines",0,0,60," %");
        LinearLayout edge=card("Límites del nivel","Políticas del host de PC para los bordes de la vista panorámica.");
        choice(edge,"Presentación en los extremos","edge",new String[]{"Reflejo","Barras negras","Desplazamiento","Deslizamiento (Glide)"},3);
        toggle(edge,"Arreglos acuáticos experimentales (reinicio)","aquatic",false);
    }
    private void audio(){
        LinearLayout c=card("Sonido", "Salida estéreo del núcleo original. La configuración se conserva al cerrar la aplicación.");
        toggle(c,"Silenciar","muted",false);slider(c,"Volumen","volume",100,0,100," %");
        LinearLayout m=card("Música","Música original de SNES. Los paquetes MSU-1 y el procesado CRT avanzado de PC aún no están integrados en esta versión móvil.");
        m.addView(Ui.text(getContext(),"Sin descargas ni conexión a Internet durante el juego.",12,Ui.MUTED));
    }
    private void controls(){
        LinearLayout c=card("Mando táctil","A/B/X/Y comparten tamaño y forman el rombo de SNES. L/R y Select/Start también son pares iguales.");
        toggle(c,"Mostrar botones","touch_visible",true);slider(c,"Tamaño del mando","touch_size",100,70,130," %");slider(c,"Opacidad","touch_opacity",55,20,90," %");
        action(c,"Editar posición de los botones","edit_controls",host.inGame(),true);
        if(!host.inGame())c.addView(Ui.text(getContext(),"Abre Controles desde la pausa del juego para moverlos sobre la imagen real.",12,Ui.MUTED));
        action(c,"Restablecer distribución estándar","reset_controls",true,false);
        LinearLayout g=card("Mandos y teclado","Admite hasta dos mandos. Guide o Start + Select abre la pausa. Teclado: flechas, Z/X/S/A, Q/W, Enter y Shift derecho.");
        slider(g,"Zona muerta del stick","deadzone",24,5,50," %");
        choice(g,"Botones del mando","pad_mapping",new String[]{"Posición física de SNES","Intercambiar A/B y X/Y"},0);
    }
    private void saves(){
        LinearLayout c=card("Estados rápidos","Cinco ranuras por formato. La SRAM del juego se guarda por separado, como en el port de PC.");
        for(int i=0;i<5;i++){final int slot=i;String name="Ranura "+(i+1)+" · "+modified(slotFile(i));
            Button b=Ui.button(getContext(),(settings.slot()==i?"✓  ":"")+name,()->{settings.set("slot",slot);changed();refresh();});b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);c.addView(b,new LayoutParams(-1,-2));}
        if(host.inGame()){action(c,"Guardar en ranura seleccionada","save",true,true);action(c,"Cargar ranura seleccionada","load",slotFile(settings.slot()).isFile(),false);}
        LinearLayout a=card("Continuidad automática","Crea un estado separado al pausar, salir y cada 30 segundos de juego. No reemplaza las ranuras manuales.");
        toggle(a,"Guardado automático","autosave",true);a.addView(Ui.text(getContext(),"Último: "+modified(autoFile()),12,Ui.MUTED));
        LinearLayout b=card("Copia de seguridad","Exporta o restaura partidas y configuración desde la pantalla de inicio. La ROM no se incluye.");
        action(b,"Exportar partidas y ajustes","export_backup",!host.inGame()&&!host.busy(),false);
        action(b,"Importar copia de seguridad","import_backup",!host.inGame()&&!host.busy(),false);
    }
    private void extras(){
        LinearLayout rom=card("ROM instalada","Se verifica y copia una única vez al almacenamiento privado. Cerrar el juego o reiniciar el teléfono no obliga a buscarla otra vez.");
        rom.addView(Ui.text(getContext(),host.romReady()?"USA v1.0 verificada":"No hay una ROM instalada",14,host.romReady()?Ui.ACCENT:Ui.MUTED));
        action(rom,"Cambiar la ROM…","rom",!host.inGame()&&!host.busy(),false);
        LinearLayout mod=card("Baby Kong · experimental","Mod opcional del proyecto de PC. Requiere tu propia ROM DKC3 USA compatible. Se aplica al iniciar una nueva sesión.");
        boolean installed=new File(AppSettings.gameDirectory(getContext()),"dkc3.sfc").isFile();
        if(installed)toggle(mod,"Usar Baby Kong","baby",false);
        action(mod,installed?"Cambiar ROM de DKC3…":"Importar ROM de DKC3…","rom3",!host.inGame()&&!host.busy(),false);
        LinearLayout d=card("Diagnóstico","El registro contiene errores técnicos. No exporta ROM ni partidas.");
        action(d,"Exportar diagnóstico","export_log",!host.inGame()&&!host.busy(),false);
    }
    private void credits(){
        LinearLayout c=card("DKC1Recomp · Android 0.3","Adaptación móvil del fork YlPorts/DKC1Recomp-ANdroid.");
        c.addView(Ui.text(getContext(),"Juego original: Rare / Nintendo\nRecompilación y host: contribuyentes de DKC1Recomp\nFramework: snesrecomp\nMultimedia: SDL2\nInterfaz móvil: YlPorts",14,Ui.TEXT));
        c.addView(Ui.text(getContext(),"Proyecto de aficionados no oficial y no comercial. No incluye ROM ni recursos extraídos del juego. Las licencias de terceros están incorporadas en el APK.",12,Ui.MUTED));
        LinearLayout status=card("Alcance de esta versión",null);
        status.addView(Ui.text(getContext(),"Panel organizado como el de PC, adaptado a la pantalla táctil. Hay opciones móviles específicas; no se declara paridad completa con los shaders CRT, Reconstruct, MSU-1 o rebobinado de escritorio.",13,Ui.MUTED));
    }
}
