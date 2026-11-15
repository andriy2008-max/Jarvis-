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
    private FinanceLedger ledger; private LinearLayout body; private String tab="Огляд"; private String query=""; private boolean unlocked=false,authenticating=false,pickingDocument=false; private String lastDeleted; private char[] backupPassword;
    public void onCreate(Bundle state){super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        ledger=new FinanceLedger(this);authenticate();
    }
    private void authenticate(){
        android.app.KeyguardManager km=(android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(km!=null&&km.isDeviceSecure()){
            Intent auth=km.createConfirmDeviceCredentialIntent("Фінанси Джарвіса","Підтвердь доступ до фінансових даних");
            if(auth!=null){authenticating=true;startActivityForResult(auth,90);return;}
        }
        unlocked=true;show();
    }
    @Override protected void onStart(){super.onStart();if(ledger!=null&&!unlocked&&!authenticating&&!pickingDocument)authenticate();}
    @Override protected void onStop(){if(!pickingDocument&&!authenticating)unlocked=false;super.onStop();}
    protected void onDestroy(){if(backupPassword!=null)Arrays.fill(backupPassword,'\\0');if(ledger!=null)ledger.close();super.onDestroy();}
    private void text(String value){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(18);t.setPadding(12,12,12,12);body.addView(t);}
    private void button(String label,Runnable action){Button b=new Button(this);b.setText(label);body.addView(b);b.setOnClickListener(v->action.run());}
    private void error(Exception e){new AlertDialog.Builder(this).setMessage(e instanceof IllegalArgumentException?e.getMessage():"Не вдалося зберегти. Перевір дані.").setPositiveButton("ОК",null).show();}
    private void show(){
        if(!unlocked)return;
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(16,24,16,24);body.setBackgroundColor(Color.rgb(10,18,30));scroll.addView(body);setContentView(scroll);
        text("JARVIS · Фінанси");
        button("Рахунок для голосових команд",()->pickAccount(id->{ledger.setSetting("default_account",""+id);Toast.makeText(this,"Типовий рахунок збережено",Toast.LENGTH_SHORT).show();}));
        button("Резервна копія",()->backup(false));button("Відновити копію",()->new AlertDialog.Builder(this).setTitle("Замінити фінансові дані?").setMessage("Спершу збережи поточну резервну копію. Відновлення замінить рахунки, операції та плани.").setNegativeButton("Скасувати",null).setPositiveButton("Продовжити",(d,w)->backup(true)).show());
        for(String name:new String[]{"Огляд","Операції","План","Звіти"})button(name,()->{tab=name;show();});
        if(tab.equals("План")){planning();return;}
        if(tab.equals("Звіти")){text(ledger.monthlyReport());text(FinanceInsights.report(ledger));text(FinanceFx.valuation(ledger));button("Оновити курси НБУ",()->{Toast.makeText(this,"Отримую курси НБУ…",Toast.LENGTH_SHORT).show();new Thread(()->{String message;try(FinanceLedger db=new FinanceLedger(getApplicationContext())){FinanceFx.refresh(db);message="Курси оновлено";}catch(Exception e){message="НБУ недоступний. Попередні курси збережено з датою.";}final String result=message;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){Toast.makeText(this,result,Toast.LENGTH_LONG).show();show();}});},"finance-fx").start();});button("Експорт CSV",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/csv");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"jarvis-finance.csv");pickingDocument=true;startActivityForResult(i,71);});return;}
        if(!tab.equals("Операції")){
        text(ledger.report());text(FinanceInsights.reminders(ledger));
        button("Увімкнути / вимкнути нагадування",()->{boolean enable=!ledger.setting("reminders","false").equals("true");ledger.setSetting("reminders",Boolean.toString(enable));if(enable)FinanceReminderJob.schedule(this);else FinanceReminderJob.cancel(this);Toast.makeText(this,enable?"Нагадування ввімкнено. Дозволь сповіщення Джарвіса в Android.":"Нагадування вимкнено",Toast.LENGTH_LONG).show();});
        button("Додати рахунок",()->form("Новий рахунок",new String[]{"Назва","Валюта (UAH)","Поточний залишок (можна пропустити)"},values->ledger.account(values[0],values[1].trim().isEmpty()?"UAH":values[1].trim().toUpperCase(Locale.ROOT),values[2])));
        button("Витрата",()->entry(false));button("Дохід / чайові",()->entry(true));
        button("Переказ",()->pickAccount(from->pickAccount(to->form("Переказ",new String[]{"Сума"},v->ledger.transfer(from,to,FinanceLedger.minor(v[0]))))));
        return;
        }
        button("Всі операції",()->{query="";show();});
        button("Пошук операцій",()->form("Пошук",new String[]{"Категорія або нотатка"},v->query=v[0]));
        if(lastDeleted!=null)button("Відновити останнє видалення",()->{ledger.restore(lastDeleted);lastDeleted=null;show();});
        text("Операції · натисни для дій");
        try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT e.id,e.amount,a.currency,e.category,a.name,e.happened,e.kind FROM entries e JOIN accounts a ON a.id=e.account WHERE e.deleted=0 AND (e.category LIKE ? OR e.note LIKE ?) ORDER BY e.happened DESC LIMIT 100",new String[]{"%"+query+"%","%"+query+"%"})){
            while(c.moveToNext()){
                final String id=c.getString(0);String label=FinanceLedger.money(c.getLong(1))+" "+c.getString(2)+" · "+c.getString(3)+" · "+c.getString(4)+" · "+android.text.format.DateFormat.format("dd.MM.yyyy",c.getLong(5));
                final String kind=c.getString(6);
                final String[] actions=kind.equals("expense")?new String[]{"Редагувати","Повернення покупки","Видалити"}:
                    kind.equals("income")?new String[]{"Редагувати","Видалити"}:new String[]{"Видалити"};
                button(label,()->new AlertDialog.Builder(this).setTitle(label).setItems(actions,(dialog,index)->{
                    String choice=actions[index];
                    if(choice.equals("Редагувати"))form("Редагувати",new String[]{"Нова сума","Категорія"},v->ledger.edit(id,v[0],v[1]));
                    else if(choice.equals("Повернення покупки"))form("Отримане повернення",new String[]{"Сума повернення"},v->ledger.refund(id,v[0]));
                    else new AlertDialog.Builder(this).setTitle("Видалити операцію?").setMessage(label+"\nПереказ буде скасовано з обох рахунків.").setNegativeButton("Ні",null).setPositiveButton("Видалити",(d,w)->{try{ledger.remove(id);lastDeleted=id;show();}catch(Exception ex){error(ex);}}).show();
                }).show());
            }
        }
    }
    private void planning(){
        String[] types={"budget","goal","payment","owed","owing"};
        String[] labels={"Бюджет на місяць","Ціль","Майбутній платіж","Мені винні","Я винен"};
        for(int n=0;n<types.length;n++){final String type=types[n],label=labels[n];button("Додати: "+label,()->form(label,new String[]{"Назва","Валюта (UAH)","Сума","Дата РРРР-ММ-ДД (необов'язково)","Категорія (для бюджету)"},v->ledger.plan(type,v[0],v[1].isEmpty()?"UAH":v[1].toUpperCase(Locale.ROOT),v[2],v[3],v[4])));}
        text("Плани не змінюють фактичні залишки. Борги тут — початковий реєстр зобов’язань.");
        try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT type,name,currency,amount,due,category,id,progress,interval_months FROM plans ORDER BY due,name",null)){
            while(c.moveToNext()){
                String detail=c.getString(1)+" · "+FinanceLedger.money(c.getLong(3))+" "+c.getString(2)+" · "+c.getString(4);
                if(c.getString(0).equals("budget")){
                    long start=java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    try(Cursor spent=ledger.getReadableDatabase().rawQuery("SELECT COALESCE(-SUM(e.amount),0) FROM entries e JOIN accounts a ON a.id=e.account WHERE e.deleted=0 AND e.kind IN ('expense','refund') AND a.currency=? AND (e.category=? OR ?='') AND e.happened>=? AND e.happened<?",new String[]{c.getString(2),c.getString(5),c.getString(5),""+start,""+(System.currentTimeMillis()+1L)})){
                        spent.moveToFirst();detail+="\nВитрачено: "+FinanceLedger.money(spent.getLong(0))+" · Залишилось: "+FinanceLedger.money(c.getLong(3)-spent.getLong(0));
                    }
                }
                String id=c.getString(6),type=c.getString(0);
                detail+="\nВиконано: "+FinanceLedger.money(c.getLong(7));
                if(type.equals("payment"))detail+=" · Інтервал: "+c.getInt(8)+" міс.";
                final String summary=detail;
                button(detail,()->new AlertDialog.Builder(this).setTitle(summary).setItems(new String[]{"Внесок / сплачено / повернуто","Періодичність платежу","Видалити план"},(dialog,index)->{
                    if(index==0)pickAccount(account->form("Вкажи фактичну суму",new String[]{"Сума"},v->ledger.settle(id,account,v[0])));
                    if(index==1)form("0 — одноразово, 1 — щомісяця, 12 — щороку",new String[]{"Місяців"},v->ledger.recurrence(id,Integer.parseInt(v[0])));
                    if(index==2)new AlertDialog.Builder(this).setMessage("Видалити план і звільнити резерв? Проведені грошові операції залишаться.").setNegativeButton("Ні",null).setPositiveButton("Так",(d,w)->{ledger.deletePlan(id);show();}).show();
                }).show());
            }
        }
    }
    private void backup(boolean restore){
        EditText field=new EditText(this);field.setHint("Пароль копії, мінімум 10 символів");field.setInputType(129);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(restore?"Пароль для відновлення":"Збережи пароль: без нього копію не відновити").setView(field).setNegativeButton("Скасувати",null).setPositiveButton("Далі",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{if(field.length()<10){field.setError("Мінімум 10 символів");return;}backupPassword=field.getText().toString().toCharArray();dialog.dismiss();Intent i=new Intent(restore?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT);i.setType("application/octet-stream");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"jarvis-finance.jfb");pickingDocument=true;startActivityForResult(i,restore?73:72);}));dialog.show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==90){authenticating=false;if(result==RESULT_OK){unlocked=true;show();}else finish();return;}
        if(request==71||request==72||request==73)pickingDocument=false;
        if(request==72||request==73){
            if(result!=RESULT_OK||data==null||data.getData()==null){if(backupPassword!=null)Arrays.fill(backupPassword,'\0');backupPassword=null;return;}
            if(backupPassword==null){error(new IllegalArgumentException("Повтори дію: пароль не зберігається після перезапуску"));return;}
            try{
                if(request==72){byte[] bytes=FinanceBackup.export(ledger,backupPassword);try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new java.io.IOException();out.write(bytes);}}
                else {try(java.io.InputStream in=getContentResolver().openInputStream(data.getData());java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                    if(in==null)throw new java.io.IOException();byte[] buf=new byte[8192];int count;while((count=in.read(buf))!=-1){if(out.size()+count>20*1024*1024)throw new IllegalArgumentException("Копія завелика");out.write(buf,0,count);}FinanceBackup.restore(ledger,backupPassword,out.toByteArray());}}
                Toast.makeText(this,request==72?"Копію збережено":"Дані відновлено",Toast.LENGTH_LONG).show();show();
            }catch(Exception e){new AlertDialog.Builder(this).setMessage("Не вдалося виконати дію. Перевір пароль і файл копії. Незавершене відновлення не змінює дані.").setPositiveButton("ОК",null).show();}
            finally{Arrays.fill(backupPassword,'\0');backupPassword=null;}return;
        }
        if(request==71&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new java.io.IOException();out.write(ledger.csv().getBytes(java.nio.charset.StandardCharsets.UTF_8));Toast.makeText(this,"CSV збережено",Toast.LENGTH_SHORT).show();
            }catch(Exception e){error(e);}
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
