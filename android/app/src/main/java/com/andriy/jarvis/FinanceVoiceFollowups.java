package com.andriy.jarvis;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A correction must name both the old and the new exact amount. */
public final class FinanceVoiceFollowups {
    public static final class Correction {
        public final String oldAmount,newAmount;
        private Correction(String oldValue,String newValue){oldAmount=oldValue;newAmount=newValue;}
    }
    private static final Pattern CORRECTION=Pattern.compile(
        "^(?:(?:джарвіс|джарвис|jarvis)[\\s,:—-]*)?не\\s+([0-9]{1,7}(?:[,.][0-9]{1,2})?)"+
        "\\s*(?:грн)?\\s*[,;]?\\s+а\\s+([0-9]{1,7}(?:[,.][0-9]{1,2})?)\\s*(?:грн)?[.!?]*$",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private FinanceVoiceFollowups(){}
    public static Correction parse(String voice){
        if(voice==null||voice.length()>140)return null;
        Matcher m=CORRECTION.matcher(voice.trim().toLowerCase(Locale.forLanguageTag("uk-UA")));
        if(!m.matches())return null;
        try{
            BigDecimal oldValue=new BigDecimal(m.group(1).replace(',','.'));
            BigDecimal newValue=new BigDecimal(m.group(2).replace(',','.'));
            if(oldValue.signum()<=0||newValue.signum()<=0||
                oldValue.compareTo(new BigDecimal("999999"))>0||
                newValue.compareTo(new BigDecimal("999999"))>0||
                oldValue.compareTo(newValue)==0)return null;
            return new Correction(oldValue.stripTrailingZeros().toPlainString(),newValue.stripTrailingZeros().toPlainString());
        }catch(RuntimeException e){return null;}
    }
}
