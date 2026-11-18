package com.andriy.jarvis;

public final class JarvisWatchLocalCommandsTest {
    private static void yes(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args) {
        JarvisWatchLocalCommands.Request launch=
            JarvisWatchLocalCommands.parse("Джарвіс, відкрий Spotify на годиннику");
        yes(launch!=null && launch.opensApp());
        yes("spotify".equals(launch.app));
        yes(JarvisWatchLocalCommands.parse("Відкрий Telegram на Pixel")==null);
        yes(JarvisWatchLocalCommands.parse("Відкрий Telegram")==null);
        yes(JarvisWatchLocalCommands.parse("Натисни пошук на годиннику").screen.kind==
            ScreenControlCommands.Kind.CLICK);
        yes("пошук".equals(JarvisWatchLocalCommands.parse(
            "Натисни пошук на годиннику").screen.label));
        yes(JarvisWatchLocalCommands.parse("Прокрути вниз на годиннику").screen.kind==
            ScreenControlCommands.Kind.SCROLL_DOWN);
        yes(JarvisWatchLocalCommands.parse("Назад на годиннику").screen.kind==
            ScreenControlCommands.Kind.BACK);
        yes(JarvisWatchLocalCommands.parse("Напиши Привіт, друже на годиннику").screen.kind==
            ScreenControlCommands.Kind.TYPE_TEXT);

        JarvisWatchLocalCommands.Request song=JarvisWatchLocalCommands.parse(
            "Джарвіс, увімкни пісню Пачка сигарет на годиннику");
        yes(song!=null && song.playsSpotify() && !song.opensApp());
        yes(song.music==SpotifyMusicCommands.Kind.TRACK);
        yes("пачка сигарет".equals(song.musicTitle));
        JarvisWatchLocalCommands.Request playlist=JarvisWatchLocalCommands.parse(
            "Увімкни плейлист Мій реп на годиннику");
        yes(playlist!=null && playlist.playsSpotify());
        yes(playlist.music==SpotifyMusicCommands.Kind.PLAYLIST);
        yes("мій реп".equals(playlist.musicTitle));
        yes(JarvisWatchLocalCommands.parse(
            "Увімкни пісню Пачка сигарет")==null);
        yes(!JarvisWatchLocalCommands.targetsWatch(
            "Увімкни плейлист Мій реп"));
        yes(JarvisWatchLocalCommands.parse("Увімкни пісню на годиннику")==null);
        yes(JarvisWatchLocalCommands.parse("Натисни оплатити на годиннику")==null);
        yes(JarvisWatchLocalCommands.parse("Увімкни керування екраном на годиннику")==null);
        System.out.println("PASS JarvisWatchLocalCommandsTest");
    }
}
