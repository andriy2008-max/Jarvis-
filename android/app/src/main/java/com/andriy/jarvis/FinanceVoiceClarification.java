package com.andriy.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local two-turn Ukrainian clarification; never guesses a missing amount or category. */
public final class FinanceVoiceClarification {
    private static final String PREFS="finance_voice_clarifications";
    private static final long TTL=120_000L;
    private FinanceVoiceClarification(){}
    public static final class Step {
        public final String command,reply;
        Step(String command,String reply){this.command=command;this.reply=reply;}
    }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static String key(String source){return "pending:"+(source.length()>100?source.substring(0,100):source);}
    private static String clean(String voice){
        if(voice==null||voice.length()>220)return "";
        return FinanceNumberWords.normalize(voice.toLowerCase(Locale.forLanguageTag("uk-UA")).trim())
            .replaceFirst("^(?:джарвіс|джарвис|jarvis)[,\\s:-]*","").replaceAll("[!?.,]+$","").trim();
    }
    public static boolean maybeStart(String voice){
        String s=clean(voice);
        return s.matches("^(?:(?:запиши|додай)\\s+)?(?:витрата|витрату|дохід|чайові)\\s+.{1,80}$")
            &&FinanceVoiceCommands.parse(s)==null;
    }
    public static boolean maybeReply(String voice){
        String s=clean(voice);
        if(normNumber(s)!=null||s.equals("скасуй")||s.equals("відміна")||s.equals("не треба"))return true;
        return s.length()<70&&(FinanceVoiceCommands.parse("витрата 1 "+s)!=null||
            FinanceVoiceCommands.parse("дохід 1 "+s)!=null);
    }
    public static synchronized boolean pending(Context c,String source){
        String raw=prefs(c).getString(key(source),"");
        if(raw.isEmpty())return false;
        try{
            JSONObject state=new JSONObject(FinanceWatchVault.reveal(raw));
            if(System.currentTimeMillis()-state.getLong("at")<=TTL)return true;
        }catch(Exception ignored){}
        prefs(c).edit().remove(key(source)).commit();
        return false;
    }
    public static synchronized void clear(Context c,String source){
        prefs(c).edit().remove(key(source)).commit();
    }
    private static String normNumber(String s){
        String number=s.trim().replaceFirst("\\s*(?:грн|гривень)$","").replace(',','.');
        if(!number.matches("\\d{1,6}(?:\\.\\d{1,2})?"))return null;
        try{
            java.math.BigDecimal amount=new java.math.BigDecimal(number);
            if(amount.signum()>0&&amount.compareTo(new java.math.BigDecimal("999999"))<=0)return number;
        }catch(Exception ignored){}
        return null;
    }
    public static synchronized Step process(Context c,String source,String utterance){
        String normalized=clean(utterance);
        if(normalized.isEmpty())return null;
        if(pending(c,source)){
            if(FinanceVoiceCommands.parse(normalized)!=null){
                clear(c,source);
                return null;
            }
            if(normalized.equals("скасуй")||normalized.equals("відміна")||normalized.equals("не треба")){
                clear(c,source);return new Step(null,"Незавершений фінансовий запис скасовано. Гроші не списано.");
            }
            try{
                JSONObject state=new JSONObject(FinanceWatchVault.reveal(prefs(c).getString(key(source),"")));
                String type=state.getString("type"),known=state.getString("known");
                String whole;
                if(type.equals("amount")){
                    String amount=normNumber(normalized);
                    if(amount==null)return new Step(null,"Назви точну суму в гривнях, наприклад 85. Або скажи «скасуй».");
                    whole=known+" "+amount;
                }else{
                    if(normalized.length()>60||normalized.matches(".*\\d.*"))
                        return new Step(null,"Назви категорію: наприклад кава або таксі. Або скажи «скасуй».");
                    whole=known+" "+normalized;
                }
                if(FinanceVoiceCommands.parse(whole)==null)
                    return new Step(null,"Категорію не впізнано. Скажи, наприклад, «кава» або «таксі».");
                return new Step(whole,null);
            }catch(Exception e){clear(c,source);return new Step(null,"Незавершений запис пошкоджено; повтори команду повністю.");}
        }
        if(!maybeStart(normalized))return null;
        String text=normalized.replaceFirst("^(?:(?:запиши|додай)\\s+)?","");
        String base=text.startsWith("дохід")||text.startsWith("чайові")?"дохід":"витрата";
        String description=text.replaceFirst("^(?:витрата|витрату|дохід|чайові)\\s+","").trim();
        if(description.isEmpty())return null;
        String type,known,message;
        if(normNumber(description)!=null){
            type="category";known=base+" "+description;
            message="На що записати "+description+" гривень? Назви категорію: кава, продукти, таксі тощо.";
        }else if(FinanceVoiceCommands.parse(base+" "+description+" 1")!=null){
            type="amount";known=base+" "+description;
            message="Яка точна сума для «"+description+"»? Скажи суму в гривнях.";
        }else return new Step(null,"Категорію поки не впізнав. Скажи повністю: «витрата кава 85».");
        JSONObject state=new JSONObject();
        try{state.put("at",System.currentTimeMillis()).put("type",type).put("known",known);}
        catch(Exception e){return new Step(null,"Не вдалося зберегти уточнення.");}
        if(!prefs(c).edit().putString(key(source),FinanceWatchVault.protect(state.toString())).commit())
            return new Step(null,"Не вдалося захистити запит на уточнення.");
        return new Step(null,message);
    }
}
