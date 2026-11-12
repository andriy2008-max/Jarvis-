package com.andriy.jarvis;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** User-consent configuration for replying FROM their own Telegram account.
 * No Telegram bot token, api_hash, password or session needed.
 */
public final class TelegramReplySettingsActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroller=new ScrollView(this);
        scroller.setBackgroundColor(Color.rgb(8,20,35));
        LinearLayout layout=new LinearLayout(this);
        layout.setPadding(dp(20),dp(28),dp(20),dp(30));
        layout.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(layout);
        add(layout,"💬 Telegram — відповідь від мого імені",23);
        add(layout,"Не потрібен BotFather, Client ID або Telegram API. "+
            "Джарвіс використовує системну кнопку «Відповісти» "+
            "у сповіщенні Telegram, тому повідомлення йде від твого акаунта.",16);
        add(layout,"1. Увімкни сповіщення Telegram. "+
            "2. Дозволь Джарвісу «Доступ до сповіщень» у системних налаштуваннях. "+
            "3. Отримай повідомлення Telegram — у нього має бути кнопка «Відповісти».",15);
        Button permission=button("Надати доступ до сповіщень",()->{
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });
        layout.addView(permission);
        status=add(layout,"",15);
        layout.addView(button("Перевірити підключення",this::updateStatus));
        add(layout,"Голосова команда:\n"+
            "«Джарвіс, відповідай у телеграм Олена: Буду через десять хвилин»\n"+
            "Після перевірки отримувача:\n"+
            "«Надішли в телеграм» або «Скасуй відповідь».",16);
        add(layout,"Без сповіщення з кнопкою відповіді Джарвіс не відправляє "+
            "повідомлення. Щоб писати будь-кому у Telegram незалежно "+
            "від сповіщень, знадобиться окрема авторизація TDLib.\n"+
            "Жодна відповідь не надсилається автоматично; "+
            "підтвердження голосом обов'язкове.",14);
        setContentView(scroller);
    }
    @Override protected void onResume(){super.onResume();updateStatus();}
    private void updateStatus(){status.setText(TelegramNotificationReplyService.status(this));}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView add(LinearLayout parent,String text,int size){
        TextView label=new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(Color.rgb(225,240,250));
        label.setPadding(0,dp(12),0,dp(13));
        parent.addView(label);
        return label;
    }
    private Button button(String title,Runnable run){
        Button b=new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setOnClickListener(view->run.run());
        return b;
    }
}
