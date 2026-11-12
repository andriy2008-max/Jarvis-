package com.andriy.jarvis;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Manual Telegram API/TDLib authentication for the user's PERSONAL account.
 * Never asks for BotFather credentials or notification listener permission.
 */
public final class TelegramReplySettingsActivity extends Activity {
    private TextView status;
    private EditText idInput,hashInput,phoneInput,codeInput,passwordInput;
    private TelegramTdClient client;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        client=TelegramTdClient.get(this);
        ScrollView scroller=new ScrollView(this);
        scroller.setBackgroundColor(Color.rgb(8,20,35));
        LinearLayout layout=new LinearLayout(this);
        layout.setPadding(dp(20),dp(25),dp(20),dp(32));
        layout.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(layout);
        add(layout,"💬 Telegram API · твій акаунт",23);
        add(layout,"Підключення через Telegram TDLib (MTProto): без бота, "+
            "без сповіщень і без керування чужим екраном. "+
            "Джарвіс працює від твого особистого акаунта.",15);

        add(layout,"1. Відкрий my.telegram.org → API development tools. "+
            "Створи API-застосунок, скопіюй api_id і api_hash. "+
            "НЕ надсилай api_hash або код входу в чат чи GitHub.",15);
        layout.addView(button("Відкрити my.telegram.org",()->{
            startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://my.telegram.org")));
        }));
        TelegramApiSecrets.Credentials saved=TelegramApiSecrets.load(this);
        add(layout,"api_id",15);
        idInput=edit(saved==null?"":Integer.toString(saved.apiId),
            InputType.TYPE_CLASS_NUMBER);
        layout.addView(idInput);
        add(layout,"api_hash (32 символи, зберігається в Android Keystore)",15);
        hashInput=edit("",InputType.TYPE_CLASS_TEXT|
            InputType.TYPE_TEXT_VARIATION_PASSWORD);
        hashInput.setHint(saved==null?"api_hash":
            "Збережено: залиш порожнім, щоб не змінювати");
        layout.addView(hashInput);
        layout.addView(button("Зберегти API та підключитися",()->{
            TelegramApiSecrets.Credentials stored=TelegramApiSecrets.load(this);
            String id=idInput.getText().toString().trim();
            String hash=hashInput.getText().toString().trim();
            if(hash.isEmpty()&&stored!=null&&id.equals(Integer.toString(stored.apiId)))
                hash=stored.apiHash;
            if(client.isAuthorized() && stored!=null &&
                (!id.equals(Integer.toString(stored.apiId)) ||
                !hash.equals(stored.apiHash))) {
                toast("Для зміни API на вже активному акаунті потрібне повторне налаштування.");
                return;
            }
            String error=TelegramApiSecrets.save(this,id,hash);
            if(error!=null){toast(error);return;}
            hashInput.setText("");
            client.start((ok,msg)->{
                toast(msg);
                refresh();
            });
        }));

        add(layout,"2. Авторизуй свій номер Telegram",17);
        add(layout,"Номер телефону (+380XXXXXXXXX)",15);
        phoneInput=edit("",InputType.TYPE_CLASS_PHONE);
        phoneInput.setHint("+380...");
        layout.addView(phoneInput);
        layout.addView(button("Отримати код входу",()->{
            client.phone(phoneInput.getText().toString(),(ok,message)->{
                toast(message);refresh();
            });
        }));

        add(layout,"Код підтвердження з Telegram",15);
        codeInput=edit("",InputType.TYPE_CLASS_TEXT);
        codeInput.setHint("Код входу");
        layout.addView(codeInput);
        layout.addView(button("Підтвердити код",()->{
            String code=codeInput.getText().toString();
            codeInput.setText("");
            client.code(code,(ok,message)->{toast(message);refresh();});
        }));

        add(layout,"Пароль двоетапного захисту (якщо Telegram запросить)",15);
        passwordInput=edit("",InputType.TYPE_CLASS_TEXT|
            InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);
        layout.addView(button("Підтвердити пароль 2FA",()->{
            String secret=passwordInput.getText().toString();
            passwordInput.setText("");
            client.password(secret,(ok,message)->{toast(message);refresh();});
        }));

        add(layout,"3. Перевір стан підключення",17);
        status=add(layout,"",16);
        layout.addView(button("Перевірити Telegram API",this::refresh));
        add(layout,"Голосом:\n"+
            "«Джарвіс, напиши в телеграм Олена: Буду через десять хвилин»\n"+
            "Джарвіс знайде чат із точною назвою «Олена» та підготує чернетку.\n"+
            "Для відправлення окремо скажи «Надішли в телеграм».\n"+
            "«Скасуй відповідь» — нічого не відправляє.",15);
        add(layout,"Це Telegram API, а не бот. Доступ до сповіщень не потрібен. "+
            "Функція надсилає тільки твої явно підтверджені голосові повідомлення. "+
            "Перший варіант працює з особистими чатами; групи й автоматичні відповіді вимкнені.",14);
        setContentView(scroller);
    }
    @Override protected void onResume(){
        super.onResume();
        client.setStatusListener(message->{
            if(isFinishing()||isDestroyed())return;
            status.setText(message);
        });
        if(TelegramApiSecrets.load(this)!=null)client.start(null);
        refresh();
    }
    @Override protected void onPause(){
        client.setStatusListener(null);
        super.onPause();
    }
    private void refresh(){status.setText(client.status());}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView add(LinearLayout parent,String text,int size){
        TextView label=new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(Color.rgb(225,240,250));
        label.setPadding(0,dp(10),0,dp(8));
        parent.addView(label);
        return label;
    }
    private EditText edit(String text,int type){
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setInputType(type);
        field.setText(text);
        field.setTextSize(16);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.GRAY);
        field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            Color.rgb(64,201,233)));
        return field;
    }
    private Button button(String text,Runnable action){
        Button b=new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(v->action.run());
        return b;
    }
}
