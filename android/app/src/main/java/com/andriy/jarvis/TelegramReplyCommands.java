package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit personal Telegram voice commands for TDLib/MTProto.
 * A draft is NEVER itself a send authorization: a separate "Надішли..."
 * command is always necessary. No bots, notification tricks or auto-replies.
 */
public final class TelegramReplyCommands {
    public enum Kind { DRAFT, DRAFT_AUTO, SEND, CANCEL, STATUS, HELP }
    /** Short replies are recognized ONLY while a Telegram draft dialogue is active. */
    public enum DialogAction { NONE, CONFIRM, REWRITE_MESSAGE, CHANGE_CONTACT, CANCEL }
    public static final class Request {
        public final Kind kind;
        public final String recipient; // mandatory for MTProto direct messages
        public final String text;      // draft body, never normalized
        private Request(Kind k,String r,String t){kind=k;recipient=r;text=t;}
    }
    private static final String COMMAND_PREFIX =
        "(?iu)^(?:відповідай|відповісти|дай\\s+відповідь|відправ\\s+відповідь|напиши|написати|відправ|надішли)\\s+"+
        "(?:(?:на\\s+повідомлення\\s+)?(?:у|в|через)\\s+)?"+
        "(?:телеграм(?:і)?|telegram|тг)";
    private static final Pattern REPLY=Pattern.compile(
        COMMAND_PREFIX+"(?:\\s+([^:]{1,80}?))?\\s*:\\s*(.+)$"
    );
    private static final Pattern REPLY_SAY=Pattern.compile(
        COMMAND_PREFIX+"\\s+(.{1,80}?)\\s+(?:скажи|текст|повідомлення)\\s+(.+)$"
    );
    private static final Pattern TELEGRAM_INTENT=Pattern.compile(
        COMMAND_PREFIX+"(?:\\s|$|[:;,]).*$"
    );
    private static final Pattern REPLY_FREE=Pattern.compile(
        COMMAND_PREFIX+"\\s+(.+)$"
    );

    private TelegramReplyCommands(){}

    /** The word "ок" never becomes a global Telegram command. */
    public static DialogAction dialogAction(String raw) {
        if (raw == null) return DialogAction.NONE;
        String phrase=CommandPatterns.normalize(raw).replaceAll("\\s+"," ").trim()
            .replaceAll("[,:;]+$", "").trim();
        switch (phrase) {
            case "ок":
            case "окей":
                return DialogAction.CONFIRM;
            case "не так":
            case "заново":
                return DialogAction.REWRITE_MESSAGE;
            case "не той контакт":
                return DialogAction.CHANGE_CONTACT;
            case "скасуй відповідь":
            case "скасуй повідомлення":
            case "скасуй телеграм":
            case "не надсилай":
                return DialogAction.CANCEL;
            default: return DialogAction.NONE;
        }
    }

    /** Preserve original dictated text. A corrected contact is never inferred by AI. */
    public static Request correctedContact(String recipient,String unchangedText) {
        if (recipient==null || unchangedText==null)return null;
        String name=recipient.trim(),body=unchangedText.trim();
        if (name.length()<1 || name.length()>75 || body.isEmpty() ||
            body.length()>1000 || name.contains("\n") || name.contains("\r") ||
            body.contains("\n") || body.contains("\r")) return null;
        return new Request(Kind.DRAFT,name,body);
    }

    public static String dictatedCorrection(String raw) {
        if (raw==null)return null;
        String text=raw.trim()
            .replaceFirst("(?iu)^(?:джарвіс|джарвис|jarvis)[,\\s:—-]*","");
        if(text.isEmpty() || text.length()>1000 || text.contains("\n") ||
            text.contains("\r"))return null;
        return text.trim();
    }

    public static Request parse(String raw) {
        if(raw==null || raw.contains("\n") || raw.contains("\r"))return null;
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
            case "статус телеграм api":case "перевір telegram api":
                return new Request(Kind.STATUS,null,null);
            default: break;
        }
        Matcher m=REPLY.matcher(input);
        if(!m.matches())m=REPLY_SAY.matcher(input);
        if(!m.matches()){
            Matcher free=REPLY_FREE.matcher(input);
            if(free.matches()) {
                String rest=free.group(1).trim();
                if(rest.length()<=1100 && !rest.isEmpty())
                    // TDLib resolves the longest exact chat-title prefix;
                    // the remaining words become the body. No guessing by AI.
                    return new Request(Kind.DRAFT_AUTO,null,rest);
            }
            // Never pass an attempted Telegram command to generic text insertion.
            return TELEGRAM_INTENT.matcher(input).matches()
                ? new Request(Kind.HELP,null,null) : null;
        }
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

    /** Only a known Telegram chat title may delimit the voice command.
     * Return null when that title is not the exact prefix, or no body follows.
     * Preserve the dictated message case and punctuation. */
    public static String messageAfterContact(String speech,String chatTitle) {
        if(speech==null || chatTitle==null || chatTitle.isEmpty())return null;
        String[] words=speech.trim().split("\\s+");
        if(words.length<2)return null;
        StringBuilder title=new StringBuilder();
        for(int i=0;i<words.length-1;i++){
            if(i>0)title.append(' ');
            title.append(words[i]);
            if(exactContact(title.toString(),chatTitle)){
                StringBuilder body=new StringBuilder();
                for(int j=i+1;j<words.length;j++){
                    if(body.length()>0)body.append(' ');
                    body.append(words[j]);
                }
                String text=body.toString().trim();
                return !text.isEmpty() && text.length()<=1000 ? text : null;
            }
            if(title.length()>100)return null;
        }
        return null;
    }
}
