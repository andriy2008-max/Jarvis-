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
        JarvisWatchLocalCommands.Request naturalSong=
            JarvisWatchLocalCommands.spotifyOnWatch(
                "Джарвіс, увімкни пісню Пачка сигарет");
        yes(naturalSong!=null && naturalSong.playsSpotify());
        yes(naturalSong.music==SpotifyMusicCommands.Kind.TRACK);
        yes(JarvisWatchLocalCommands.spotifyOnWatch(
            "Включи плейлист Вечірній")==null ? false : true);
        yes(JarvisWatchLocalCommands.spotifyOnWatch(
            "Натисни оплатити")==null);
        JarvisWatchLocalCommands.Request rap=
            JarvisWatchLocalCommands.spotifyOnWatch("Увімкни реп");
        yes(rap!=null && rap.playsSpotify());
        yes(rap.music==SpotifyMusicCommands.Kind.RAP);
        yes("реп".equals(rap.musicTitle));
        JarvisWatchLocalCommands.Request ukrainian=
            JarvisWatchLocalCommands.spotifyOnWatch("Включи українське");
        yes(ukrainian!=null && ukrainian.playsSpotify());
        yes(ukrainian.music==SpotifyMusicCommands.Kind.UKRAINIAN);
        yes("українські пісні".equals(ukrainian.musicTitle));

        // Open Spotify on WATCH without the wake word or watch suffix.
        for(String speech:new String[]{
            "Відкрий Spotify",
            "Відкрий Спотіфай",
            "Відкрити Спотіфай",
            "Відкрити Спотіфай на годинку",
            "Відкрий Spotify на годиннику",
            "Дімон, відкрий Spotify",
            "Дімон, відкрий Spotify на годиннику",
            "Запусти Spotify",
            "Увімкни спотіфай",
            "Включи музику"
        }) {
            JarvisWatchLocalCommands.Request opened=
                JarvisWatchLocalCommands.spotifyOnWatch(speech);
            yes(opened!=null && opened.opensApp() &&
                "spotify".equals(opened.app));
        }
        JarvisWatchLocalCommands.Request naturalPlaylist=
            JarvisWatchLocalCommands.spotifyOnWatch(
                "Увімкни плейлист Мій реп");
        yes(naturalPlaylist!=null && naturalPlaylist.playsSpotify());
        yes(JarvisWatchLocalCommands.spotifyOnWatch(
            "Увімкни пісню Ніч яка місячна на годиннику").playsSpotify());
        yes(JarvisWatchLocalCommands.spotifyRelatedOnWatch(
            "Зупини Spotify"));
        yes(!JarvisWatchLocalCommands.spotifyRelatedOnWatch(
            "Відкрий Telegram"));
        yes(!JarvisWatchLocalCommands.spotifyRelatedOnWatch(
            "Хто створив Spotify?"));
        yes(JarvisWatchLocalCommands.spotifyOnWatch(
            "Відкрий Telegram")==null);
        yes(JarvisWatchLocalCommands.spotifyOnWatch(
            "Дімон, де я")==null);

        yes(JarvisWatchLocalCommands.parse("Натисни оплатити на годиннику")==null);
        yes(JarvisWatchLocalCommands.parse("Увімкни керування екраном на годиннику")==null);
        JarvisWatchLocalCommands.Request location=
            JarvisWatchLocalCommands.mapsOnWatch("Де я");
        yes(location!=null && location.usesMaps() && location.maps.whereAmI);
        JarvisWatchLocalCommands.Request route=
            JarvisWatchLocalCommands.mapsOnWatch(
                "Джарвіс, знайди дорогу до Apartel Skhidnytsya");
        yes(route!=null && route.usesMaps());
        yes("Apartel Skhidnytsya".equals(route.maps.destination));
        JarvisWatchLocalCommands.Request explicitRoute=
            JarvisWatchLocalCommands.parse(
                "Знайди дорогу до Стебник, вулиця Грушевського 14а на годиннику");
        yes(explicitRoute!=null && explicitRoute.usesMaps());
        yes("Стебник, вулиця Грушевського 14а".equals(
            explicitRoute.maps.destination));
        yes(JarvisWatchLocalCommands.parse("Де я на годиннику").usesMaps());
        yes(JarvisWatchLocalCommands.parse(
            "Знайди дорогу до на годиннику")==null);
        yes(JarvisWatchLocalCommands.mapsOnWatch(
            "Знайди дорогу до Львів на годиннику")==null);
        yes(JarvisWatchLocalCommands.mapsOnWatch(
            "Відкрий Bolt")==null);
        System.out.println("PASS JarvisWatchLocalCommandsTest");
    }
}
