package com.andriy.jarvis;
public final class FinanceVoiceQueryMatcherTest {
    private static void check(boolean expected,String voice){
        boolean actual=FinanceVoiceQueryMatcher.matches(voice);
        if(actual!=expected)throw new AssertionError(voice+" expected "+expected+" got "+actual);
    }
    public static void main(String[] args){
        check(true,"Який баланс");
        check(true,"скільки я витратив цього місяця");
        check(true,"що з бюджетом");
        check(true,"покажи чистий капітал");
        check(true,"покажи крипто собівартість");
        check(true,"розкажи про фінансовий стан");
        check(true,"покажи чайові за зміну");
        check(true,"фінансовий звіт");
        check(false,"включи пісню");
        check(false,"запиши витрату кава 85");
        check(false,"перекажи 1000 мамі");
        check(false,"оплати рахунок");
        check(false,"");
        System.out.println("PASS FinanceVoiceQueryMatcherTest");
    }
}
