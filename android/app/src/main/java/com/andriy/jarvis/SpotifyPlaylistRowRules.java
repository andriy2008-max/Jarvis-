package com.andriy.jarvis;

import java.util.Locale;

/** Strict single-song-row geometry for explicit Spotify playlist playback.
 * No blind green Play taps and no menu, search or bottom mini-player presses.
 */
final class SpotifyPlaylistRowRules {
    private SpotifyPlaylistRowRules(){}

    static boolean row(int left,int top,int right,int bottom,
                       int screenL,int screenT,int screenR,int screenB,
                       float density) {
        int width=screenR-screenL,height=screenB-screenT;
        int w=right-left,h=bottom-top;
        return width>=320 && height>=640 && density>0f &&
            w>=width*.40f && w<=width*1.02f &&
            h>=Math.max(22,density*22f) && h<=height*.13f &&
            top>=screenT+height*.28f &&
            bottom<screenT+height*.88f &&
            left>=screenL && right<=screenR;
    }

    static boolean possibleTitle(String raw) {
        if(raw==null)return false;
        String s=raw.toLowerCase(Locale.forLanguageTag("uk-UA")).trim();
        if(s.length()<2 || s.length()>150 || s.matches("\\p{Punct}+"))return false;
        String plain=SpotifyUiLabels.normalize(s);
        if(plain.isEmpty())return false;
        String[] banned={
            "play","pause","shuffle","repeat","back","назад",
            "відтворити","пауза","додати","видалити","завантажити",
            "завантажено","вподобати","подобається","вибрати",
            "моя бібліотека","your library","liked songs",
            "пісні що сподобалися","сортурувати","сортувати",
            "фільтр","filter","реп","rap","пошук","search",
            "поділитися","share","редагувати","edit","більше","more",
            "перемішування","зберегти","save","playlist",
            "плейлист","плейліст","укр муз"
        };
        for(String b:banned)
            if(plain.equals(b) || plain.startsWith(b+" ") ||
               plain.endsWith(" "+b))return false;
        return true;
    }
}
