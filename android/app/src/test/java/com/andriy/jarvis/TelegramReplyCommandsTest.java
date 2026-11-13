package com.andriy.jarvis;

public final class TelegramReplyCommandsTest {
    private static void yes(boolean v){if(!v)throw new AssertionError("Telegram reply parser");}
    public static void main(String[] args){
        TelegramReplyCommands.Request r=TelegramReplyCommands.parse(
            "Джарвіс, відповідай в телеграм Олена: Я буду через 10 хвилин.");
        yes(r!=null && r.kind==TelegramReplyCommands.Kind.DRAFT &&
            "Олена".equals(r.recipient) &&
            "Я буду через 10 хвилин.".equals(r.text));
        r=TelegramReplyCommands.parse("відповідай у телеграм: Зрозумів");
        yes(r!=null && r.recipient==null && r.text.equals("Зрозумів"));
        r=TelegramReplyCommands.parse(
            "Джарвіс, напиши в телеграм Олена: Привіт, як справи?");
        yes(r!=null && r.kind==TelegramReplyCommands.Kind.DRAFT &&
            "Олена".equals(r.recipient) &&
            "Привіт, як справи?".equals(r.text));
        r=TelegramReplyCommands.parse("напиши у телеграм Ігор: Буду через 5 хв.");
        yes(r!=null && "Ігор".equals(r.recipient));
        r=TelegramReplyCommands.parse("відповідай у телеграм Олена скажи Уже йду");
        yes(r!=null && "Олена".equals(r.recipient) && "Уже йду".equals(r.text));
        r=TelegramReplyCommands.parse(
            "Джарвіс, напиши в telegram Яна Привіт, це тест.");
        yes(r!=null && r.kind==TelegramReplyCommands.Kind.DRAFT_AUTO &&
            r.recipient==null && "Яна Привіт, це тест.".equals(r.text));
        yes("Привіт, це тест.".equals(
            TelegramReplyCommands.messageAfterContact(r.text,"Яна")));
        r=TelegramReplyCommands.parse(
            "Напиши в Telegram Яна Петрівна Буду через 10 хвилин");
        yes(r!=null && r.kind==TelegramReplyCommands.Kind.DRAFT_AUTO &&
            "Буду через 10 хвилин".equals(
                TelegramReplyCommands.messageAfterContact(r.text,"Яна Петрівна")));
        yes(TelegramReplyCommands.messageAfterContact(
            "Яна Петрівна Буду через 10 хвилин","Яна Сидоренко")==null);
        yes(TelegramReplyCommands.messageAfterContact(
            "Яна","Яна")==null);
        yes("Привіт".equals(TelegramReplyCommands.messageAfterContact(
            "Яна, Привіт","Яна")));
        yes(TelegramReplyCommands.parse("напиши в telegram")!=null &&
            TelegramReplyCommands.parse("напиши в telegram").kind==
                TelegramReplyCommands.Kind.HELP);
        yes(TelegramReplyCommands.parse("напиши Привіт") == null);
        yes(TelegramReplyCommands.parse("надішли в телеграм").kind==
            TelegramReplyCommands.Kind.SEND);
        yes(TelegramReplyCommands.parse("скасуй відповідь").kind==
            TelegramReplyCommands.Kind.CANCEL);
        yes(TelegramReplyCommands.parse("перевір телеграм").kind==
            TelegramReplyCommands.Kind.STATUS);
        yes(TelegramReplyCommands.parse("надішли гроші") == null);
        yes(TelegramReplyCommands.parse("відповідай у телеграм: ").kind==
            TelegramReplyCommands.Kind.HELP);
        yes(TelegramReplyCommands.parse("відповідай у телеграм: \nновий") == null);
        yes(TelegramReplyCommands.exactContact("Олена-К.", "олена к"));
        yes(!TelegramReplyCommands.exactContact("Олена","Олена С."));
        yes(!TelegramReplyCommands.exactContact("Олена","Олена з роботи"));
        for(String word:new String[]{"ок","ОК","окей","Окей!",
            "Джарвіс, окей"}) {
            yes(TelegramReplyCommands.dialogAction(word)==
                TelegramReplyCommands.DialogAction.CONFIRM);
            yes(TelegramReplyCommands.parse(word)==null);
        }
        for(String phrase:new String[]{"не так","Заново",
            "Джарвіс, не так!"}) {
            yes(TelegramReplyCommands.dialogAction(phrase)==
                TelegramReplyCommands.DialogAction.REWRITE_MESSAGE);
        }
        yes(TelegramReplyCommands.dialogAction("Не той контакт")==
            TelegramReplyCommands.DialogAction.CHANGE_CONTACT);
        yes(TelegramReplyCommands.dialogAction("Джарвіс, не той контакт.")==
            TelegramReplyCommands.DialogAction.CHANGE_CONTACT);
        yes(TelegramReplyCommands.dialogAction("Не надсилай")==
            TelegramReplyCommands.DialogAction.CANCEL);
        for(String phrase:new String[]{"окей, але не зараз","окей надішли гроші",
            "Окей, відкрий YouTube","не той контакт Олена",
            "заново, але завтра","не така","нічого","так"}) {
            yes(TelegramReplyCommands.dialogAction(phrase)==
                TelegramReplyCommands.DialogAction.NONE);
        }
        TelegramReplyCommands.Request corrected=
            TelegramReplyCommands.correctedContact("Яна Петрівна","Привіт, як справи?");
        yes(corrected!=null && corrected.kind==TelegramReplyCommands.Kind.DRAFT &&
            "Яна Петрівна".equals(corrected.recipient) &&
            "Привіт, як справи?".equals(corrected.text));
        yes(TelegramReplyCommands.correctedContact("","Привіт")==null);
        yes(TelegramReplyCommands.correctedContact("Яна","")==null);
        yes("Привіт, YouTube!".equals(TelegramReplyCommands.dictatedCorrection(
            "Джарвіс, Привіт, YouTube!")));
        yes(TelegramReplyCommands.dictatedCorrection(null)==null);
        yes(TelegramReplyCommands.dictatedCorrection("  ")==null);
        System.out.println("PASS TelegramReplyCommandsTest");
    }
}
