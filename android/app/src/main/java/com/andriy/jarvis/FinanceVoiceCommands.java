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
        public Entry(Kind kind,String amount,String category,String account) {
            this.kind=kind;this.amount=amount;this.category=category;this.account=account;
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
    private FinanceVoiceCommands(){}

    public static Entry parse(String voice) {
        if(voice==null||voice.length()>230)return null;
        String s=voice.toLowerCase(Locale.forLanguageTag("uk-UA")).trim()
            .replaceAll("[.!?]+$","");
        Matcher m=EXP.matcher(s);
        Kind kind=Kind.EXPENSE;
        if(!m.matches()) {
            m=INC.matcher(s);
            if(!m.matches())return null;
            kind=Kind.INCOME;
        }
        String amount=m.group(1).replace(',','.');
        java.math.BigDecimal number;
        try { number=new java.math.BigDecimal(amount); }
        catch(NumberFormatException bad){return null;}
        if(number.signum()<=0 || number.compareTo(new java.math.BigDecimal("999999"))>0)
            return null;
        amount=number.stripTrailingZeros().toPlainString();
        String descriptor=m.group(2).trim();
        String account=null;
        // Optional account suffix. Never interpret "з ... " elsewhere.
        final String[][] suffixes={
            {"(?:з|із|зі|на)\\s+готівк(?:и|а|у)\\s*$","готівка"},
            {"(?:з|із|зі|на)\\s+карт(?:и|а|у)\\s+моно\\s*$","карта моно"},
            {"(?:з|із|зі|на)\\s+заначк(?:и|а|у)\\s+гривн(?:і|я|ю|ень)\\s*$","заначка гривні"}
        };
        for(String[] suffix:suffixes) {
            Pattern p=Pattern.compile("(?:[,;]?\\s*)"+suffix[0]);
            Matcher match=p.matcher(descriptor);
            if(match.find()){
                account=suffix[1];descriptor=descriptor.substring(0,match.start()).trim();
                break;
            }
        }
        String category=category(kind,descriptor);
        return category==null?null:new Entry(kind,amount,category,account);
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
            case "харчування":case "їжу":case "їжа":case "їжі":
            case "продукти":case "продуктів":case "продукти харчування":
                return "Харчування";
            case "транспорт":case "проїзд":case "таксі":return "Транспорт";
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
