package com.andriy.jarvis;
import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.widget.*;
import java.util.*;

/** Native finance screen backed by the same ledger as voice commands. */
public final class FinanceActivity extends Activity {
    private FinanceLedger ledger; private LinearLayout body;
    public void onCreate(Bundle state){super.onCreate(state);ledger=new FinanceLedger(this);show();}
    protected void onDestroy(){if(ledger!=null)ledger.close();super.onDestroy();}
    private void text(String value){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(18);t.setPadding(12,12,12,12);body.addView(t);}
    private void button(String label,Runnable action){Button b=new Button(this);b.setText(label);body.addView(b);b.setOnClickListener(v->action.run());}
    private void error(Exception e){new AlertDialog.Builder(this).setMessage(e instanceof IllegalArgumentException?e.getMessage():"Не вдалося зберегти. Перевір дані.").setPositiveButton("ОК",null).show();}
    private void show(){
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(16,24,16,24);body.setBackgroundColor(Color.rgb(10,18,30));scroll.addView(body);setContentView(scroll);
        text("JARVIS · Фінанси");text(ledger.report());
        button("Додати рахунок",()->form("Новий рахунок",new String[]{"Назва","Валюта (UAH)","Поточний залишок (можна пропустити)"},values->ledger.account(values[0],values[1].trim().isEmpty()?"UAH":values[1].trim().toUpperCase(Locale.ROOT),values[2])));
        button("Витрата",()->entry(false));button("Дохід / чайові",()->entry(true));
        button("Переказ",()->pickAccount(from->pickAccount(to->form("Переказ",new String[]{"Сума"},v->ledger.transfer(from,to,FinanceLedger.minor(v[0]))))));
        text("Операції · натисни для видалення");
        try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT e.id,e.amount,a.currency,e.category,a.name,e.happened FROM entries e JOIN accounts a ON a.id=e.account WHERE e.deleted=0 ORDER BY e.happened DESC LIMIT 100",null)){
            while(c.moveToNext()){
                final String id=c.getString(0);String label=FinanceLedger.money(c.getLong(1))+" "+c.getString(2)+" · "+c.getString(3)+" · "+c.getString(4)+" · "+android.text.format.DateFormat.format("dd.MM.yyyy",c.getLong(5));
                button(label,()->new AlertDialog.Builder(this).setTitle("Видалити операцію?").setMessage(label+"\nДля переказу буде видалено обидві сторони.").setNegativeButton("Ні",null).setPositiveButton("Видалити",(d,w)->{ledger.remove(id);show();}).show());
            }
        }
    }
    private interface Values{void run(String[] values);}
    private interface Account{void run(long id);}
    private void form(String title,String[] hints,Values action){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);EditText[] fields=new EditText[hints.length];
        for(int i=0;i<hints.length;i++){fields[i]=new EditText(this);fields[i].setHint(hints[i]);box.addView(fields[i]);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("Скасувати",null).setPositiveButton("Зберегти",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String[] values=new String[fields.length];for(int i=0;i<fields.length;i++)values[i]=fields[i].getText().toString();try{action.run(values);dialog.dismiss();show();}catch(Exception e){error(e);}}));dialog.show();
    }
    private void pickAccount(Account action){
        ArrayList<Long> ids=new ArrayList<>();ArrayList<String> names=new ArrayList<>();
        try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT id,name,currency FROM accounts ORDER BY id",null)){while(c.moveToNext()){ids.add(c.getLong(0));names.add(c.getString(1)+" · "+c.getString(2));}}
        if(ids.isEmpty()){error(new IllegalArgumentException("Спочатку додай рахунок"));return;}
        new AlertDialog.Builder(this).setTitle("Рахунок").setItems(names.toArray(new String[0]),(d,w)->action.run(ids.get(w))).show();
    }
    private void entry(boolean income){pickAccount(account->form(income?"Дохід":"Витрата",new String[]{"Сума","Категорія","Нотатка"},v->{if(v[1].trim().isEmpty())throw new IllegalArgumentException("Вкажи категорію");ledger.record(UUID.randomUUID().toString(),account,FinanceLedger.minor(v[0]),income?"income":"expense",v[1],v[2],System.currentTimeMillis());}));}
}
