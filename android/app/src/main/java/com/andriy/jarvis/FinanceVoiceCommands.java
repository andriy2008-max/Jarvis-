package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative voice parser: one user-requested local finance draft only.
 * Never treats a transfer, bank payment, or unspecific amount as a transaction.
 */
public final class FinanceVoiceCommands {
    public enum Kind { EXPENSE, INCOME }
    public static final class Entry {
        public final Kind kind;
        public final String amount; // canonical positive decimal with dot
        public final String category;
        public final String account; // null means selected account in app
        public final int daysAgo; // 0=today, 1=yesterday, 2=day before yesterday
        public final String note; // trailing words of the short finance command
        public Entry(Kind kind,String amount,String category,String account) {
            this(kind,amount,category,account,0,null);
        }
        private Entry(Kind kind,String amount,String category,String account,int daysAgo) {
            this(kind,amount,category,account,daysAgo,null);
        }
        private Entry(Kind kind,String amount,String category,String account,int daysAgo,String note) {
            this.kind=kind;this.amount=amount;this.category=category;
            this.account=account;this.daysAgo=daysAgo;this.note=note;
        }
        /** Keypad chars, without extra zeros or a hidden negative sign. */
        public String keypad() {return amount.replace('.',',');}
    }
    private static final String PREFIX=
        "^(?:(?:джарвіс|джарвис|jarvis)[\\s,:—-]*)?"+
        "(?:запиши|додай|занеси|зафіксуй)\\s+";
    private static final Pattern EXP=Pattern.compile(
        PREFIX+"(?:витрату|витрати)\\s+([0-9]{1,7}(?:[,.][0-9]{1,2})?)"+
        "\\s*(?:грн\\.?|грив(?:ня|ні|ень|ню))?(?:\\s+(?:на|за|категорія|категорію))?\\s+(.+)$",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern INC=Pattern.compile(
        PREFIX+"(?:дохід|доход|надходження)\\s+([0-9]{1,7}(?:[,.][0-9]{1,2})?)"+
        "\\s*(?:грн\\.?|грив(?:ня|ні|ень|ню))?(?:\\s+(?:від|за|категорія|категорію))?\\s+(.+)$",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    // Short form: "витратив/потратив/заробив [sum] [account] [one-word category] [memo...]".
    // A malformed recognized financial command is NEVER an instruction for the AI.
    // "портатив" was a transcription mistake and is intentionally unsupported.
    private static final Pattern SHORT=Pattern.compile(
        "^(витратив|потратив|заробив)\\s+([0-9]{1,7}(?:[,.][0-9]{1,2})?)" +
        "(?:\\s*(?:грн\\.?|грив(?:ня|ні|ень|ню)))?\\s+(.+)$",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final String[][] SHORT_ACCOUNTS={
        {"карта моно","карта моно"},{"карти моно","карта моно"},
        {"картка моно","карта моно"},{"картки моно","карта моно"},
        {"заначка гривні","заначка гривні"},{"заначки гривні","заначка гривні"},
        {"готівка","готівка"},{"готівки","готівка"},
        {"монобанк","монобанк"},{"моно","карта моно"},
        {"заначка","заначка гривні"},{"заначки","заначка гривні"}
    };

    public static boolean shortFinanceIntent(String words) {
        String s=CommandPatterns.normalize(words);
        return s.matches("^(?:витратив|потратив|заробив)(?:\\s+.*)?$");
    }

    private static Entry shortEntry(String speech,int daysAgo) {
        Matcher m=SHORT.matcher(speech);
        if(!m.matches())return null;
        Kind kind=m.group(1).equals("заробив") ? Kind.INCOME : Kind.EXPENSE;
        String amount;
        try {
            java.math.BigDecimal value=new java.math.BigDecimal(m.group(2).replace(',','.'));
            if(value.signum()<=0 || value.compareTo(new java.math.BigDecimal("999999"))>0)
                return null;
            amount=value.stripTrailingZeros().toPlainString();
        } catch(NumberFormatException error) { return null; }
        String tail=m.group(3).replaceAll("\\s+"," ").trim();
        for(String[] hint:SHORT_ACCOUNTS) {
            if(!tail.startsWith(hint[0]+" "))continue;
            String remainder=tail.substring(hint[0].length()).trim();
            if(remainder.isEmpty())return null;
            String[] words=remainder.split(" ",2);
            String selected=category(kind,words[0]);
            if(selected==null)return null; // never infer income from expense
            String memo=words.length==2 ? words[1].trim() : null;
            if(memo!=null && (memo.length()>100 || memo.isEmpty() ||
                memo.chars().anyMatch(Character::isISOControl)))return null;
            return new Entry(kind,amount,selected,hint[1],daysAgo,memo);
        }
        return null; // never silently substitute a default account
    }

    private FinanceVoiceCommands(){}

    public static Entry parse(String voice) {
        if(voice==null||voice.length()>230)return null;
        String expanded=FinanceSlashCommands.expand(voice);
        if(expanded==null)return null;
        String s=FinanceNumberWords.normalize(expanded.toLowerCase(Locale.forLanguageTag("uk-UA")).trim())
            .replaceAll("[.!?]+$","");
        int daysAgo=s.endsWith(" позавчора")?2:s.endsWith(" вчора")?1:0;
        s=s.replaceFirst("\\s+(?:позавчора|вчора|сьогодні)$","").trim();
        String bare=CommandPatterns.normalize(s);
        if(shortFinanceIntent(bare)) {
            Entry quick=shortEntry(bare,daysAgo);
            if(quick!=null)return quick;
            // Version 0.1.33 also accepted old natural phrases such as
            // "витратив 150 на каву позавчора". Preserve that flow;
            // "потратив" and "заробив" remain strict short formats.
            if(!bare.startsWith("витратив "))return null;
        }
        Matcher m=EXP.matcher(s);
        Kind kind=Kind.EXPENSE;
        if(!m.matches()) {
            m=INC.matcher(s);
            if(!m.matches()){
                Entry fallback=natural(s);
                return fallback==null?null:new Entry(fallback.kind,fallback.amount,fallback.category,fallback.account,daysAgo);
            }
            kind=Kind.INCOME;
        }
        String amount=m.group(1).replace(',','.');
        java.math.BigDecimal number;
        try { number=new java.math.BigDecimal(amount); }
        catch(NumberFormatException bad){return null;}
        if(number.signum()<=0 || number.compareTo(new java.math.BigDecimal("999999"))>0)
            return null;
        amount=number.stripTrailingZeros().toPlainString();
        AccountHint hint=accountHint(m.group(2).trim());
        String descriptor=hint.description;
        String account=hint.account;
        String category=category(kind,descriptor);
        return category==null?null:new Entry(kind,amount,category,account,daysAgo);
    }

    /** Explicit finance trigger prevents ambiguous taxi/song commands from recording money. */
    private static Entry natural(String speech){
        String s=speech.replaceFirst("^(?:джарвіс|jarvis)[ ,:]*", "");
        boolean income=s.matches("^(?:(?:запиши|додай)\\s+)?(?:дохід|чайові|зарплата|отримав)(?:\\s|:).*" );
        if(!income&&!s.matches("^(?:(?:запиши|додай)\\s+)?(?:витрата|витрату|витратив|витратила)(?:\\s|:).*"))return null;
        if(s.matches(".*(?:вчора|завтра|позавчора|долар|євро|usd|eur|борг|позик).*"))return null;
        AccountHint hint=accountHint(s);
        String account=hint.account;
        s=hint.description;
        java.util.regex.Matcher number=Pattern.compile("(?<![0-9])([0-9]+(?:[,.][0-9]{1,2})?)(?![0-9])").matcher(s);
        if(!number.find())return null;
        String amount=number.group(1).replace(',','.');int begin=number.start(),end=number.end();if(number.find())return null;
        try{
            java.math.BigDecimal value=new java.math.BigDecimal(amount);
            if(value.signum()<=0||value.compareTo(new java.math.BigDecimal("999999"))>0)return null;
        }catch(RuntimeException e){return null;}
        String descriptor=(s.substring(0,begin)+" "+s.substring(end)).replaceFirst("^(?:(?:запиши|додай)\\s+)?(?:витрата|витрату|витратив|витратила|дохід|отримав)[: ]*", "").replaceAll("\\b(?:грн|гривень|гривні)\\b", "").trim();
        descriptor=descriptor.replaceFirst("^(?:на|за|від)\\s+","").trim();
        if(descriptor.isEmpty())return null;
        String cat=category(income?Kind.INCOME:Kind.EXPENSE,descriptor);
        if(cat==null&&descriptor.equals("кава"))cat="Їжа → Кафе";
        if(cat==null)return null;
        return new Entry(income?Kind.INCOME:Kind.EXPENSE,amount,cat,account);
    }

    private static final class AccountHint {
        final String description,account;
        AccountHint(String description,String account){this.description=description;this.account=account;}
    }
    /** Extract account only from a clearly marked suffix, never from category words. */
    private static AccountHint accountHint(String value){
        String s=value.trim();
        if(s.endsWith("готівкою"))return new AccountHint(s.substring(0,s.length()-"готівкою".length()).trim(),"готівка");
        final String[][] known={
            {"(?:з|із|зі|на)\\s+готівк(?:и|а|у)","готівка"},
            {"(?:з|із|зі|на)\\s+карт(?:и|а|у)\\s+моно","карта моно"},
            {"(?:з|із|зі|на)\\s+монобанк(?:у|а)?","монобанк"},
            {"(?:з|із|зі|на)\\s+заначк(?:и|а|у)\\s+гривн(?:і|я|ю|ень)","заначка гривні"}
        };
        for(String[] option:known){
            Matcher m=Pattern.compile("(?:[,;]?\\s+)"+option[0]+"\\s*$").matcher(s);
            if(m.find())return new AccountHint(s.substring(0,m.start()).trim(),option[1]);
        }
        Matcher other=Pattern.compile("(?:[,;]?\\s+)(?:з|із|зі|на)\\s+рахунк(?:у|а)\\s+([\\p{L}\\p{N} _-]{2,45})\\s*$").matcher(s);
        if(other.find())return new AccountHint(s.substring(0,other.start()).trim(),other.group(1).trim());
        return new AccountHint(s,null);
    }

    private static String category(Kind kind,String value){
        String s=value.replaceAll("\\s+"," ").trim();
        if(kind==Kind.INCOME){
            switch(s) {
                case "зарплата":case "зарплату":case "зп":case "зарплати":return "Зарплата";
                case "чай":case "чайові":case "чаєві":case "чайових":return "чай";
                case "борг":case "боргу":case "повернення боргу":return "борг";
                case "підробіток":case "підробітку":return "Підробіток";
                case "бонус":case "бонуси":return "Бонус";
                case "інвестиції":case "інвестицій":return "Інвестиції";
                case "крипта":case "крипту":return "крипта";
                case "інші":case "інше":return "Інші";
                default:return null;
            }
        }
        switch(s){
            case "кава":case "каву":case "кафе":return "Їжа → Кафе";
            case "харчування":case "їжу":case "їжа":case "їжі":
            case "продукти":case "продуктів":case "продукти харчування":
                return "Харчування";
            case "транспорт":case "проїзд":case "таксі":return "Транспорт";
            case "заправка":case "заправку":case "бензин":case "пальне":return "Транспорт → Пальне";
            case "комуналка":case "комунальні":case "комуналку":return "Комунальні";
            case "інтернет":case "інтернету":return "Зв’язок";
            case "розваги":case "розваг":return "Розваги";
            case "підписки":case "підписку":return "підписки";
            case "шопінг":case "покупки":case "покупку":return "Шопінг";
            case "телефон":case "зв'язок":return "Телефон";
            case "освіта":case "освіту":return "Освіта";
            case "краса":case "красу":return "Краса";
            case "спорт":case "спорту":return "Спорт";
            case "спілкування":return "Спілкування";
            case "одяг":case "одягу":return "Одяг";
            case "автомобіль":case "авто":return "Автомобіль";
            case "здоров'я":case "здоровʼя":case "ліки":return "Здоров'я";
            case "подорожі":case "подорож":return "Подорожі";
            case "тварини":case "тварин":return "Тварини";
            case "ремонти":case "ремонт":return "Ремонти";
            case "житло":case "оренду":return "Житло";
            case "меблі":return "Меблі";
            case "подарунки":case "подарунок":return "Подарунки";
            case "пожертви":case "донат":case "донати":return "Пожертви";
            case "закуски":case "закуску":return "Закуски";
            case "діти":case "дітей":return "Діти";
            case "овочі":return "Овочі";
            case "електроніка":case "електроніку":return "Електроніка";
            case "крипта":case "крипту":return "крипта";
            case "алкоголь":return "Алкоголь";
            case "сигарети":return "Сигарети";
            case "лотерея":return "Лотерея";
            default:return null;
        }
    }

    public static boolean cancel(String speech){
        String s=CommandPatterns.normalize(speech);
        return s.equals("скасуй витрату")||
            s.equals("скасуй фінанси")||s.equals("скасуй запис витрати");
    }
}
