package com.ylports.dkc1recomp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Native Android widgets styled after the PC host's dark options panel. */
public final class Ui {
    public static final int BG=0xff1c1d21,PANEL=0xff24262c,CARD=0xff2b2d34,LINE=0xff3e424d,
        TEXT=0xffebebf0,MUTED=0xffa9b0be,ACCENT=0xff8cb6f1;
    private Ui(){}
    public static int dp(Context c,float n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    public static GradientDrawable bg(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    public static TextView text(Context c,String value,int size,int color){
        TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");
        v.setPadding(0,dp(c,3),0,dp(c,3));return v;
    }
    public static TextView title(Context c,String value,int size){TextView v=text(c,value,size,TEXT);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    public static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    public static Button button(Context c,String label,Runnable action){
        Button b=new Button(c);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(TEXT);
        b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,14),dp(c,4),dp(c,14),dp(c,4));
        b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff262831,CARD}));
        b.setOnClickListener(v->action.run());return b;
    }
    public static void primary(Button b){b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,BG}));b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff30353f,ACCENT}));}
    public static LinearLayout card(LinearLayout parent,String title,String help){
        Context c=parent.getContext();LinearLayout v=column(c);v.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,12));
        v.setBackground(bg(CARD,dp(c,10)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(c,10);parent.addView(v,lp);
        if(title!=null)v.addView(title(c,title,16));if(help!=null&&!help.isEmpty())v.addView(text(c,help,12,MUTED));return v;
    }
    public static void message(Context c,String title,String value){new android.app.AlertDialog.Builder(c).setTitle(title).setMessage(value).setPositiveButton("Entendido",null).show();}
    public static void immersive(android.app.Activity a){
        if(android.os.Build.VERSION.SDK_INT>=30){a.getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController i=a.getWindow().getInsetsController();if(i!=null){i.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);i.hide(WindowInsets.Type.systemBars());}}
        else a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
