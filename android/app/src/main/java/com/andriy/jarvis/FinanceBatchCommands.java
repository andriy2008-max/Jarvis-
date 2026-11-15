package com.andriy.jarvis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded explicit multi-expense parser. Reject the whole batch on one bad item. */
public final class FinanceBatchCommands {
    private FinanceBatchCommands(){}
    public static boolean looksLikeBatch(String speech){
        if(speech==null)return false;
        return speech.trim().toLowerCase(Locale.forLanguageTag("uk-UA"))
            .matches("^(?:(?:джарвіс|джарвис|jarvis)[,\\s:-]*)?(?:запиши|додай|занеси)\\s+витрати\\s+.*");
    }
    public static List<FinanceVoiceCommands.Entry> parse(String speech){
        if(speech==null||speech.length()>350||!looksLikeBatch(speech))return null;
        String s=speech.trim().toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceFirst("^(?:(?:джарвіс|джарвис|jarvis)[,\\s:-]*)?(?:запиши|додай|занеси)\\s+витрати\\s+","")
            .replaceAll("[.!?]+$","").trim();
        String suffix="";
        if(s.endsWith(" позавчора")){suffix=" позавчора";s=s.substring(0,s.length()-9).trim();}
        else if(s.endsWith(" вчора")){suffix=" вчора";s=s.substring(0,s.length()-6).trim();}
        else if(s.endsWith(" сьогодні")){suffix=" сьогодні";s=s.substring(0,s.length()-8).trim();}
        String[] segments=s.split("\\s+і\\s+");
        if(segments.length<2||segments.length>5)return null;
        List<FinanceVoiceCommands.Entry> result=new ArrayList<>();
        for(String segment:segments){
            FinanceVoiceCommands.Entry item=FinanceVoiceCommands.parse("витрата "+segment.trim()+suffix);
            if(item==null||item.kind!=FinanceVoiceCommands.Kind.EXPENSE)return null;
            result.add(item);
        }
        return result;
    }
}
