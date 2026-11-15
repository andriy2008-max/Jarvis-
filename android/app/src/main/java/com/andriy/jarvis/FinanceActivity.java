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
    private FinanceLedger ledger; private LinearLayout body; private String tab="Огляд"; private String query="",filterKind="",filterPeriod="all"; private long filterAccount=-1,filterMin=0,filterMax=Long.MAX_VALUE; private boolean unlocked=false,authenticating=false,pickingDocument=false,backupRunning=false; private String lastDeleted; private char[] backupPassword;
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
    protected void onDestroy(){if(backupPassword!=null)Arrays.fill(backupPassword,'\0');if(ledger!=null)ledger.close();super.onDestroy();}
    private void text(String value){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(18);t.setPadding(12,12,12,12);body.addView(t);}
    private void button(String label,Runnable action){Button b=new Button(this);b.setText(label);body.addView(b);b.setOnClickListener(v->action.run());}
    private void showInfo(String value){new AlertDialog.Builder(this).setMessage(value).setPositiveButton("ОК",null).show();}
    private void error(Exception e){new AlertDialog.Builder(this).setMessage(e instanceof IllegalArgumentException?e.getMessage():"Не вдалося зберегти. Перевір дані.").setPositiveButton("ОК",null).show();}
    private void show(){
        if(!unlocked)return;
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(16,24,16,24);body.setBackgroundColor(Color.rgb(10,18,30));scroll.addView(body);setContentView(scroll);
        text("JARVIS · Фінанси");
        try(Cursor count=ledger.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM accounts",null)){
            count.moveToFirst();
            if(count.getLong(0)==0){
                text("Перший запуск: додай рахунок (готівка чи картка), вибери UAH і вкажи відомий залишок. Якщо залишок невідомий, залиш поле порожнім.");
                button("Почати · додати перший рахунок",()->newAccount());
            }else if(ledger.setting("default_account","").isEmpty()){
                text("Для голосових витрат вибери типовий рахунок. До вибору Джарвіс не записуватиме операції навмання.");
            }
        }
        button("Дозволити / заборонити озвучування балансу при блокуванні",()->{
            boolean allow=!ledger.setting("finance_voice_locked","false").equals("true");
            ledger.setSetting("finance_voice_locked",Boolean.toString(allow));
            Toast.makeText(this,allow?"Озвучування при блокуванні дозволено":"Суми при блокуванні приховані",Toast.LENGTH_LONG).show();
        });
        button("Рахунок для голосових команд",()->pickAccount(id->{ledger.setSetting("default_account",""+id);Toast.makeText(this,"Типовий рахунок збережено",Toast.LENGTH_SHORT).show();}));
        button("Резервна копія",()->backup(false));
        if(FinanceBackupFiles.hasPrevious(this))
            button("Повернути стан перед останнім відновленням",this::recoverPrevious);button("Відновити копію",()->new AlertDialog.Builder(this).setTitle("Замінити фінансові дані?").setMessage("Спершу збережи поточну резервну копію. Відновлення замінить рахунки, операції та плани.").setNegativeButton("Скасувати",null).setPositiveButton("Продовжити",(d,w)->backup(true)).show());
        for(String name:new String[]{"Огляд","Операції","План","Звіти"})button(name,()->{tab=name;show();});
        if(tab.equals("План")){planning();return;}
        if(tab.equals("Звіти")){
            FinanceBarChart chart=new FinanceBarChart(this,ledger);
            body.addView(chart,new LinearLayout.LayoutParams(-1,(int)(240*getResources().getDisplayMetrics().density)));
            text(FinanceAnalytics.report(ledger,"month"));
            button("Звіт: сьогодні",()->showInfo(FinanceAnalytics.report(ledger,"today")));
            button("Звіт: тиждень",()->showInfo(FinanceAnalytics.report(ledger,"week")));
            button("Звіт: минулий місяць",()->showInfo(FinanceAnalytics.report(ledger,"previous_month")));
            text(FinanceAnalytics.budgets(ledger));
            text(FinanceAnalytics.knownPayments(ledger));
            text(FinanceInsights.report(ledger));
            text(FinanceFx.valuation(ledger));
            text(FinanceAssets.positions(ledger));
            button("Підсумки змін і чайових",()->showInfo(FinanceShifts.report(ledger)));
            button("Аудит витрат і повторів",()->showInfo(FinanceAnalytics.audit(ledger)));
            button("Сценарій: зменшення витрат на N%",()->form("Сценарій без змін у бюджеті",
                new String[]{"Скоротити витрати на N%"},v->showInfo(FinanceAnalytics.scenario(ledger,Integer.parseInt(v[0])))));
            button("Оновити курси НБУ",()->{
                Toast.makeText(this,"Отримую курси НБУ…",Toast.LENGTH_SHORT).show();
                new Thread(()->{
                    String message;
                    try(FinanceLedger db=new FinanceLedger(getApplicationContext())){
                        FinanceFx.refresh(db);message="Курси оновлено";
                    }catch(Exception e){message="НБУ недоступний. Попередні курси збережено з датою.";}
                    final String result=message;
                    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){
                        Toast.makeText(this,result,Toast.LENGTH_LONG).show();show();
                    }});
                },"finance-fx").start();
            });
            button("Експорт CSV",()->{
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.setType("text/csv");i.addCategory(Intent.CATEGORY_OPENABLE);
                i.putExtra(Intent.EXTRA_TITLE,"jarvis-finance.csv");pickingDocument=true;
                startActivityForResult(i,71);
            });
            return;
        }
        if(!tab.equals("Операції")){
        text(ledger.report());text(FinanceInsights.reminders(ledger));
        button("Увімкнути / вимкнути нагадування",()->{boolean enable=!ledger.setting("reminders","false").equals("true");ledger.setSetting("reminders",Boolean.toString(enable));if(enable)FinanceReminderJob.schedule(this);else FinanceReminderJob.cancel(this);Toast.makeText(this,enable?"Нагадування ввімкнено. Дозволь сповіщення Джарвіса в Android.":"Нагадування вимкнено",Toast.LENGTH_LONG).show();});
        button("Тихі години для фінансових сповіщень",()->formWithDefaults("Тихі години · 24-годинний формат",
            new String[]{"Початок HH:MM","Кінець HH:MM"},
            new String[]{ledger.setting("quiet_start","22:00"),ledger.setting("quiet_end","08:00")},
            v->{
                try{java.time.LocalTime.parse(v[0]);java.time.LocalTime.parse(v[1]);}
                catch(RuntimeException e){throw new IllegalArgumentException("Введи час як 22:00 або 08:00");}
                ledger.setSetting("quiet_start",v[0]);ledger.setSetting("quiet_end",v[1]);
            }));
        button("Тижневий фінансовий дайджест · увімкнути / вимкнути",()->toggleFinanceSetting("digest_weekly"));
        button("Місячний фінансовий дайджест · увімкнути / вимкнути",()->toggleFinanceSetting("digest_monthly"));
        button("Додати рахунок",this::newAccount);
        button("Звірити баланс рахунку",()->pickAccount(id->form("Фактично на рахунку",new String[]{"Сума за даними банку або готівка"},v->{
            String comparison=FinanceAssets.difference(ledger,id,v[0]);
            new AlertDialog.Builder(this).setTitle("Підтвердити звірку?").setMessage(comparison)
                .setNegativeButton("Ні",null).setPositiveButton("Звірити",(d,w)->{
                    try{FinanceAssets.reconcile(ledger,id,v[0]);show();}
                    catch(Exception e){error(e);}
                }).show();
        })));
        button("Обмін валют (фактичний курс і комісія)",()->pickAccount(from->pickAccount(to->
            form("Обмін валют",new String[]{"Списано з першого рахунку","Отримано на другий рахунок","Комісія у валюті першого рахунку (0)"},
                v->FinanceAssets.exchange(ledger,from,to,v[0],v[1],v[2])))));
        button("Криптоактив · кількість і ручна оцінка",()->form("Криптоактив",
            new String[]{"Символ (BTC, ETH тощо)","Кількість","UAH за одиницю (необов'язково)","Дата ціни РРРР-ММ-ДД"},
            v->FinanceAssets.position(ledger,v[0],v[1],v[2],v[3])));
        button("Швидко записати текстом",()->form("Витрата / дохід",new String[]{"Наприклад: кава 85 або чайові 650 готівкою"},v->{
            String voice=v[0].trim();
            if(voice.matches("(?iu)^.+[0-9]+.*\\s+і\\s+.+[0-9]+.*$")&&!FinanceBatchCommands.looksLikeBatch(voice))
                voice="запиши витрати "+voice;
            if(voice.matches("(?iu)^кава\\s+[0-9]+(?:[,.][0-9]{1,2})?$"))voice="витрата "+voice;
            final String[] error={null};
            FinanceManagerController.start(this,voice,new FinanceManagerController.Listener(){
                public void onProgress(String message){}
                public void onFinished(boolean success,String message){
                    if(!success)error[0]=message;
                    else Toast.makeText(FinanceActivity.this,message,Toast.LENGTH_LONG).show();
                }
            });
            if(error[0]!=null)throw new IllegalArgumentException(error[0]);
        }));
        button("Чайові за зміну · розділити між рахунками",()->pickAccount(cashAccount->pickAccount(cardAccount->
            form("Чайові за зміну",new String[]{"Назва зміни","Всього чайових","Готівкою","На картку","Години роботи (необов'язково)"},
                v->FinanceShifts.tips(ledger,v[0],v[1],v[2],v[3],cashAccount,cardAccount,v[4])))));
        button("Витрата",()->entry(false));button("Дохід / чайові",()->entry(true));
        button("Переказ",()->pickAccount(from->pickAccount(to->form("Переказ",new String[]{"Сума"},v->ledger.transfer(from,to,FinanceLedger.minor(v[0]))))));
        return;
        }
        button("Скинути фільтри",()->{query="";filterKind="";filterPeriod="all";filterAccount=-1;filterMin=0;filterMax=Long.MAX_VALUE;show();});
        button("Рахунок для фільтру",()->pickAccount(id->{filterAccount=id;show();}));
        button("Тип операції: "+(filterKind.isEmpty()?"усі":filterKind),()->{
            String[] labels={"Усі","Витрати","Доходи","Перекази","Повернення","Борги"};
            String[] keys={"","expense","income","transfer","refund","debt"};
            new AlertDialog.Builder(this).setItems(labels,(dialog,index)->{filterKind=keys[index];show();}).show();
        });
        button("Період: "+filterPeriod,()->{
            String[] labels={"Увесь час","Сьогодні","Цей тиждень","Цей місяць","Попередній місяць"};
            String[] keys={"all","today","week","month","previous_month"};
            new AlertDialog.Builder(this).setItems(labels,(dialog,index)->{filterPeriod=keys[index];show();}).show();
        });
        button("Фільтр сум",()->form("Межі суми (можна пропустити)",new String[]{"Від, у валюті рахунку","До, у валюті рахунку"},
            v->{filterMin=v[0].trim().isEmpty()?0:FinanceLedger.minor(v[0]);
                filterMax=v[1].trim().isEmpty()?Long.MAX_VALUE:FinanceLedger.minor(v[1]);
                if(filterMin<0||filterMax<filterMin)throw new IllegalArgumentException("Перевір діапазон сум");
            }));
        button("Пошук операцій",()->form("Пошук",new String[]{"Категорія або нотатка"},v->query=v[0]));
        if(lastDeleted!=null)button("Відновити останнє видалення",()->{ledger.restore(lastDeleted);lastDeleted=null;show();});
        text("Операції · натисни для дій");
        long periodFrom=filterPeriod.equals("all")?0:FinanceAnalytics.begin(filterPeriod);
        long periodTo=filterPeriod.equals("all")?Long.MAX_VALUE:FinanceAnalytics.end(filterPeriod);
        String sql="SELECT e.id,e.amount,a.currency,e.category,a.name,e.happened,e.kind,e.group_id "+
            "FROM entries e JOIN accounts a ON a.id=e.account WHERE e.deleted=0 "+
            "AND (e.category LIKE ? OR e.note LIKE ? OR a.name LIKE ?) "+
            "AND (?='' OR e.kind=?) AND (?=-1 OR e.account=?) "+
            "AND e.happened>=? AND e.happened<? AND ABS(e.amount)>=? AND ABS(e.amount)<=? "+
            "ORDER BY e.happened DESC LIMIT 100";
        String[] args={"%"+query+"%","%"+query+"%","%"+query+"%",
            filterKind,filterKind,""+filterAccount,""+filterAccount,""+periodFrom,""+periodTo,
            ""+filterMin,""+filterMax};
        try(Cursor c=ledger.getReadableDatabase().rawQuery(sql,args)){
            while(c.moveToNext()){
                final String id=c.getString(0);String label=FinanceLedger.money(c.getLong(1))+" "+c.getString(2)+" · "+c.getString(3)+" · "+c.getString(4)+" · "+android.text.format.DateFormat.format("dd.MM.yyyy",c.getLong(5));
                final String kind=c.getString(6),group=c.getString(7);
                final String[] actions=group!=null&&group.startsWith("plan:")?
                    new String[]{"Скасувати проведений внесок"}:
                    kind.equals("expense")?new String[]{"Редагувати","Повернення покупки","Видалити"}:
                    kind.equals("income")?new String[]{"Редагувати","Видалити"}:new String[]{"Видалити"};
                button(label,()->new AlertDialog.Builder(this).setTitle(label).setItems(actions,(dialog,index)->{
                    String choice=actions[index];
                    if(choice.equals("Скасувати проведений внесок")){
                        new AlertDialog.Builder(this).setTitle("Повернути внесок?").setMessage("Запис буде скасовано, а прогрес і дата плану повернуться до стану перед цим внеском. Можна скасувати тільки останній внесок плану.")
                            .setNegativeButton("Ні",null).setPositiveButton("Підтвердити",(d,w)->{
                                try{ledger.undoSettlement(id);show();}catch(Exception e){error(e);}
                            }).show();
                    }
                    else if(choice.equals("Редагувати"))form("Редагувати",new String[]{"Нова сума","Категорія"},v->ledger.edit(id,v[0],v[1]));
                    else if(choice.equals("Повернення покупки"))form("Отримане повернення",new String[]{"Сума повернення"},v->ledger.refund(id,v[0]));
                    else new AlertDialog.Builder(this).setTitle("Видалити операцію?").setMessage(label+"\nПереказ буде скасовано з обох рахунків.").setNegativeButton("Ні",null).setPositiveButton("Видалити",(d,w)->{try{ledger.remove(id);lastDeleted=id;show();}catch(Exception ex){error(ex);}}).show();
                }).show());
            }
        }
    }
    private void toggleFinanceSetting(String key){
        boolean enabled=!ledger.setting(key,"false").equals("true");
        ledger.setSetting(key,Boolean.toString(enabled));
        Toast.makeText(this,enabled?"Увімкнено":"Вимкнено",Toast.LENGTH_SHORT).show();
    }
    private void newAccount(){
        formWithDefaults("Новий рахунок",new String[]{"Назва (готівка або картка)","Валюта (UAH)","Залишок на сьогодні (необов’язково)"},
            new String[]{"","UAH",""},values->ledger.account(values[0],
                values[1].trim().isEmpty()?"UAH":values[1].trim().toUpperCase(Locale.ROOT),values[2]));
    }
    private void planning(){
        String[] types={"budget","goal","payment","owed","owing"};
        String[] labels={"Бюджет на місяць","Ціль","Майбутній платіж","Мені винні","Я винен"};
        for(int n=0;n<types.length;n++){final String type=types[n],label=labels[n];button("Додати: "+label,()->form(label,new String[]{"Назва","Валюта (UAH)","Сума","Дата РРРР-ММ-ДД (необов'язково)","Категорія (для бюджету)"},v->ledger.plan(type,v[0],v[1].isEmpty()?"UAH":v[1].toUpperCase(Locale.ROOT),v[2],v[3],v[4])));}
        button("Додати тижневий бюджет",()->form("Тижневий бюджет",
            new String[]{"Назва","Валюта (UAH)","Сума","Категорія (необов'язково)"},
            v->ledger.plan("budget",v[0],v[1].trim().isEmpty()?"UAH":v[1].trim().toUpperCase(Locale.ROOT),
                v[2],"",v[3],"week")));
        text("Плани не змінюють фактичні залишки. Борги тут — початковий реєстр зобов’язань.");
        try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT type,name,currency,amount,due,category,id,progress,interval_months,budget_period,recurrence_unit,recurrence_every FROM plans ORDER BY due,name",null)){
            while(c.moveToNext()){
                String detail=c.getString(1)+" · "+FinanceLedger.money(c.getLong(3))+" "+c.getString(2)+" · "+c.getString(4);
                if(c.getString(0).equals("budget")){
                    long spent=FinanceAnalytics.spent(ledger,c.getString(2),c.getString(5),c.getString(9));
                    detail+="\n"+(c.getString(9).equals("week")?"Тиждень":"Місяць")+
                        " · витрачено: "+FinanceLedger.money(spent)+" · залишилось: "+
                        FinanceLedger.money(Math.subtractExact(c.getLong(3),spent));
                }
                String id=c.getString(6),type=c.getString(0);
                detail+="\nВиконано: "+FinanceLedger.money(c.getLong(7));
                if(type.equals("payment"))detail+=" · Повтор: "+c.getInt(11)+" "+c.getString(10);
                final String summary=detail;
                final String planName=c.getString(1),planAmount=FinanceLedger.money(c.getLong(3)),planDue=c.getString(4),planCategory=c.getString(5);
                ArrayList<String> possible=new ArrayList<>();
                if(!type.equals("budget"))possible.add(type.equals("goal")?"Відкласти на ціль":type.equals("payment")?"Підтвердити сплату":"Записати повернення боргу");
                if(type.equals("goal"))possible.add("Звільнити резерв");
                possible.add("Редагувати план");
                if(type.equals("payment"))possible.add("Періодичність");
                possible.add("Видалити план");
                String[] actions=possible.toArray(new String[0]);
                button(detail,()->new AlertDialog.Builder(this).setTitle(summary).setItems(actions,(dialog,index)->{
                    String action=actions[index];
                    if(action.equals("Відкласти на ціль")||action.equals("Підтвердити сплату")||action.equals("Записати повернення боргу")){
                        pickAccount(account->form("Сума",new String[]{"Сума"},v->ledger.settle(id,account,v[0])));
                    }else if(action.equals("Звільнити резерв")){
                        pickAccount(account->form("Звільнити резерв цілі",new String[]{"Сума"},v->ledger.releaseGoal(id,account,v[0])));
                    }else if(action.equals("Редагувати план")){
                        formWithDefaults("Редагувати план",new String[]{"Назва","Сума","Дата РРРР-ММ-ДД","Категорія"},
                            new String[]{planName,planAmount,planDue,planCategory},
                            v->ledger.editPlan(id,v[0],v[1],v[2],v[3]));
                    }else if(action.equals("Періодичність")){
                        new AlertDialog.Builder(this).setTitle("Повторювати платіж").setItems(
                            new String[]{"День","Тиждень","Місяць","Рік","Вимкнути"},
                            (d,w)->{
                                String unit=new String[]{"day","week","month","year","month"}[w];
                                if(w==4){ledger.recurrence(id,"month",0);show();}
                                else form("Повторювати кожні N одиниць",new String[]{"N"},
                                    v->ledger.recurrence(id,unit,Integer.parseInt(v[0])));
                            }).show();
                    }else if(action.equals("Видалити план")){
                        new AlertDialog.Builder(this).setMessage("Видалити план і звільнити резерв? Проведені операції залишаться.")
                            .setNegativeButton("Ні",null).setPositiveButton("Так",(d,w)->{try{ledger.deletePlan(id);show();}catch(Exception e){error(e);}}).show();
                    }
                }).show());
            }
        }
    }
    private void recoverPrevious(){
        if(backupRunning){showInfo("Завершується попередня операція з файлом.");return;}
        EditText field=new EditText(this);
        field.setHint("Пароль, введений під час останнього відновлення");
        field.setInputType(129);
        new AlertDialog.Builder(this).setTitle("Відновити попередній стан?")
            .setMessage("Поточний журнал буде замінено попередньою зашифрованою копією.")
            .setView(field).setNegativeButton("Скасувати",null)
            .setPositiveButton("Відновити",(d,w)->{
                char[] secret=field.getText().toString().toCharArray();
                backupRunning=true;
                new Thread(()->{
                    String error=null;
                    try{FinanceBackupFiles.undoRestore(getApplicationContext(),secret);}
                    catch(Exception e){error="Не вдалося відновити попередній стан. Перевір пароль.";}
                    finally{Arrays.fill(secret,'\0');}
                    final String issue=error;
                    runOnUiThread(()->{backupRunning=false;
                        if(isFinishing()||isDestroyed())return;
                        if(issue==null){Toast.makeText(this,"Попередній стан відновлено",Toast.LENGTH_LONG).show();show();}
                        else showInfo(issue);
                    });
                },"finance-recover").start();
            }).show();
    }
    private void backup(boolean restore){
        if(backupRunning){showInfo("Фінансовий файл ще обробляється.");return;}
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
            final char[] password=backupPassword;backupPassword=null;
            final android.net.Uri uri=data.getData();
            final boolean importing=request==73;
            backupRunning=true;
            Toast.makeText(this,"Обробка зашифрованої копії…",Toast.LENGTH_SHORT).show();
            new Thread(()->{
                String problem=null;
                try{
                    if(importing)FinanceBackupFiles.restore(getApplicationContext(),uri,password);
                    else FinanceBackupFiles.save(getApplicationContext(),uri,password);
                }catch(Exception e){
                    problem="Операція не виконана. Перевір пароль, формат і вільне місце. Дані не повинні змінитися при помилці.";
                }finally{Arrays.fill(password,'\0');}
                final String error=problem;
                runOnUiThread(()->{
                    backupRunning=false;
                    if(isFinishing()||isDestroyed())return;
                    if(error==null){
                        Toast.makeText(this,importing?"Дані відновлено; попередній стан збережено":"Копію збережено",Toast.LENGTH_LONG).show();
                        show();
                    }else showInfo(error);
                });
            },"finance-backup").start();
            return;
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
        formWithDefaults(title,hints,new String[hints.length],action);
    }
    private void formWithDefaults(String title,String[] hints,String[] initial,Values action){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);EditText[] fields=new EditText[hints.length];
        for(int i=0;i<hints.length;i++){fields[i]=new EditText(this);fields[i].setHint(hints[i]);if(i<initial.length&&initial[i]!=null)fields[i].setText(initial[i]);box.addView(fields[i]);}
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
