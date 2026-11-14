package com.andriy.jarvis;

/** Regression tests for voice commands sent from Galaxy Watch7. */
public final class JarvisWearImageCommandsTest {
    private static void eq(Object actual,Object expected) {
        if(actual==null?expected!=null:!actual.equals(expected))
            throw new AssertionError("Expected "+expected+", got "+actual);
    }
    public static void main(String[] args) {
        eq(JarvisWearImageCommands.prompt("Створи зображення кота в окулярах"),
            "кота в окулярах");
        eq(JarvisWearImageCommands.prompt("Джарвіс, створи фото гір"),
            "гір");
        eq(JarvisWearImageCommands.prompt("Згенеруй картинку міста"),
            "міста");
        eq(JarvisWearImageCommands.prompt("Намалюй сонячну систему"),
            "сонячну систему");
        eq(JarvisWearImageCommands.prompt("Jarvis, create image"),null);
        eq(JarvisWearImageCommands.prompt("Увімкни пісню Shape of You"),null);
        eq(JarvisWearImageCommands.prompt("Створи зображення"),"");
        eq(JarvisWearImageCommands.lastImage("Покажи останнє зображення"),true);
        eq(JarvisWearImageCommands.lastImage("Джарвіс, покажи останню картинку"),true);
        eq(JarvisWearImageCommands.lastImage("Покажи нотатки"),false);
        System.out.println("PASS JarvisWearImageCommandsTest");
    }
}
