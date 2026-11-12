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
        System.out.println("PASS TelegramReplyCommandsTest");
    }
}
