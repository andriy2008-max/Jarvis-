package com.andriy.jarvis;

public final class JarvisMicrophoneChoiceTest {
    private static void yes(boolean check) {
        if(!check)throw new AssertionError("Microphone selection");
    }

    public static void main(String[] args) {
        yes(JarvisMicrophoneChoice.fromStored(null)==JarvisMicrophoneChoice.Mode.AUTO);
        yes(JarvisMicrophoneChoice.fromStored("")==JarvisMicrophoneChoice.Mode.AUTO);
        yes(JarvisMicrophoneChoice.fromStored("auto")==JarvisMicrophoneChoice.Mode.AUTO);
        yes(JarvisMicrophoneChoice.fromStored("phone")==JarvisMicrophoneChoice.Mode.PHONE);
        yes(JarvisMicrophoneChoice.fromStored("headset")==JarvisMicrophoneChoice.Mode.HEADSET);
        yes(JarvisMicrophoneChoice.fromStored("headset; command")==JarvisMicrophoneChoice.Mode.AUTO);
        for(JarvisMicrophoneChoice.Mode mode: JarvisMicrophoneChoice.Mode.values()) {
            yes(JarvisMicrophoneChoice.fromStored(JarvisMicrophoneChoice.stored(mode))==mode);
            yes(!JarvisMicrophoneChoice.label(mode).isEmpty());
        }
        yes("auto".equals(JarvisMicrophoneChoice.stored(null)));
        System.out.println("PASS JarvisMicrophoneChoiceTest");
    }
}
