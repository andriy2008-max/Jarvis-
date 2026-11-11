package com.andriy.jarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Native phone screen for saved taxi aliases. */
public final class JarvisTaxiPlacesActivity extends Activity {
    private static final int BG=Color.rgb(8,14,29);
    private static final int PANEL=Color.rgb(17,27,48);
    private static final int TEXT=Color.rgb(237,246,255);
    private static final int CYAN=Color.rgb(80,223,251);
    private LinearLayout root;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){super.onCreate(state);render();}
    private TextView label(String value,int size){
        TextView view=new TextView(this);
        view.setText(value);view.setTextSize(size);view.setTextColor(TEXT);
        view.setPadding(dp(3),dp(7),dp(3),dp(7));
        return view;
    }
    private Button button(String title,Runnable callback){
        Button b=new Button(this);b.setAllCaps(false);
        b.setText(title);b.setTextColor(CYAN);
        b.setOnClickListener(v->callback.run());return b;
    }
    private EditText field(String hint,String value){
        EditText f=new EditText(this);
        f.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        f.setHint(hint);f.setText(value);f.setTextSize(15);
        f.setTextColor(TEXT);f.setHintTextColor(Color.rgb(161,179,208));
        return f;
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(22),dp(16),dp(30));
        scroll.addView(root);setContentView(scroll);
        root.addView(label("📍 Адреси для таксі",24));
        root.addView(label("Говори: «Джарвіс, таксі [назва]». Адреси залишаються на телефоні.",13));
        root.addView(button("← Джарвіс",this::finish));
        root.addView(button("＋ Додати адресу",()->edit(null)));
        for(JarvisTaxiPlaces.Place p:JarvisTaxiPlaces.all(this)){
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(10),dp(8),dp(10),dp(8));item.setBackgroundColor(PANEL);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);
            root.addView(item,lp);
            item.addView(label(p.label,19));item.addView(label(p.address,14));
            LinearLayout actions=new LinearLayout(this);item.addView(actions);
            actions.addView(button("Редагувати",()->edit(p)),new LinearLayout.LayoutParams(0,-2,1));
            actions.addView(button("Видалити",()->remove(p)),new LinearLayout.LayoutParams(0,-2,1));
        }
    }
    private void edit(JarvisTaxiPlaces.Place previous){
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(8),dp(16),0);
        content.addView(label("Голосова назва",14));
        EditText name=field("Наприклад: Батьки",previous==null?"":previous.label);
        content.addView(name);
        content.addView(label("Пункт призначення Bolt",14));
        EditText address=field("Місто, вулиця, будинок",previous==null?"":previous.address);
        content.addView(address);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(previous==null?"Нова адреса":"Редагувати адресу")
            .setView(content).setNegativeButton("Скасувати",null)
            .setPositiveButton("Зберегти",null).create();
        dialog.setOnShowListener(unused->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String error=JarvisTaxiPlaces.save(this,
                    previous==null?null:previous.label,
                    name.getText().toString(),address.getText().toString());
                if(error!=null)Toast.makeText(this,error,Toast.LENGTH_LONG).show();
                else{dialog.dismiss();render();}
            }));
        dialog.show();
    }
    private void remove(JarvisTaxiPlaces.Place p){
        new AlertDialog.Builder(this).setTitle("Видалити адресу?")
            .setMessage(p.label+" — "+p.address)
            .setNegativeButton("Скасувати",null)
            .setPositiveButton("Видалити",(d,w)->{
                if(!JarvisTaxiPlaces.remove(this,p.label))
                    Toast.makeText(this,"Не вдалося видалити адресу.",Toast.LENGTH_LONG).show();
                render();
            }).show();
    }
}
