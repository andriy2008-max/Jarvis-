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
        System.out.println("PASS SpotifyUiLabelsTest");
    }
}
