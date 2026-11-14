package com.andriy.jarvis;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Pixel-only settings for the user's Money Tracker API token. */
public final class FinanceApiSettingsActivity extends Activity {
    private static final int WHITE=Color.rgb(237,245,255);
    private static final int MUTED=Color.rgb(165,192,212);
    private EditText tokenInput;
    private TextView status;
    private Button testButton;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        // The bearer token must not end up in Android screenshots/recents.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView outer=new ScrollView(this);
        outer.setFillViewport(true);
        outer.setBackgroundColor(Color.rgb(10,22,40));
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(24),dp(18),dp(40));
        outer.addView(page);

        addText(page,"💰 Менеджер фінансів — API",22,WHITE);
        addText(page,"Застосунок: com.freeman.moneymanager\n"+
            "Сервер: https://money.quhou123.com",14,MUTED);
        addText(page,"1. У «Менеджері фінансів» відкрий API та натисни "+
            "«Створити знову». Раніше показаний токен уже був на скріншоті. "+
            "Скопіюй НОВИЙ токен і встав нижче.",15,WHITE);

        tokenInput=new EditText(this);
        tokenInput.setHint("Встав JWT токен (він не відображатиметься)");
        tokenInput.setHintTextColor(MUTED);
        tokenInput.setTextColor(WHITE);
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        page.addView(tokenInput,new LinearLayout.LayoutParams(-1,dp(62)));

        status=addText(page,FinanceApiSecrets.configured(this)
            ? "Захищений токен уже збережений на Pixel."
            : "Токен не налаштований.",14,WHITE);

        Button save=button("🔒 Зберегти токен у Pixel",()->{
            String provided=tokenInput.getText().toString();
            String problem=FinanceApiSecrets.save(this,provided);
            tokenInput.setText("");
            if(problem!=null)status.setText(problem);
            else status.setText("Токен зашифрований Android Keystore. "+
                "Він не надсилається до ChatGPT або на Watch7.");
        });
        page.addView(save);

        testButton=button("🔍 Перевірити з’єднання з API",()->{
            testButton.setEnabled(false);
            status.setText("Перевіряю HTTPS-з’єднання із сервером...");
            FinanceApiClient.check(this,(available,message)->{
                if(isFinishing() || isDestroyed())return;
                testButton.setEnabled(true);
                status.setText(message);
            });
        });
        page.addView(testButton);

        page.addView(button("📖 Відкрити документацію API",()->
            browse(FinanceApiClient.DOCS_URL)));
        page.addView(button("🌐 Відкрити тестову сторінку",()->
            browse(FinanceApiClient.TEST_URL)));
        page.addView(button("🗑 Видалити збережений токен",()->{
            FinanceApiSecrets.clear(this);
            tokenInput.setText("");
            status.setText("Токен видалено з Pixel.");
        }));

        addText(page,"⚠️ Наразі налаштоване безпечне збереження токена та "+
            "перевірка сервера. Створення витрат і доходів напряму через API "+
            "залишиться вимкненим, доки не буде підтверджено методи й формат "+
            "запитів у документації. Голосові записи поки продовжують "+
            "працювати через наявний інтерфейс застосунку.",14,MUTED);
        setContentView(outer);
    }

    private void browse(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
        }catch(RuntimeException ignored){
            status.setText("Не вдалося відкрити браузер.");
        }
    }
    private TextView addText(LinearLayout holder,String value,int size,int color) {
        TextView view=new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0,dp(12),0,dp(6));
        holder.addView(view);
        return view;
    }
    private Button button(String label,Runnable action) {
        Button b=new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v->action.run());
        return b;
    }
    private int dp(float n){
        return Math.round(n*getResources().getDisplayMetrics().density);
    }
}
