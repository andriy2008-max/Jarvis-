package com.andriy.jarvis;

import java.util.Locale;

/** Finite allowlisted slash aliases; missing data never mutates a ledger. */
public final class FinanceSlashCommands {
    private FinanceSlashCommands(){}
    public static String expand(String raw){
        if(raw==null)return null;
        String input=raw.trim().toLowerCase(Locale.forLanguageTag("uk-UA"));
        if(!input.startsWith("/"))return raw;
        String[] parts=input.split("\\s+",2);
        String argument=parts.length>1?parts[1].trim():"";
        switch(parts[0]){
            case "/витрата":case "/витрати":return argument.isEmpty()?null:"запиши витрату "+argument;
            case "/дохід":case "/доход":return argument.isEmpty()?null:"запиши дохід "+argument;
            case "/баланс":case "/рахунки":return "який баланс";
            case "/звіт":case "/категорії":case "/тренди":return "фінансовий звіт";
            case "/бюджет":case "/бюджети":return "що з бюджетом";
            case "/підписки":return "які найближчі платежі";
            case "/фінанси":return "відкрий фінанси";
            default:return null;
        }
    }
}
