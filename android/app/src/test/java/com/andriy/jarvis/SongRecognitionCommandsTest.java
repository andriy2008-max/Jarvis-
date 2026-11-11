package com.andriy.jarvis;
public final class SongRecognitionCommandsTest {
    public static void main(String[] args) {
        for (String s : new String[]{"Що це за пісня?", "Джарвіс, розпізнай пісню!", "зашазам", "що зараз грає"})
            if (!SongRecognitionCommands.matches(s)) throw new AssertionError("Missed recognition: " + s);
        for (String s : new String[]{null, "", "увімкни музику", "знайди пісню Океан Ельзи", "не розпізнавай пісню", "напиши що це за пісня"})
            if (SongRecognitionCommands.matches(s)) throw new AssertionError("Unexpected recognition: " + s);
        System.out.println("PASS SongRecognitionCommandsTest");
    }
}
