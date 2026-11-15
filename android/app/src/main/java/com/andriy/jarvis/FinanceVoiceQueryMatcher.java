package com.andriy.jarvis;

import java.util.Locale;

/** Unicode-safe Ukrainian finance queries: explicit read-only phrases only. */
public final class FinanceVoiceQueryMatcher {
    private FinanceVoiceQueryMatcher(){}
    private static boolean any(String text,String... words){
        for(String word:words)if(text.contains(word))return true;
        return false;
    }
    public static boolean matches(String normalized){
        if(normalized==null)return false;
        String s=normalized.trim().toLowerCase(Locale.forLanguageTag("uk-UA"));
        if(s.isEmpty()||s.length()>240)return false;
        // Deliberately reject commands that could contain expenditure amounts.
        if(s.startsWith("запиши ")||s.startsWith("витрата ")||s.startsWith("додай ")
            ||s.startsWith("перекажи ")||s.startsWith("оплати "))return false;
        if(s.equals("фінансовий звіт")||s.equals("звіт фінанси")||s.equals("що з моїми фінансами"))return true;
        boolean question=any(s,"скільки","покажи","який","яка","мої ","мій ","що з","скажи",
            "які ","розкажи","фінансовий ","чистий ","подушка ","прибуток ");
        if(!question)return false;
        return any(s,"баланс","залиш","грошей","кошт","витрат","витратив","зароб",
            "дохід","доход","отримав","чайов","чаєв","платіж","платеж",
            "нагадув","бюджет","капітал","подушк","крипто","собіварт",
            "фінансовий звіт","фінансовий стан","змін","курс");
    }
}
