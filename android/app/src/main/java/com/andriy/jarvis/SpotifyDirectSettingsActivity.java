package com.andriy.jarvis;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.security.MessageDigest;
import java.util.Locale;

/** Local Spotify developer connection and voice alias configuration. */
public final class SpotifyDirectSettingsActivity extends Activity {
    private static final int WHITE=Color.rgb(236,246,255);
    private static final int MUTED=Color.rgb(164,194,214);
    private EditText clientField,aliasesField;
    private SpotifyAppRemoteBridge connection;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView page=new ScrollView(this);
        page.setBackgroundColor(Color.rgb(10,22,40));
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(25),dp(18),dp(38));
        page.addView(body);

        addText(body,"🎵 Spotify — прямий запуск",23,WHITE);
        addText(body,"Джарвіс запускатиме плейлист через офіційний Spotify App Remote SDK без натискання Play. Spotify має бути встановлений, а ти — увійти в акаунт.",15,MUTED);
        addText(body,"1. Створи застосунок у Spotify Developer Dashboard та вибери Android. Укажи цей пакет і SHA-1:",16,WHITE);
        addText(body,"Пакет: "+getPackageName()+"\nSHA-1: "+certificateSha1(),14,WHITE);
        addText(body,"Redirect URI, який треба додати в Spotify Dashboard:",15,WHITE);
        addText(body,SpotifyDirectSettings.REDIRECT_URI,14,WHITE);
        Button dashboard=button("Відкрити Spotify Developer Dashboard",()->{
            startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://developer.spotify.com/dashboard")));
        });
        body.addView(dashboard);

        addText(body,"2. Встав Spotify Client ID (це не Client Secret):",16,WHITE);
        clientField=edit(SpotifyDirectSettings.clientId(this),1);
        clientField.setSingleLine(true);
        clientField.setInputType(InputType.TYPE_CLASS_TEXT);
        body.addView(clientField);

        addText(body,"3. Додай плейлисти для голосових команд. Один на рядок: Назва | Посилання Spotify",16,WHITE);
        addText(body,"Наприклад:\nУкр муз | https://open.spotify.com/playlist/...\nРеп | https://open.spotify.com/playlist/...\nWorkout | https://open.spotify.com/playlist/...\n\nДля «Увімкни реп» потрібен окремий плейлист «Реп»: жанровий фільтр уподобаних пісень не має окремого Spotify URI.",13,MUTED);
        aliasesField=edit(SpotifyDirectSettings.entries(this),7);
        aliasesField.setGravity(Gravity.TOP | Gravity.START);
        body.addView(aliasesField);

        Button save=button("Зберегти Spotify",()->saveSettings());
        body.addView(save);
        Button connect=button("Підключити / перевірити Spotify",()->{
            if(!saveSettings())return;
            String id=SpotifyDirectSettings.clientId(this);
            if(id.isEmpty()){
                Toast.makeText(this,"Спочатку вкажи Spotify Client ID.",
                    Toast.LENGTH_LONG).show();
                return;
            }
            if(connection!=null)connection.cancel();
            Toast.makeText(this,"Підключаю Spotify...",Toast.LENGTH_SHORT).show();
            connection=new SpotifyAppRemoteBridge(this,id,null,(ok,message)->{
                if(isFinishing()||isDestroyed())return;
                Toast.makeText(this,message,Toast.LENGTH_LONG).show();
            });
            connection.connect();
        });
        body.addView(connect);
        addText(body,"Якщо Client ID, авторизації або посилання немає, Джарвіс залишить старий спосіб: знайти плейлист та натиснути Play через Accessibility. Для окремого треку на вимогу Spotify може вимагати Premium.",14,MUTED);
        setContentView(page);
    }

    private boolean saveSettings() {
        String error=SpotifyDirectSettings.save(this,
            clientField.getText().toString(),aliasesField.getText().toString());
        if(error!=null){
            Toast.makeText(this,error,Toast.LENGTH_LONG).show();
            return false;
        }
        Toast.makeText(this,"Spotify налаштування збережено.",Toast.LENGTH_SHORT).show();
        return true;
    }
    @Override protected void onDestroy() {
        if(connection!=null)connection.cancel();
        super.onDestroy();
    }
    private String certificateSha1() {
        try {
            PackageInfo info;
            Signature[] signatures;
            if(Build.VERSION.SDK_INT>=28) {
                info=getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
                signatures=info.signingInfo.hasMultipleSigners()?
                    info.signingInfo.getApkContentsSigners():
                    info.signingInfo.getSigningCertificateHistory();
            } else {
                info=getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_SIGNATURES);
                signatures=info.signatures;
            }
            if(signatures==null||signatures.length==0)return "недоступний";
            byte[] digest=MessageDigest.getInstance("SHA-1").digest(signatures[0].toByteArray());
            StringBuilder out=new StringBuilder();
            for(byte part:digest){
                if(out.length()>0)out.append(':');
                out.append(String.format(Locale.ROOT,"%02X",part & 0xff));
            }
            return out.toString();
        }catch(Exception ignored){return "недоступний";}
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void addText(LinearLayout parent,String title,int size,int color) {
        TextView text=new TextView(this);
        text.setText(title);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setPadding(0,dp(12),0,dp(7));
        parent.addView(text);
    }
    private EditText edit(String text,int rows) {
        EditText edit=new EditText(this);
        edit.setText(text);
        edit.setTextColor(WHITE);
        edit.setHintTextColor(MUTED);
        edit.setTextSize(15);
        edit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            Color.rgb(70,180,218)));
        edit.setMinLines(rows);
        edit.setPadding(dp(10),dp(8),dp(10),dp(8));
        return edit;
    }
    private Button button(String title,Runnable click) {
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setOnClickListener(v->click.run());
        return button;
    }
}
