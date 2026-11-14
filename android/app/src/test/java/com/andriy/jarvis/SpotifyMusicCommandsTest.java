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
        is("включи плейлист Рок",SpotifyMusicCommands.Kind.PLAYLIST);
        is("увімкни пісню Океан",SpotifyMusicCommands.Kind.TRACK);
        is("Джарвіс, включи трек Мій трек",SpotifyMusicCommands.Kind.TRACK);
        is("запусти song Skyfall",SpotifyMusicCommands.Kind.TRACK);
        is("Включи песню Метель",SpotifyMusicCommands.Kind.TRACK);
        is("Джарвіс, поставь песню Всё как у людей",SpotifyMusicCommands.Kind.TRACK);
        is("Проиграй песню Снег",SpotifyMusicCommands.Kind.TRACK);
        is("Воспроизведи трек Белые розы",SpotifyMusicCommands.Kind.TRACK);
        is("Сыграй песню Трава у дома",SpotifyMusicCommands.Kind.TRACK);
        is("Увімкни пісню Плакала",SpotifyMusicCommands.Kind.TRACK);
        is("Увімкни пісню Весна прийде",SpotifyMusicCommands.Kind.TRACK);
        is("Увімкни пісню Shape of You",SpotifyMusicCommands.Kind.TRACK);
        is("Увімкни пісню Billie Jean",SpotifyMusicCommands.Kind.TRACK);
        is("Увімкни пісню Bohemian Rhapsody",SpotifyMusicCommands.Kind.TRACK);
        is("Увімкни пісню Метель",SpotifyMusicCommands.Kind.TRACK);
        is("Увімкни пісню Всё как у людей",SpotifyMusicCommands.Kind.TRACK);
        is("Джарвіс, увімкни пісню Never Gonna Give You Up",
            SpotifyMusicCommands.Kind.TRACK);
        if (!"shape of you".equals(SpotifyMusicCommands.namedTrack(
                 "Увімкни пісню Shape of You")) ||
            !"всё как у людей".equals(SpotifyMusicCommands.namedTrack(
                 "Увімкни пісню Всё как у людей")) ||
            !"billie jean".equals(SpotifyMusicCommands.namedTrack(
                 "Джарвіс, увімкни пісню Billie Jean")))
            throw new AssertionError("Ukrainian command must preserve Russian/English song titles");
        is("Включи музыку",SpotifyMusicCommands.Kind.OPEN);
        is("Проиграй украинскую музыку",SpotifyMusicCommands.Kind.UKRAINIAN);
        if (!"всё как у людей".equals(SpotifyMusicCommands.namedTrack(
             "Поставь песню Всё как у людей")) ||
            !"весна прийде".equals(SpotifyMusicCommands.namedTrack(
             "Увімкни пісню Весна прийде")))
            throw new AssertionError("Preserve Ukrainian and Russian dictated titles");
        is("увімкни пісню",null);
        is("виключи пісню Океан",null);
        if(!"океан".equals(SpotifyMusicCommands.namedTrack("увімкни пісню Океан")))
            throw new AssertionError("saved song alias parser");
        is("включи плейлист Рок без перемішування",SpotifyMusicCommands.Kind.PLAYLIST);
        is("виключи плейлист Рок",null);
        is("виключи музику",null);
        is("Джарвіс, увімкни плейлист Рок",SpotifyMusicCommands.Kind.PLAYLIST);
        is("увімкни плейліст Укр муз",SpotifyMusicCommands.Kind.PLAYLIST);
        is("Запусти playlist Workout 2026",SpotifyMusicCommands.Kind.PLAYLIST);
        is("Джарвіс, увімкни плейлист Рок без перемішування",SpotifyMusicCommands.Kind.PLAYLIST);
        SpotifyMusicCommands.NamedPlaylist named = SpotifyMusicCommands.namedPlaylist(
            "Джарвіс, увімкни плейлист Рок");
        if (named==null || !named.name.equals("рок") || !named.shuffle)
            throw new AssertionError("shuffle playlist name");
        named = SpotifyMusicCommands.namedPlaylist(
            "Джарвіс, увімкни плейлист Тренування без перемішування");
        if (named==null || !named.name.equals("тренування") || named.shuffle)
            throw new AssertionError("playlist ordered playback");
        named = SpotifyMusicCommands.namedPlaylist("ввімкни плейлист «Мої хіти»");
        if (named==null || !named.name.equals("мої хіти"))
            throw new AssertionError("quoted playlist");
        if (SpotifyMusicCommands.namedPlaylist("увімкни плейлист")!=null ||
            SpotifyMusicCommands.namedPlaylist("увімкни плейлист без перемішування")!=null ||
            SpotifyMusicCommands.namedPlaylist("відкрий плейлист Рок")!=null)
            throw new AssertionError("invalid playlist command");
        is(null,null);
        is("вимкни музику",null);
        is("постав музику на паузу",null);
        is("увімкни ліхтарик",null);
        is("включи spotify пошук",null);
        is("що таке реп",null);
        System.out.println("PASS SpotifyMusicCommandsTest");
    }
}
