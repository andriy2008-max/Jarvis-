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
        yes(JarvisMacroScreenRules.validText("Напиши Spotify"));
        yes(!JarvisMacroScreenRules.validText(""));
        yes(!JarvisMacroScreenRules.validText(null));
        yes(!JarvisMacroScreenRules.validText("\u0000"));
        yes(!JarvisMacroScreenRules.validText("x".repeat(1001)));
        yes(JarvisMacroScreenRules.riskyLabel("Оплатити покупку"));
        yes(JarvisMacroScreenRules.riskyLabel("Send money"));
        yes(JarvisMacroScreenRules.riskyLabel("Видалити"));
        yes(!JarvisMacroScreenRules.riskyLabel("Пошук"));
        yes(!JarvisMacroScreenRules.riskyLabel("Відкрити Spotify"));
        System.out.println("PASS "+assertions+" macro screen rules");
    }
}
