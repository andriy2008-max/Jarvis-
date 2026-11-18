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
        private Request(String app,ScreenControlCommands.Action screen) {
            this.app=app;this.screen=screen;
        }
        public boolean opensApp() { return app!=null; }
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
        String name=AssistantIntents.requestedApp(
            CommandPatterns.normalizeControlVerb(local));
        if(name!=null && !name.trim().isEmpty() && name.length()<=80)
            return new Request(name.trim(),null);
        ScreenControlCommands.Action step=ScreenControlCommands.parse(local);
        if(step==null)return null;
        switch(step.kind) {
            case CLICK:case LONG_PRESS:case TYPE_TEXT:
                if(step.label==null || step.label.trim().isEmpty() ||
                    step.label.length()>150 ||
                    (step.kind!=ScreenControlCommands.Kind.TYPE_TEXT &&
                     ScreenControlCommands.isSensitiveLabel(step.label)))
                    return null;
                return new Request(null,step);
            case BACK:case SCROLL_UP:case SCROLL_DOWN:
            case SWIPE_UP:case SWIPE_DOWN:case SWIPE_LEFT:case SWIPE_RIGHT:
                return new Request(null,step);
            default:return null;
        }
    }
}
