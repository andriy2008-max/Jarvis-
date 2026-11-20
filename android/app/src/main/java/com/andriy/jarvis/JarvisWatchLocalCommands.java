package com.andriy.jarvis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explicit watch-local screen actions; every Spotify command spoken in the
 * watch Activity runs only on Wear OS. No Pixel fallback for Spotify intents.
 * No AI-generated device actions or silently reinterpreted phone commands.
 */
public final class JarvisWatchLocalCommands {
    public static final class Request {
        public final String app;
        public final ScreenControlCommands.Action screen;
        public final SpotifyMusicCommands.Kind music;
        public final String musicTitle;
        public final JarvisWatchMapsCommands.Request maps;
        private Request(String app,ScreenControlCommands.Action screen,
                        SpotifyMusicCommands.Kind music,String musicTitle) {
            this(app,screen,music,musicTitle,null);
        }
        private Request(String app,ScreenControlCommands.Action screen,
                        SpotifyMusicCommands.Kind music,String musicTitle,
                        JarvisWatchMapsCommands.Request mapsRequest) {
            this.app=app;this.screen=screen;this.music=music;this.musicTitle=musicTitle;
            this.maps=mapsRequest;
        }
        public boolean opensApp() { return app!=null; }
        public boolean playsSpotify() { return music!=null; }
        public boolean usesMaps() { return maps!=null; }
    }

    private static final Pattern WAKE=Pattern.compile(
        "(?iu)^(?:дімон|димон|dimon|джарвіс|джарвис|jarvis)[\\s,:—-]+");
    private static final Pattern WATCH_SUFFIX=Pattern.compile(
        "(?iu)\\s+(?:на|у|в)\\s+(?:(?:моєму|своєму)\\s+)?"+
        "(?:годиннику|годинку|годинник|watch7|watch)\\s*[.!?]*$");

    private JarvisWatchLocalCommands() {}

    /** Prevent a failed watch-targeted command from silently running on Pixel. */
    public static boolean targetsWatch(String raw) {
        return raw!=null && raw.length()<=450 &&
            WATCH_SUFFIX.matcher(raw.trim()).find();
    }

    /** Spotify spoken inside the Watch app always stays on Wear OS.
     * Works without a wake word, including "Відкрий Spotify" and
     * "відкрити Спотіфай на годинку" transcription variants.
     * Call this only from a WATCH Activity, not the phone dispatcher. */
    public static Request spotifyOnWatch(String raw) {
        if(raw==null || raw.length()>450)return null;
        String local=raw.trim();
        Matcher suffix=WATCH_SUFFIX.matcher(local);
        if(suffix.find())local=local.substring(0,suffix.start()).trim();
        local=WAKE.matcher(local).replaceFirst("").trim();
        if(local.isEmpty())return null;
        String requested=AssistantIntents.requestedApp(
            CommandPatterns.normalizeControlVerb(local));
        if("spotify".equals(AssistantIntents.appLabel(requested)) ||
           local.matches("(?iu)^(?:відкрити|відкрий|запусти|зайди\\s+в|зайди\\s+до)\\s+(?:(?:програму|додаток|застосунок)\\s+)?(?:spotify|спотіфай|спотифай)[.!?]*$"))
            return new Request("spotify",null,null,null);
        SpotifyMusicCommands.Kind music=SpotifyMusicCommands.parse(local);
        if(music==SpotifyMusicCommands.Kind.NEXT_RESULT)
            return new Request(null,null,music,null);
        if(music==SpotifyMusicCommands.Kind.TRACK) {
            String title=SpotifyMusicCommands.namedTrack(local);
            return title==null?null:new Request(null,null,music,title);
        }
        if(music==SpotifyMusicCommands.Kind.PLAYLIST) {
            SpotifyMusicCommands.NamedPlaylist playlist=
                SpotifyMusicCommands.namedPlaylist(local);
            return playlist==null?null:new Request(null,null,music,playlist.name);
        }
        if(music==SpotifyMusicCommands.Kind.OPEN)
            return new Request("spotify",null,null,null);
        if(music==SpotifyMusicCommands.Kind.RAP)
            return new Request(null,null,music,"реп");
        if(music==SpotifyMusicCommands.Kind.UKRAINIAN)
            return new Request(null,null,music,"українські пісні");
        return null;
    }

    /** True for Spotify voice intents, even if a particular action cannot
     * be handled by the installed version. Never send these to Pixel. */
    public static boolean spotifyRelatedOnWatch(String raw) {
        if(raw==null || raw.length()>450)return false;
        String local=raw.trim();
        Matcher suffix=WATCH_SUFFIX.matcher(local);
        if(suffix.find())local=local.substring(0,suffix.start()).trim();
        local=WAKE.matcher(local).replaceFirst("").trim();
        String normalized=CommandPatterns.normalize(local);
        return SpotifyMusicCommands.parse(local)!=null ||
            normalized.matches("(?iu)^(?:скасуй|зупини|пауза|продовж|віднови)\\s+(?:spotify|спотіфай|спотифай)$") ||
            normalized.matches("(?iu)^(?:відкрий|відкрити|запусти|закрий|зайди.*)\\s+(?:spotify|спотіфай|спотифай)$");
    }

    /** Location and directions uttered into the Watch app use Watch7 Maps. */
    public static Request mapsOnWatch(String raw) {
        if(raw==null || raw.length()>450 || targetsWatch(raw))return null;
        JarvisWatchMapsCommands.Request maps=JarvisWatchMapsCommands.parse(raw);
        return maps==null?null:new Request(null,null,null,null,maps);
    }

    public static Request parse(String raw) {
        if(raw==null || raw.length()>450)return null;
        String source=raw.trim();
        Matcher suffix=WATCH_SUFFIX.matcher(source);
        if(!suffix.find())return null;
        String local=WAKE.matcher(source.substring(0,suffix.start()).trim())
            .replaceFirst("").trim();
        if(local.isEmpty())return null;
        // For a watch-targeted command the suffix was already removed.
        Request mapsRequest=mapsOnWatch(local);
        if(mapsRequest!=null)return mapsRequest;
        Request musicRequest=spotifyOnWatch(local);
        if(musicRequest!=null)return musicRequest;
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
