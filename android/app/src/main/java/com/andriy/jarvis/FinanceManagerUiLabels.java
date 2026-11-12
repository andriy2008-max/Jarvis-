package com.andriy.jarvis;

import java.util.Locale;

/** Finite, screenshot-grounded layout map for Money Manager
 * (com.freeman.moneymanager). Coordinates are NEVER a substitute for checking
 * application identity and the visible screen before dispatching a gesture.
 *
 * Reference: user screenshots 684 x 1536 (portrait).
 */
public final class FinanceManagerUiLabels {
    private FinanceManagerUiLabels(){}

    public static String normalize(String input){
        return input==null?"":input.toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("[^\\p{L}\\p{N}]+"," ").replaceAll("\\s+"," ").trim();
    }

    public static boolean exact(String input,String expected){
        return normalize(input).equals(normalize(expected));
    }
    public static boolean amountEquals(String expected,String actual){
        if(expected==null||actual==null)return false;
        String cleaned=actual.replaceAll("[\\s\\u00a0\\u202f]","")
            .replace(',','.').replace("₴","").replace("грн","");
        try {
            java.math.BigDecimal got=new java.math.BigDecimal(cleaned);
            java.math.BigDecimal want=new java.math.BigDecimal(expected);
            return want.compareTo(got)==0;
        }catch(NumberFormatException error){return false;}
    }

    public static boolean screenshotShape(int w,int h){
        return w>=360 && h>=w*2.05f && h<=w*2.5f;
    }

    public static int[] plusPoint(int w,int h){
        return screenshotShape(w,h)?pt(w,h,342,1387):null;
    }
    public static int[] typePoint(FinanceVoiceCommands.Kind kind,int w,int h){
        return screenshotShape(w,h)?pt(w,h,
            kind==FinanceVoiceCommands.Kind.EXPENSE?136:342,203):null;
    }
    public static int[] categoryPoint(FinanceVoiceCommands.Kind kind,
                                      String category,int w,int h){
        if(!screenshotShape(w,h)||category==null)return null;
        String[] cats=kind==FinanceVoiceCommands.Kind.EXPENSE?
            new String[]{"крипта","підписки","Шопінг","Харчування",
                "Телефон","Розваги","Освіта","Краса",
                "Спорт","Спілкування","Транспорт","Одяг",
                "Автомобіль","Алкоголь","Сигарети","Електроніка",
                "Подорожі","Здоров'я","Тварини","Ремонти",
                "Житло","Меблі","Подарунки","Пожертви",
                "Лотерея","Закуски","Діти","Овочі"}:
            new String[]{"крипта","чай","борг","Зарплата",
                "Інвестиції","Підробіток","Бонус","Інші"};
        int index=-1;
        for(int i=0;i<cats.length;i++)if(exact(category,cats[i])){index=i;break;}
        if(index<0)return null;
        return pt(w,h,98+(index%4)*162,322+(index/4)*155);
    }
    public static int[] accountPoint(String account,int w,int h){
        if(!screenshotShape(w,h))return null;
        if(exact(account,"готівка"))return pt(w,h,260,911);
        if(exact(account,"карта моно"))return pt(w,h,260,1020);
        if(exact(account,"заначка гривні"))return pt(w,h,260,1135);
        return null;
    }
    public static int[] keypadPoint(char digit,int w,int h){
        if(!screenshotShape(w,h))return null;
        switch(digit){
            case '7':return pt(w,h,95,1139);
            case '8':return pt(w,h,254,1139);
            case '9':return pt(w,h,415,1139);
            case '4':return pt(w,h,95,1220);
            case '5':return pt(w,h,254,1220);
            case '6':return pt(w,h,415,1220);
            case '1':return pt(w,h,95,1306);
            case '2':return pt(w,h,254,1306);
            case '3':return pt(w,h,415,1306);
            case '0':return pt(w,h,254,1394);
            case ',':return pt(w,h,95,1394);
            default:return null;
        }
    }
    public static int[] confirmPoint(int w,int h){
        return screenshotShape(w,h)?pt(w,h,581,1397):null;
    }
    private static int[] pt(int width,int height,int x,int y){
        return new int[]{Math.round(width*x/684f),Math.round(height*y/1536f)};
    }
}
