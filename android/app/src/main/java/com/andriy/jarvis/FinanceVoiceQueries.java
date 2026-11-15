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
        Period selected=periodFor(words);
        String label=selected.label;long from=selected.from,to=selected.to;
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
        Period selected=periodFor(words);
        long from=selected.from,to=selected.to;
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
    private static final class Period {
        final long from,to;final String label;
        Period(LocalDate first,LocalDate end,String title){
            ZoneId zone=ZoneId.systemDefault();
            from=first.atStartOfDay(zone).toInstant().toEpochMilli();
            to=end==null?System.currentTimeMillis()+1L:end.atStartOfDay(zone).toInstant().toEpochMilli();
            label=title;
        }
    }
    private static Period periodFor(String words){
        LocalDate today=LocalDate.now();
        if(words.contains("позавчора"))return new Period(today.minusDays(2),today.minusDays(1),"позавчора");
        if(words.contains("вчора"))return new Period(today.minusDays(1),today,"вчора");
        if(words.contains("минул")&&words.contains("місяц"))
            return new Period(today.withDayOfMonth(1).minusMonths(1),today.withDayOfMonth(1),"минулого місяця");
        if(words.contains("минул")&&words.contains("тижд")){
            LocalDate monday=today.minusDays(today.getDayOfWeek().getValue()-1L);
            return new Period(monday.minusWeeks(1),monday,"минулого тижня");
        }
        if(words.contains("сьогодні"))return new Period(today,null,"сьогодні");
        if(words.contains("тижд"))
            return new Period(today.minusDays(today.getDayOfWeek().getValue()-1L),null,"цього тижня");
        if(words.contains("місяц"))return new Period(today.withDayOfMonth(1),null,"цього місяця");
        return new Period(today,null,"сьогодні");
    }
}
