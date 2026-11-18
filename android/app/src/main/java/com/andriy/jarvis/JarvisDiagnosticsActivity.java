package com.andriy.jarvis;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.widget.*;

/** Non-exported, scrollable on round watches. Reports contain metadata only. */
public final class JarvisDiagnosticsActivity extends Activity {
    private TextView text;
    private String report="";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        int inset=(int)(24*getResources().getDisplayMetrics().density);
        body.setPadding(inset,inset,inset,inset);
        add(body,"Оновити журнал / останні помилки",this::refresh);
        add(body,"Скопіювати звіт",()->{
            ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(c!=null)c.setPrimaryClip(ClipData.newPlainText("Діагностика Дімона",report));
            Toast.makeText(this,"Звіт скопійовано",Toast.LENGTH_SHORT).show();
        });
        add(body,"Поділитися звітом",()->{
            Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT,report);
            try { startActivity(Intent.createChooser(send,"Діагностика Дімона")); }
            catch(ActivityNotFoundException e) { Toast.makeText(this,
                "Немає програми для надсилання. Скопіюй звіт або скористайся ADB.",Toast.LENGTH_LONG).show(); }
        });
        add(body,"Очистити журнал",()->JarvisDiagnostics.clear(getApplicationContext(),()->{
            if(!isDestroyed())refresh();
        }));
        text=new TextView(this);text.setTextSize(12);text.setTextIsSelectable(true);body.addView(text);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);refresh();
    }
    private void add(LinearLayout body,String label,Runnable action) {
        Button b=new Button(this);b.setText(label);b.setOnClickListener(v->action.run());body.addView(b);
    }
    private void refresh() {
        // File and exit-history reads do not block the UI thread.
        new Thread(()->{
            String value=JarvisDiagnostics.report(getApplicationContext());
            runOnUiThread(()->{if(!isDestroyed()){report=value;text.setText(value);}});
        },"dimon-report").start();
    }
}
