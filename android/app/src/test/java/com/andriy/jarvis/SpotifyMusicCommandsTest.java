package com.andriy.jarvis;

public final class SpotifyMusicCommandsTest {
    private static void is(String voice, SpotifyMusicCommands.Kind kind) {
        if (SpotifyMusicCommands.parse(voice) != kind)
            throw new AssertionError("Spotify intent incorrect: " + voice);
    }
    public static void main(String[] args) {
        is("Увімкни музику",SpotifyMusicCommands.Kind.OPEN);
        is("включи музику",SpotifyMusicCommands.Kind.OPEN);
        is("увімкну музику",SpotifyMusicCommands.Kind.OPEN);
        is("Джарвіс, включи музику!",SpotifyMusicCommands.Kind.OPEN);
        is("Ввімкни Spotify",SpotifyMusicCommands.Kind.OPEN);
        is("Увімкни реп",SpotifyMusicCommands.Kind.RAP);
        is("Джарвіс увімкни реп",SpotifyMusicCommands.Kind.RAP);
        is("Включи rap",SpotifyMusicCommands.Kind.RAP);
        is("Включи українське",SpotifyMusicCommands.Kind.UKRAINIAN);
        is("Джарвіс, увімкни українську музику",SpotifyMusicCommands.Kind.UKRAINIAN);
        is("включи укр муз",SpotifyMusicCommands.Kind.UKRAINIAN);
        is(null,null);
        is("вимкни музику",null);
        is("постав музику на паузу",null);
        is("увімкни ліхтарик",null);
        is("включи spotify пошук",null);
        is("що таке реп",null);
        System.out.println("PASS SpotifyMusicCommandsTest");
    }
}
