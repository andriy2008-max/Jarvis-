package com.andriy.jarvis;

/** Cross-feature regression contract for the user-visible Dimon voice name. */
public final class DimonVoiceNameTest {
    private static void yes(boolean value,String description) {
        if(!value)throw new AssertionError("Dimon name: "+description);
    }
    public static void main(String[] args) {
        yes("увімкни ліхтарик".equals(
            CommandPatterns.normalize("Дімон, увімкни ліхтарик")),
            "normal command strips new wake name");
        yes("увімкни ліхтарик".equals(
            CommandPatterns.normalize("Димон увімкни ліхтарик")),
            "Russian spelling of wake name");
        yes("Привіт, Дімон!".equals(
            CommandPatterns.spokenText("Дімон, скажи Привіт, Дімон!")),
            "dictated text stays unchanged");
        yes("Привіт, Дімон!".equals(
            CommandPatterns.spokenText("Дімон, скажи Привіт, Дімон!")),
            "no mutation of user-provided text");

        WakeConversation conversation=new WakeConversation();
        yes(conversation.accept("Дімон",100,false).kind==
            WakeConversation.Kind.ARMED,"standalone wake");
        WakeConversation.Decision command=conversation.accept(
            "Дімон, котра година?",200,false);
        yes(command.kind==WakeConversation.Kind.COMMAND &&
            "котра година".equals(command.command),"spoken command after wake");
        yes(conversation.accept("Бувай Дімон",300,false).kind==
            WakeConversation.Kind.FINISHED,"conversation ending");

        yes(JarvisShakeGate.containsWakeWord("Дімон"),"shake wake");
        yes(JarvisShakeGate.isWakeOnly("Дімон!"),"standalone wake gate");
        yes(JarvisShakeGate.aiRequested("Дімон, що таке космос?"),
            "AI address routing");
        yes(!JarvisShakeGate.containsWakeWord("Дімончик"),
            "not an embedded word");
        yes(JarvisSearchRouting.addressedToJarvis("Дімон, знайди новини"),
            "AI search address");

        yes(JarvisPhoneMode.transition("Дімон телефон")==
            JarvisPhoneMode.Transition.ENTER,"enter phone mode");
        yes(JarvisPhoneMode.transition("Дімон")==
            JarvisPhoneMode.Transition.EXIT,"exit phone mode");
        yes(JarvisPhoneMode.transition("Джарвіс")==
            JarvisPhoneMode.Transition.EXIT,"legacy exit remains");

        yes("вокзалу".equals(JarvisTaxiCommands.destination(
            "Дімон, таксі до вокзалу")),"Bolt command");
        yes(ScreenControlCommands.parse("Дімон, натисни пошук").kind==
            ScreenControlCommands.Kind.CLICK,"screen control");
        yes(JarvisWatchMapsCommands.parse("Дімон, де я").whereAmI,
            "Maps location watch");
        JarvisWatchLocalCommands.Request map=
            JarvisWatchLocalCommands.mapsOnWatch(
                "Дімон, знайди дорогу до Стебник, вулиця Грушевського 14а");
        yes(map!=null && map.usesMaps() &&
            map.maps.destination.startsWith("Стебник"),"Maps directions watch");

        JarvisWatchLocalCommands.Request song=
            JarvisWatchLocalCommands.spotifyOnWatch(
                "Дімон, увімкни пісню Пачка сигарет");
        yes(song!=null && song.playsSpotify() &&
            song.music==SpotifyMusicCommands.Kind.TRACK,"watch Spotify");
        JarvisWatchLocalCommands.Request app=
            JarvisWatchLocalCommands.parse(
                "Дімон, відкрий Spotify на годиннику");
        yes(app!=null && app.opensApp(),"watch app launch");

        TelegramReplyCommands.Request telegram=
            TelegramReplyCommands.parse(
                "Дімон, напиши в телеграм Олена: Привіт");
        yes(telegram!=null &&
            telegram.kind==TelegramReplyCommands.Kind.DRAFT &&
            "Привіт".equals(telegram.text),"Telegram draft");
        yes("Привіт, Дімон".equals(
            TelegramReplyCommands.dictatedCorrection("Дімон, Привіт, Дімон")),
            "Telegram dictated message preserved");
        yes(CommandPatterns.normalize("Джарвіс, увімкни ліхтарик")
            .equals("увімкни ліхтарик"),"old wake name remains alias");
        System.out.println("PASS DimonVoiceNameTest");
    }
}
