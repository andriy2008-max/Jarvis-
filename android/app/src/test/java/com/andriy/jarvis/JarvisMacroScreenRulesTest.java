package com.andriy.jarvis;

public final class JarvisMacroScreenRulesTest {
    private static int assertions;
    private static void yes(boolean c) {
        assertions++;
        if(!c)throw new AssertionError("Macro screen test "+assertions);
    }
    private static void reject(String input) {
        try {
            JarvisMacroScreenRules.parsePoint(input);
            throw new AssertionError("Accepted invalid coordinates: "+input);
        } catch(IllegalArgumentException expected) { assertions++; }
    }
    public static void main(String[] args) {
        JarvisMacroScreenRules.Point p=JarvisMacroScreenRules.parsePoint("45,62");
        yes(p.x==45 && p.y==62);
        p=JarvisMacroScreenRules.parsePoint(" 45.5, 62.25 ");
        yes(p.x==45.5 && p.y==62.25);
        yes("45.5,62.3".equals(JarvisMacroScreenRules.formatPoint(p.x,p.y)));
        for(String invalid : new String[]{null,"","0,50","50,0","2,2",
            "98,20","50,99","100,50","50,100","NaN,10",
            "50,Infinity","50;50","1e2,50","-4,50","4,50; rm -rf /"})
            reject(invalid);
        yes(JarvisMacroScreenRules.waitTimeoutMillis("1")==1000);
        yes(JarvisMacroScreenRules.waitTimeoutMillis("20")==20000);
        yes(JarvisMacroScreenRules.waitTimeoutMillis("60")==60000);
        for(String bad:new String[]{null,"","0","61","1,5","2.5","-1",
            "100","NaN","1;rm","01  "}) {
            try {
                JarvisMacroScreenRules.waitTimeoutMillis(bad);
                throw new AssertionError("Accepted invalid wait deadline: "+bad);
            } catch (IllegalArgumentException expected) { assertions++; }
        }
        yes(JarvisMacroScreenRules.secondsToMillis("2")==2000);
        yes(JarvisMacroScreenRules.secondsToMillis("1,5")==1500);
        yes(JarvisMacroScreenRules.secondsToMillis("0.1")==100);
        yes(JarvisMacroScreenRules.secondsToMillis("60")==60000);
        for (String invalid : new String[]{"0","-1","60.001","61","NaN",
            "1e3","2;rm","1,5,7","","100"}) {
            try {
                JarvisMacroScreenRules.secondsToMillis(invalid);
                throw new AssertionError("Accepted invalid pause: "+invalid);
            } catch (IllegalArgumentException expected) { assertions++; }
        }
        yes(JarvisMacroScreenRules.validText("Напиши Spotify"));
        yes(!JarvisMacroScreenRules.validText(""));
        yes(!JarvisMacroScreenRules.validText(null));
        yes(!JarvisMacroScreenRules.validText("\u0000"));
        yes(!JarvisMacroScreenRules.validText("x".repeat(1001)));
        yes(JarvisMacroScreenRules.matchesLabel("Пошук","Пошук"));
        yes(JarvisMacroScreenRules.matchesLabel(" пошук  ","ПОШУК"));
        yes(!JarvisMacroScreenRules.matchesLabel("Пошук","Пошук в інтернеті"));
        yes(!JarvisMacroScreenRules.matchesLabel("Надіслати","Надіслати заявку"));
        yes(!JarvisMacroScreenRules.matchesLabel("","Пошук"));
        yes(!JarvisMacroScreenRules.matchesLabel(null,"Пошук"));
        yes(JarvisMacroScreenRules.riskyLabel("Оплатити покупку"));
        yes(JarvisMacroScreenRules.riskyLabel("Send money"));
        yes(JarvisMacroScreenRules.riskyLabel("Видалити"));
        yes(!JarvisMacroScreenRules.riskyLabel("Пошук"));
        yes(!JarvisMacroScreenRules.riskyLabel("Відкрити Spotify"));
        System.out.println("PASS "+assertions+" macro screen rules");
    }
}
