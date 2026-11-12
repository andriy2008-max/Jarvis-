package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict Ukrainian personal-Telegram reply commands.
 * A draft is NEVER itself a send authorization: a separate "Надішли..."
 * command is always necessary. These are replies to currently active
 * Telegram notifications, NOT arbitrary chats or Telegram Bot API messages.
 */
public final class TelegramReplyCommands {
    public enum Kind { DRAFT, SEND, CANCEL, STATUS }
    public static final class Request {
        public final Kind kind;
        public final String recipient; // null: exactly one reply-capable chat
        public final String text;      // draft body, never normalized
        private Request(Kind k,String r,String t){kind=k;recipient=r;text=t;}
    }
    private static final Pattern REPLY=Pattern.compile(
        "(?iu)^(?:відповідай|відповісти|дай\\s+відповідь|відправ\\s+відповідь)\\s+"+
        "(?:(?:на\\s+повідомлення\\s+)?(?:у|в|через)\\s+)?"+
        "(?:телеграм(?:і|і)?|telegram|тг)"+
        "(?:\\s+([^:]{1,80}?))?\\s*:\\s*(.+)$"
    );
    private static final Pattern REPLY_SAY=Pattern.compile(
        "(?iu)^(?:відповідай|відповісти)\\s+(?:у|в|через)\\s+"+
        "(?:телеграм(?:і)?|telegram|тг)\\s+(.{1,80}?)\\s+скажи\\s+(.+)$"
    );

    private TelegramReplyCommands(){}

    public static Request parse(String raw) {
        if(raw==null)return null;
        String input=raw.trim()
            .replaceFirst("(?iu)^(?:джарвіс|джарвис|jarvis)[,\\s:—-]*","");
        String shortForm=CommandPatterns.normalize(input);
        switch(shortForm){
            case "надішли в телеграм":case "надішли у телеграм":
            case "відправ у телеграм":case "надішли відповідь":
            case "надішли повідомлення в телеграм":
                return new Request(Kind.SEND,null,null);
            case "скасуй відповідь":case "скасуй повідомлення":
            case "скасуй телеграм":case "не надсилай":
                return new Request(Kind.CANCEL,null,null);
            case "статус телеграм":case "перевір телеграм":
                return new Request(Kind.STATUS,null,null);
            default: break;
        }
        Matcher m=REPLY.matcher(input);
        if(!m.matches())m=REPLY_SAY.matcher(input);
        if(!m.matches())return null;
        String contact=m.group(1)==null?null:m.group(1).trim();
        if(contact!=null && (contact.isEmpty() ||
            contact.equalsIgnoreCase("повідомлення")))contact=null;
        String body=m.group(2).trim();
        if(contact!=null && (contact.length()>75 ||
            contact.contains("\n")||contact.contains("\r")))return null;
        if(body.isEmpty()||body.length()>1000||body.contains("\n")||body.contains("\r"))
            return null;
        return new Request(Kind.DRAFT,contact,body);
    }

    public static String nameKey(String name){
        if(name==null)return "";
        return name.toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("[^\\p{L}\\p{N}]+"," ")
            .replaceAll("\\s+"," ").trim();
    }
    public static boolean exactContact(String asked,String actual){
        String a=nameKey(asked),b=nameKey(actual);
        return !a.isEmpty() && a.equals(b);
    }
}
