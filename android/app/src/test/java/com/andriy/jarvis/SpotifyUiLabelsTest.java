package com.andriy.jarvis;

public final class SpotifyUiLabelsTest {
    private static void accepts(SpotifyUiLabels.Target target, String label) {
        if (!SpotifyUiLabels.matches(target,label))
            throw new AssertionError("Spotify UI label missing: " + target + " / " + label);
    }
    private static void rejects(SpotifyUiLabels.Target target,String label) {
        if (SpotifyUiLabels.matches(target,label))
            throw new AssertionError("Wrong Spotify UI target: " + target + " / " + label);
    }
    public static void main(String[] args) {
        accepts(SpotifyUiLabels.Target.LIBRARY,"Your Library");
        accepts(SpotifyUiLabels.Target.LIBRARY,"Моя бібліотека");
        accepts(SpotifyUiLabels.Target.LIBRARY,"Медіатека");
        accepts(SpotifyUiLabels.Target.LIKED_SONGS,"Liked Songs");
        accepts(SpotifyUiLabels.Target.LIKED_SONGS,"Пісні, що сподобалися");
        accepts(SpotifyUiLabels.Target.RAP_FILTER,"Реп");
        accepts(SpotifyUiLabels.Target.RAP_FILTER,"Rap");
        accepts(SpotifyUiLabels.Target.UKR_MUSIC,"Укр муз");
        accepts(SpotifyUiLabels.Target.UKR_MUSIC,"Укр. муз");
        accepts(SpotifyUiLabels.Target.SHUFFLE,"Shuffle off");
        accepts(SpotifyUiLabels.Target.SHUFFLE,"Перемішати");
        accepts(SpotifyUiLabels.Target.PLAY,"Play");
        accepts(SpotifyUiLabels.Target.PLAY,"Play playlist");
        accepts(SpotifyUiLabels.Target.PLAY,"Відтворити");
        rejects(SpotifyUiLabels.Target.PLAY,"Pause");
        rejects(SpotifyUiLabels.Target.PLAY,"Playlist");
        rejects(SpotifyUiLabels.Target.RAP_FILTER,"Репортаж");
        rejects(SpotifyUiLabels.Target.UKR_MUSIC,"Українські виконавці");
        rejects(SpotifyUiLabels.Target.LIKED_SONGS,"Remove liked songs");
        if (!SpotifyUiLabels.shuffleOn("Disable shuffle",false,false))
            throw new AssertionError("On-state not recognized");
        if (SpotifyUiLabels.shuffleOn("Enable shuffle",false,false))
            throw new AssertionError("Off-state misread");
        if (!SpotifyUiLabels.shuffleOn("Shuffle",true,false))
            throw new AssertionError("Selected Shuffle not recognized");
        rejects(SpotifyUiLabels.Target.SHUFFLE,"Play shuffle");
        rejects(SpotifyUiLabels.Target.SHUFFLE,"Play in shuffle order");
        rejects(SpotifyUiLabels.Target.PLAY,"Play shuffle");
        accepts(SpotifyUiLabels.Target.SHUFFLE,"Enable Smart Shuffle");
        accepts(SpotifyUiLabels.Target.SHUFFLE,"Вимкнути перемішування");
        if (!SpotifyUiLabels.shuffleRequest("стрілочки") ||
            !SpotifyUiLabels.shuffleRequest("перехрещені стрілки") ||
            SpotifyUiLabels.shuffleRequest("стрілка назад")) throw new AssertionError("arrow intent");
        if (!SpotifyUiLabels.toolbarPair(205,110,265,158,302,70,428,196,1f))
            throw new AssertionError("reference toolbar pair");
        // Screenshot coordinates: green download arrow on far LEFT x~48,
        // correct crossed arrows x~530, green playlist Play x~610.
        if (!SpotifyUiLabels.playlistPlayAt(573,330,650,410,
            0,0,684,1500,2.6f) ||
            !SpotifyUiLabels.toolbarPair(505,345,553,391,
                573,330,650,410,2.6f))
            throw new AssertionError("RIGHT shuffle beside large Play");
        if (SpotifyUiLabels.toolbarPair(27,350,68,391,
            573,330,650,410,2.6f))
            throw new AssertionError("LEFT download must never be shuffle");
        if (SpotifyUiLabels.playlistPlayAt(573,1320,650,1400,
            0,0,684,1500,2.6f))
            throw new AssertionError("bottom mini player not playlist Play");
        if (SpotifyUiLabels.toolbarPair(205,710,265,758,302,70,428,196,1f) ||
            SpotifyUiLabels.toolbarPair(205,110,265,158,302,110,322,130,1f) ||
            SpotifyUiLabels.toolbarPair(305,110,365,158,102,70,228,196,1f))
            throw new AssertionError("wrong row, tiny track Play or wrong side accepted");
        System.out.println("PASS SpotifyUiLabelsTest");
    }
}
