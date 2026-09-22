package com.ylports.dkc1recomp;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Small atomic JSON file, re-read across launcher / :game processes. No stale SharedPreferences cache. */
public final class AppSettings {
    public static final String[] ASPECTS={"4:3","16:10","16:9","21:9","device"};
    private final AtomicFile file;
    private JSONObject data=new JSONObject();
    public AppSettings(Context context) {
        File dir=new File(context.getFilesDir(),"config");
        if(!dir.isDirectory())dir.mkdirs();
        file=new AtomicFile(new File(dir,"settings-v3.json"));reload();
    }
    public void reload(){
        try(FileInputStream in=file.openRead()){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Settings too large");out.write(b,0,n);}
            data=new JSONObject(out.toString("UTF-8"));
        }catch(Exception ignored){data=new JSONObject();}
    }
    public int get(String key,int fallback,int min,int max){return Math.max(min,Math.min(max,data.optInt(key,fallback)));}
    public boolean flag(String key,boolean fallback){return data.optBoolean(key,fallback);}
    public String text(String key,String fallback){return data.optString(key,fallback);}
    public void set(String key,int value){put(key,value);}
    public void set(String key,boolean value){put(key,value);}
    public void set(String key,String value){put(key,value);}
    private void put(String key,Object value){try{data.put(key,value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    public float position(String key,float fallback){
        double value=data.optDouble(key,Double.NaN);
        return Double.isNaN(value)||Double.isInfinite(value)?fallback:(float)Math.max(0,Math.min(1,value));
    }
    public void setPosition(String key,float value){if((!Float.isNaN(value)&&!Float.isInfinite(value)))put(key,(double)Math.max(0,Math.min(1,value)));}
    public void clearPositions(){
        java.util.ArrayList<String> keys=new java.util.ArrayList<>();
        java.util.Iterator<String> it=data.keys();while(it.hasNext()){String k=it.next();if(k.startsWith("pad3_"))keys.add(k);}
        for(String key:keys)data.remove(key);
    }
    public boolean save(){
        FileOutputStream out=null;
        try{out=file.startWrite();out.write(data.toString(2).getBytes(StandardCharsets.UTF_8));file.finishWrite(out);return true;}
        catch(Exception e){if(out!=null)file.failWrite(out);return false;}
    }
    public void activateMod(VisualMod mod){
        if(text("mod_name","").isEmpty())for(String key:VisualMod.KEYS)set("mod_previous_"+key,get(key,key.equals("edge")?3:0,0,100));
        for(String key:VisualMod.KEYS)if(mod.values.containsKey(key))set(key,mod.values.get(key));
        set("mod_name",mod.name);save();
    }
    public void removeMod(){
        if(text("mod_name","").isEmpty())return;
        for(String key:VisualMod.KEYS)set(key,get("mod_previous_"+key,key.equals("edge")?3:0,0,100));
        set("mod_name","");save();
    }
    public int aspectIndex(){return get("aspect",0,0,4);}
    public String aspect(){return ASPECTS[aspectIndex()];}
    public String aspectKey(){String value=aspect();return "device".equals(value)?"device":value.replace(':','x');}
    public int slot(){return get("slot",0,0,4);}
    public static File gameDirectory(Context context){return new File(context.getFilesDir(),"game");}
    public boolean usesMusic(Context c){return flag("music_enabled",false)&&MusicPack.isComplete(new File(c.getFilesDir(),"music/current"));}
    public String saveKey(Context c){String key=ContentMods.activeKey(c.getFilesDir());boolean compat=ContentMods.activeCompatibility(c.getFilesDir());return (compat?"4x3":aspectKey())+(!ContentMods.activeUsesOwnMusic(c.getFilesDir())&&usesMusic(c)?"-msu1":"")+(key.isEmpty()?"":"-mod-"+key);}
    public File autoState(Context c){return new File(gameDirectory(c),"autosave-"+saveKey(c)+".state");}
    public File slotState(Context c,int slot){return new File(gameDirectory(c),"quicksave-"+saveKey(c)+(slot==0?"":"-slot"+(slot+1))+".state");}
}