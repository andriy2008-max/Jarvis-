package com.andriy.jarvis;

import java.util.*;

/** Conservative Ukrainian integer amount words, evaluated only by explicit finance parsers. */
public final class FinanceNumberWords {
    private FinanceNumberWords(){}
    private static final Map<String,Integer> VALUES=new HashMap<>();
    static{
        String[][] rows={
            {"один","1"},{"одна","1"},{"одне","1"},{"два","2"},{"дві","2"},
            {"три","3"},{"чотири","4"},{"п'ять","5"},{"пʼять","5"},{"пять","5"},
            {"шість","6"},{"сім","7"},{"вісім","8"},{"дев'ять","9"},{"девʼять","9"},
            {"десять","10"},{"одинадцять","11"},{"дванадцять","12"},{"тринадцять","13"},
            {"чотирнадцять","14"},{"п'ятнадцять","15"},{"пʼятнадцять","15"},
            {"шістнадцять","16"},{"сімнадцять","17"},{"вісімнадцять","18"},{"дев'ятнадцять","19"},
            {"двадцять","20"},{"тридцять","30"},{"сорок","40"},{"п'ятдесят","50"},
            {"пʼятдесят","50"},{"шістдесят","60"},{"сімдесят","70"},{"вісімдесят","80"},
            {"дев'яносто","90"},{"девʼяносто","90"},
            {"сто","100"},{"двісті","200"},{"триста","300"},{"чотириста","400"},
            {"п'ятсот","500"},{"пʼятсот","500"},{"шістсот","600"},{"сімсот","700"},
            {"вісімсот","800"},{"дев'ятсот","900"},{"девʼятсот","900"},
        };
        for(String[] entry:rows)VALUES.put(entry[0],Integer.parseInt(entry[1]));
    }
    private static boolean isNumber(String token){
        return VALUES.containsKey(token)||token.equals("тисяча")||token.equals("тисячі")||
            token.equals("тисяч")||token.equals("півтори")||token.equals("півтора");
    }
    private static String parse(List<String> words){
        if(words.size()==0)return null;
        int thousands=0,partial=0;boolean sawThousand=false,half=false;
        for(String item:words){
            if(item.equals("півтори")||item.equals("півтора")){
                if(words.size()!=2||!(words.get(1).equals("тисячі")||words.get(1).equals("тисяч")))
                    return null;
                half=true;break;
            }
            if(item.startsWith("тисяч")){
                if(sawThousand)return null;
                sawThousand=true;thousands=(partial==0?1:partial)*1000;partial=0;
            }else{
                Integer n=VALUES.get(item);if(n==null)return null;
                partial+=n;
            }
        }
        int value=half?1500:thousands+partial;
        return value>0&&value<=999999?Integer.toString(value):null;
    }
    /** Replaces complete runs of number words only; leaves all other speech unchanged. */
    public static String normalize(String input){
        if(input==null||input.length()>300)return input;
        String[] words=input.split("\\s+");
        StringBuilder out=new StringBuilder();
        for(int i=0;i<words.length;){
            String clean=words[i].replaceAll("^[,;:]|[,;:]$","");
            if(isNumber(clean)){
                int j=i;List<String> group=new ArrayList<>();
                while(j<words.length){
                    String word=words[j].replaceAll("^[,;:]|[,;:]$","");
                    if(!isNumber(word))break;
                    group.add(word);j++;
                }
                String number=parse(group);
                if(number!=null){
                    if(out.length()>0)out.append(' ');
                    out.append(number);
                    i=j;continue;
                }
            }
            if(out.length()>0)out.append(' ');
            out.append(words[i++]);
        }
        return out.toString();
    }
}
