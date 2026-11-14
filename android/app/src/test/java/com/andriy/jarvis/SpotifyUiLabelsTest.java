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
        if (!SpotifyUiLabels.namedPlaylist("Рок","рок") ||
            !SpotifyUiLabels.namedPlaylist("Мої хіти","Плейлист: Мої хіти") ||
            !SpotifyUiLabels.namedPlaylist("Workout 2026","Workout 2026 • Playlist") ||
            SpotifyUiLabels.namedPlaylist("Рок","Рок-н-рол") ||
            SpotifyUiLabels.namedPlaylist("Рок","Мій плейлист Рок") ||
            SpotifyUiLabels.namedPlaylist("Рок","Рок 2026"))
            throw new AssertionError("Exact playlist selection");
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
        // A real green Play button should be recognised even without
        // a Compose Accessibility label; Shuffle/download are not Play.
        if(!SpotifyUiLabels.unlabelledPlaylistPlayCircle(573,330,650,410,
            0,0,684,1536,2.6f) ||
           !SpotifyUiLabels.unlabelledPlaylistPlayCircle(904,530,1025,652,
            0,0,1080,2424,3.0f) ||
           SpotifyUiLabels.unlabelledPlaylistPlayCircle(505,345,553,391,
            0,0,684,1536,2.6f) ||
           SpotifyUiLabels.unlabelledPlaylistPlayCircle(30,350,110,430,
            0,0,684,1536,2.6f) ||
           SpotifyUiLabels.unlabelledPlaylistPlayCircle(573,1350,650,1430,
            0,0,684,1536,2.6f))
            throw new AssertionError("only the large upper-right Spotify Play circle");
        int[] play=SpotifyUiLabels.screenshotPlaylistPlayPoint(684,1536);
        if(play==null || Math.abs(play[0]-616)>1 ||
           Math.abs(play[1]-369)>1 ||
           !SpotifyUiLabels.safePlaylistPlayPoint(play[0],play[1],0,0,684,1536))
            throw new AssertionError("large green playlist Play screenshot geometry");
        if(SpotifyUiLabels.screenshotPlaylistPlayPoint(1536,684)!=null ||
           SpotifyUiLabels.screenshotPlaylistPlayPoint(684,900)!=null ||
           SpotifyUiLabels.safePlaylistPlayPoint(70,369,0,0,684,1536) ||
           SpotifyUiLabels.safePlaylistPlayPoint(616,1400,0,0,684,1536))
            throw new AssertionError("do not tap a different Spotify icon or mini-player");
        if(!SpotifyUiLabels.headerRegion(230,300,250,60,0,0,684,1536) ||
           SpotifyUiLabels.headerRegion(230,1320,250,60,0,0,684,1536) ||
           SpotifyUiLabels.headerRegion(620,300,80,60,0,0,684,1536))
            throw new AssertionError("playlist header must be in upper left, never mini-player");
        if(!SpotifyUiLabels.largePause("Pause") ||
           !SpotifyUiLabels.largePause("Пауза") ||
           SpotifyUiLabels.largePause("Play") ||
           SpotifyUiLabels.largePause("Remove playlist"))
            throw new AssertionError("avoid stopping an already playing playlist");
        int[] marked = SpotifyUiLabels.screenshotShufflePoint(684,1536);
        if (marked==null || Math.abs(marked[0]-530)>1 ||
            Math.abs(marked[1]-369)>1 || marked[0]<450)
            throw new AssertionError("screenshot must target right crossed arrows");
        int[] fullPixel = SpotifyUiLabels.screenshotShufflePoint(1080,2424);
        if (fullPixel==null || Math.abs(fullPixel[0]-837)>2 ||
            Math.abs(fullPixel[1]-582)>2)
            throw new AssertionError("Pixel coordinate scaling");
        int[] relative = SpotifyUiLabels.shufflePointFromPlay(
            573,330,650,410,0,0,684,1536,2.6f);
        if (relative==null || Math.abs(relative[0]-529)>6 ||
            Math.abs(relative[1]-370)>2)
            throw new AssertionError("right shuffle relative to Play");
        if (SpotifyUiLabels.safeRightShufflePoint(48,369,0,0,684,1536))
            throw new AssertionError("left download coordinate forbidden");
        if (SpotifyUiLabels.screenshotShufflePoint(1536,684)!=null)
            throw new AssertionError("landscape not calibrated");
        if (SpotifyUiLabels.shufflePointFromPlay(573,1300,650,1380,
            0,0,684,1536,2.6f)!=null)
            throw new AssertionError("bottom mini-player not a shuffle anchor");
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
