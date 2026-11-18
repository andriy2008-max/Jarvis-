package com.andriy.jarvis;

public final class JarvisWatchMusicFollowupTest {
    private static void yes(boolean ok,String label) {
        if(!ok)throw new AssertionError("Watch music follow-up: "+label);
    }
    public static void main(String[] args) {
        yes(JarvisWatchMusicFollowup.missingTitle("увімкни пісню")==
            SpotifyMusicCommands.Kind.TRACK,"no-title song");
        yes(JarvisWatchMusicFollowup.missingTitle("Дімон, включи пісню")==
            SpotifyMusicCommands.Kind.TRACK,"Dimon wake and synonym");
        yes(JarvisWatchMusicFollowup.missingTitle("Дімон увімкни пісню!")==
            SpotifyMusicCommands.Kind.TRACK,"punctuation");
        yes(JarvisWatchMusicFollowup.missingTitle("Джарвіс, увімкни пісню")==
            SpotifyMusicCommands.Kind.TRACK,"legacy alias");
        yes(JarvisWatchMusicFollowup.missingTitle(
            "увімкни пісню на годиннику")==SpotifyMusicCommands.Kind.TRACK,
            "explicit watch intent");
        yes(JarvisWatchMusicFollowup.missingTitle("включи плейлист")==
            SpotifyMusicCommands.Kind.PLAYLIST,"no-title playlist");
        yes(JarvisWatchMusicFollowup.missingTitle(
            "Дімон, включи плейлист на годиннику")==
            SpotifyMusicCommands.Kind.PLAYLIST,"explicit watch playlist");
        yes(JarvisWatchMusicFollowup.missingTitle(
            "увімкни пісню Пачка сигарет")==null,"complete song must play");
        yes(JarvisWatchMusicFollowup.missingTitle(
            "увімкни плейлист Вечірній")==null,"complete playlist must play");
        yes(JarvisWatchMusicFollowup.missingTitle(
            "увімкни реп")==null,"other Spotify command unchanged");
        yes("пачка сигарет".equals(JarvisWatchMusicFollowup.titleOnly(
            "Пачка сигарет")),"bare title followup");
        yes("пачка сигарет".equals(JarvisWatchMusicFollowup.titleOnly(
            "Дімон, Пачка сигарет")),"title with wake");
        yes(JarvisWatchMusicFollowup.titleOnly("Дімон, де я")==null,
            "map command must remain map command");
        yes(JarvisWatchMusicFollowup.titleOnly(
            "увімкни пісню Пачка сигарет")==null,
            "full Spotify request must use direct handler");
        yes(JarvisWatchMusicFollowup.isCancel("Дімон, скасуй"),
            "cancel followup");
        yes(JarvisWatchMusicFollowup.titleOnly("скасуй")==null,
            "no search for cancel");
        yes(JarvisWatchLocalCommands.spotifyOnWatch(
            "Дімон, увімкни пісню Пачка сигарет")!=null,
            "full command still runs locally on watch");
        yes(JarvisWatchLocalCommands.spotifyOnWatch(
            "Дімон, увімкни пісню")==null,
            "incomplete command must go through prompt, not player");
        System.out.println("PASS JarvisWatchMusicFollowupTest");
    }
}
