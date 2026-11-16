package com.andriy.jarvis;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Owner-configured, off by default; only reads Portal's localhost API. */
public final class PortalUiSettingsActivity extends Activity {
    private EditText token;
    private CheckBox enabled;
    private TextView result;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size) {
        TextView v=new TextView(this);
        v.setText(s);v.setTextColor(Color.rgb(232,247,255));
        v.setTextSize(size);v.setPadding(0,dp(10),0,dp(10));
        return v;
    }
    private Button button(String title,Runnable run) {
        Button b=new Button(this);
        b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->run.run());
        return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(10,22,40));
        LinearLayout body=new LinearLayout(this);
        body.setPadding(dp(18),dp(24),dp(18),dp(32));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        body.addView(text("👁 JARVIS + Mobilerun Portal",23));
        body.addView(text(
            "Portal — додаткове читання кнопок Bolt і Spotify. "+
            "Джарвіс залишається головним керівником. "+
            "Підключення лише через HTTP 127.0.0.1:8080, без хмарного агента.",14));
        body.addView(text("1. Встанови Mobilerun Portal із GitHub. "+
            "В Android → Спеціальні можливості увімкни його службу. "+
            "В Mobilerun Portal увімкни Local HTTP Server, порт 8080. "+
            "Не вмикай Reverse/Cloud Connection.",14));
        body.addView(button("Відкрити Mobilerun Portal",()->{
            Intent launch=getPackageManager().getLaunchIntentForPackage(PortalUiSettings.PACKAGE);
            if(launch!=null)startActivity(launch);
            else result.setText("Встанови Mobilerun Portal на Pixel. "+
                "https://github.com/droidrun/mobilerun-portal");
        }));
        body.addView(text("2. Скопіюй токен локального API з головного екрана Portal "+
            "та встав нижче. Токен зберігається зашифрованим Android Keystore.",14));
        token=new EditText(this);
        token.setSingleLine(true);
        token.setText(PortalUiSettings.token(this));
        token.setHint("Токен Mobilerun Portal");
        token.setTextColor(Color.WHITE);
        token.setHintTextColor(Color.LTGRAY);
        token.setInputType(InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(token);
        enabled=new CheckBox(this);
        enabled.setText("Дозволити Джарвісу використовувати локальний Portal");
        enabled.setTextColor(Color.WHITE);
        enabled.setChecked(PortalUiSettings.enabled(this));
        body.addView(enabled);
        result=text("З'єднання ще не перевірено.",14);
        body.addView(result);
        body.addView(button("Зберегти",this::save));
        body.addView(button("Перевірити зчитування елементів",()->{
            if(!save())return;
            result.setText("Читаю поточний екран через Portal...");
            PortalUiBridge.inspect(this,(ok,detail)->{
                if(!isFinishing()&&!isDestroyed())result.setText(detail);
            });
        }));
        body.addView(button("Вимкнути й видалити токен",()->{
            boolean ok=PortalUiSettings.save(this,"",false);
            token.setText("");
            enabled.setChecked(false);
            result.setText(ok?"Portal вимкнено, токен видалено.":
                "Не вдалося видалити налаштування.");
        }));
        body.addView(text("Без збереженого токена Portal не використовується. "+
            "Джарвіс продовжить використовувати власну службу керування "+
            "екраном. Ніколи не передавай токен у чат чи скриншот.",13));
        setContentView(scroll);
    }
    private boolean save() {
        boolean ok=PortalUiSettings.save(this,token.getText().toString(),
            enabled.isChecked());
        result.setText(ok?"Налаштування Portal збережено локально.":
            "Невірний токен або Android Keystore недоступний.");
        return ok;
    }
}
