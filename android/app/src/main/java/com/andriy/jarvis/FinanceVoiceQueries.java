package com.andriy.jarvis;

import android.database.Cursor;
import java.time.*;
import java.util.Locale;

/** Read-only finance answers. No AI estimates and no transfer/payment side effects. */
public final class FinanceVoiceQueries {
    private FinanceVoiceQueries(){}
    private static String normalize(String voice){
        if(voice==null||voice.length()>240)return "";
        String expanded=FinanceSlashCommands.expand(voice);
        if(expanded==null)return "";
        return expanded.toLowerCase(Locale.forLanguageTag("uk-UA")).trim()
            .replaceFirst("^(?:джарвіс|джарвис|jarvis)[,\\s:–—-]*","")
            .replaceAll("[?.!]+$","").trim();
    }
    public static boolean open(String voice){
        String s=normalize(voice);
        return s.matches("^(?:відкрий|покажи|відкрити|показати) (?:мої )?(?:фінанси|фінансовий менеджер)$");
    }
    public static boolean matches(String voice){
        String s=normalize(voice);
        if(s.isEmpty())return false;
        if(s.startsWith("скільки ")&&(s.contains("чайов")||s.contains("чаєв")))return true;
        if(s.matches("^(?:який|покажи|скажи|скільки|мій|мої|що з|скільки в мене|скільки у мене).*\\b(?:баланс|залишок|грошей|кошти)\\b.*"))return true;
        if(s.matches("^(?:скільки|покажи|які) .*\\b(?:витратив|витрачено|витрати|заробив|отримав|доходи|дохід)\\b.*"))return true;
        if(s.matches("^(?:які|покажи|що з|скільки).*\\b(?:платежі|платіж|нагадування|бюджет|бюджетом|бюджету)\\b.*"))return true;
        return s.equals("фінансовий звіт")||s.equals("звіт фінанси");
    }
    public static String answer(FinanceLedger ledger,String voice){
        String s=normalize(voice);
        if(!matches(voice))return null;
        if(s.contains("чайов")||s.contains("чаєв"))return tips(ledger,s);
        if(s.contains("платіж")||s.contains("платеж")||s.contains("нагадування")){
            String events=FinanceInsights.reminders(ledger);
            return events.isEmpty()?"Немає внесених найближчих платежів на 3 дні.":events;
        }
        if(s.contains("бюджет"))return FinanceAnalytics.budgets(ledger);
        if(s.equals("фінансовий звіт")||s.equals("звіт фінанси"))return FinanceAnalytics.report(ledger,"month");
        if(s.contains("витрат")||s.contains("витратив"))return period(ledger,s,false);
        if(s.contains("заробив")||s.contains("отримав")||s.contains("доход")||s.contains("дохід"))return period(ledger,s,true);
        return ledger.report();
    }
    private static String period(FinanceLedger ledger,String words,boolean income){
        LocalDate today=LocalDate.now();LocalDate first=today.withDayOfMonth(1);
        String label="цього місяця";
        if(words.contains("сьогодні")){first=today;label="сьогодні";}
        else if(words.contains("вчора")){first=today.minusDays(1);label="вчора";}
        else if(words.contains("тижд")){first=today.minusDays(today.getDayOfWeek().getValue()-1L);label="цього тижня";}
        long from=first.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long to=words.contains("вчора")?today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli():System.currentTimeMillis()+1L;
        StringBuilder result=new StringBuilder(income?"Доходи ":"Чисті витрати ").append(label).append(":\n");
        boolean found=false;
        try(Cursor c=ledger.getReadableDatabase().rawQuery(
            "SELECT a.currency,COALESCE(SUM(e.amount),0),COUNT(*) FROM entries e JOIN accounts a ON a.id=e.account "+
            "WHERE e.deleted=0 AND e.kind IN ("+(income?"'income'":"'expense','refund'")+") AND e.happened>=? AND e.happened<? GROUP BY a.currency",
            new String[]{""+from,""+to})){
            while(c.moveToNext()){
                found=true;
                long value=c.getLong(1);
                result.append(FinanceLedger.money(income?value:Math.negateExact(value))).append(" ").append(c.getString(0)).append(" · ").append(c.getLong(2)).append(" операцій\n");
            }
        }
        if(!found)result.append("Записів немає.");
        return result.toString();
    }
    private static String tips(FinanceLedger ledger,String words){
        LocalDate today=LocalDate.now();
        LocalDate first=words.contains("вчора")?today.minusDays(1):
            words.contains("тижд")?today.minusDays(today.getDayOfWeek().getValue()-1L):
            words.contains("місяц")?today.withDayOfMonth(1):today;
        long from=first.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long to=words.contains("вчора")?today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli():
            System.currentTimeMillis()+1L;
        StringBuilder out=new StringBuilder("Чайові за вибраний період:\n");boolean any=false;
        try(Cursor c=ledger.getReadableDatabase().rawQuery(
            "SELECT a.currency,SUM(e.amount) FROM entries e JOIN accounts a ON e.account=a.id "+
            "WHERE e.deleted=0 AND e.kind='income' AND e.category IN ('чай','Чайові') "+
            "AND e.happened>=? AND e.happened<? GROUP BY a.currency",
            new String[]{""+from,""+to})){
            while(c.moveToNext()){
                any=true;out.append(FinanceLedger.money(c.getLong(1))).append(" ").append(c.getString(0)).append("\n");
            }
        }
        return any?out.toString():"Записів чайових за цей період немає.";
    }
    private static String budgets(FinanceLedger ledger){
        long start=LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long now=System.currentTimeMillis()+1L;
        StringBuilder out=new StringBuilder("Місячні бюджети:\n");boolean any=false;
        try(Cursor budgets=ledger.getReadableDatabase().rawQuery("SELECT name,category,currency,amount FROM plans WHERE type='budget' ORDER BY name",null)){
            while(budgets.moveToNext()){
                any=true;long spent=0;
                try(Cursor c=ledger.getReadableDatabase().rawQuery(
                    "SELECT COALESCE(-SUM(e.amount),0) FROM entries e JOIN accounts a ON a.id=e.account "+
                    "WHERE e.deleted=0 AND e.kind IN ('expense','refund') AND a.currency=? "+
                    "AND (e.category=? OR ?='') AND e.happened>=? AND e.happened<?",
                    new String[]{budgets.getString(2),budgets.getString(1),budgets.getString(1),""+start,""+now})){
                    if(c.moveToFirst())spent=c.getLong(0);
                }
                out.append(budgets.getString(0)).append(": ").append(FinanceLedger.money(spent))
                    .append(" із ").append(FinanceLedger.money(budgets.getLong(3)))
                    .append(" ").append(budgets.getString(2));
                if(spent>budgets.getLong(3))out.append(" · перевищено на ").append(FinanceLedger.money(spent-budgets.getLong(3)));
                out.append("\n");
            }
        }
        return any?out.toString():"Бюджетів ще немає. Створи бюджет у розділі «План».";
    }
}
