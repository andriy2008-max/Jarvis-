package com.andriy.jarvis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explicit watch-local commands. Ordinary Watch7 speech still goes to Pixel.
 * No AI-generated device actions and no silently reinterpreted phone commands.
 */
public final class JarvisWatchLocalCommands {
    public static final class Request {
        public final String app;
        public final ScreenControlCommands.Action screen;
        public final SpotifyMusicCommands.Kind music;
        public final String musicTitle;
        private Request(String app,ScreenControlCommands.Action screen,
                        SpotifyMusicCommands.Kind music,String musicTitle) {
            this.app=app;this.screen=screen;this.music=music;this.musicTitle=musicTitle;
        }
        public boolean opensApp() { return app!=null; }
        public boolean playsSpotify() { return music!=null; }
    }

    private static final Pattern WAKE=Pattern.compile(
        "(?iu)^(?:джарвіс|джарвис|jarvis)[\\s,:—-]+");
    private static final Pattern WATCH_SUFFIX=Pattern.compile(
        "(?iu)\\s+(?:на|у|в)\\s+(?:(?:моєму|своєму)\\s+)?"+
        "(?:годиннику|watch7|watch)\\s*[.!?]*$");

    private JarvisWatchLocalCommands() {}

    /** Prevent a failed watch-targeted command from silently running on Pixel. */
    public static boolean targetsWatch(String raw) {
        return raw!=null && raw.length()<=450 &&
            WATCH_SUFFIX.matcher(raw.trim()).find();
    }

    public static Request parse(String raw) {
        if(raw==null || raw.length()>450)return null;
        String source=raw.trim();
        Matcher suffix=WATCH_SUFFIX.matcher(source);
        if(!suffix.find())return null;
        String local=WAKE.matcher(source.substring(0,suffix.start()).trim())
            .replaceFirst("").trim();
        if(local.isEmpty())return null;
        // Watch-specific Spotify playback is handled locally, before the
        // generic app/phone router. Only explicitly named tracks/playlists.
        SpotifyMusicCommands.Kind music=SpotifyMusicCommands.parse(local);
        if(music==SpotifyMusicCommands.Kind.TRACK) {
            String title=SpotifyMusicCommands.namedTrack(local);
            return title==null?null:new Request(null,null,music,title);
        }
        if(music==SpotifyMusicCommands.Kind.PLAYLIST) {
            SpotifyMusicCommands.NamedPlaylist playlist=
                SpotifyMusicCommands.namedPlaylist(local);
            return playlist==null?null:new Request(null,null,music,playlist.name);
        }
        String name=AssistantIntents.requestedApp(
            CommandPatterns.normalizeControlVerb(local));
        if(name!=null && !name.trim().isEmpty() && name.length()<=80)
            return new Request(name.trim(),null,null,null);
        ScreenControlCommands.Action step=ScreenControlCommands.parse(local);
        if(step==null)return null;
        switch(step.kind) {
            case CLICK:case LONG_PRESS:case TYPE_TEXT:
                if(step.label==null || step.label.trim().isEmpty() ||
                    step.label.length()>150 ||
                    (step.kind!=ScreenControlCommands.Kind.TYPE_TEXT &&
                     ScreenControlCommands.isSensitiveLabel(step.label)))
                    return null;
                return new Request(null,step,null,null);
            case BACK:case SCROLL_UP:case SCROLL_DOWN:
            case SWIPE_UP:case SWIPE_DOWN:case SWIPE_LEFT:case SWIPE_RIGHT:
                return new Request(null,step,null,null);
            default:return null;
        }
    }
}
