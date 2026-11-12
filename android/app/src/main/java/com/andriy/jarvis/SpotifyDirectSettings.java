package com.andriy.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;

/**
 * User-owned, local Spotify App Remote settings.
 * Client ID is public, not a client secret. No access/refresh tokens stored.
 */
public final class SpotifyDirectSettings {
    public static final String REDIRECT_URI="http://127.0.0.1:43827/callback";
    private static final String PREFS="jarvis_spotify_direct_v1";
    private static final String KEY_CLIENT="client_id";
    private static final String KEY_PLAYLISTS="content_uris";
    private SpotifyDirectSettings(){}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public static String clientId(Context c) {
        String id=prefs(c).getString(KEY_CLIENT,"");
        return SpotifyUriRules.validClientId(id)?id.trim():"";
    }

    public static String entries(Context c) {
        StringBuilder out=new StringBuilder();
        try {
            JSONArray data=new JSONArray(prefs(c).getString(KEY_PLAYLISTS,"[]"));
            for(int i=0;i<data.length();i++) {
                JSONObject item=data.optJSONObject(i);
                if(item==null)continue;
                String name=item.optString("name","");
                String uri=item.optString("uri","");
                if(!name.isEmpty()&&!uri.isEmpty())
                    out.append(name).append(" | ").append(uri).append('\n');
            }
        } catch(Exception ignored){}
        return out.toString().trim();
    }

    /** On failure return a message without overwriting existing configuration. */
    public static synchronized String save(Context context,String client,String text) {
        client=client==null?"":client.trim();
        if(!client.isEmpty()&&!SpotifyUriRules.validClientId(client))
            return "Spotify Client ID має містити рівно 32 символи 0–9 / a–f.";
        JSONArray data=new JSONArray();
        HashSet<String> used=new HashSet<>();
        String raw=text==null?"":text.trim();
        if(raw.length()>12500)return "Завеликий список плейлистів.";
        if(!raw.isEmpty()) {
            String[] lines=raw.split("\\r?\\n");
            if(lines.length>60)return "Максимум 60 посилань.";
            for(int i=0;i<lines.length;i++) {
                String line=lines[i].trim();
                if(line.isEmpty())continue;
                int separator=line.indexOf('|');
                if(separator<=0)return "Рядок "+(i+1)+": формат Назва | Spotify-посилання.";
                String name=line.substring(0,separator).trim();
                String link=line.substring(separator+1).trim();
                String uri=SpotifyUriRules.canonical(link);
                String key=SpotifyUriRules.normalizedAlias(name);
                if(key.isEmpty()||name.length()>75||uri==null)
                    return "Рядок "+(i+1)+": перевір назву й Spotify-посилання.";
                if(!used.add(key))return "Назва «"+name+"» повторюється.";
                JSONObject item=new JSONObject();
                try { item.put("name",name);item.put("uri",uri);data.put(item); }
                catch(Exception error){return "Помилка збереження списку.";}
            }
        }
        return prefs(context).edit()
            .putString(KEY_CLIENT,client)
            .putString(KEY_PLAYLISTS,data.toString()).commit()
            ? null : "Не вдалося зберегти Spotify-налаштування.";
    }

    /** No network lookup: direct control requires an explicit URI bound to
     * the spoken alias. Otherwise the existing Spotify UI flow is used. */
    public static String resolve(Context c,SpotifyMusicCommands.Kind kind,String name) {
        if(kind==null||clientId(c).isEmpty())return null;
        String alias;
        switch(kind) {
            case RAP: alias="реп";break;
            case UKRAINIAN: alias="укр муз";break;
            case PLAYLIST: alias=name;break;
            default: return null;
        }
        String wanted=SpotifyUriRules.normalizedAlias(alias);
        if(wanted.isEmpty())return null;
        try {
            JSONArray list=new JSONArray(prefs(c).getString(KEY_PLAYLISTS,"[]"));
            for(int i=0;i<list.length();i++) {
                JSONObject item=list.optJSONObject(i);
                if(item==null)continue;
                if(wanted.equals(SpotifyUriRules.normalizedAlias(
                    item.optString("name",""))))
                    return SpotifyUriRules.canonical(item.optString("uri",""));
            }
        }catch(Exception ignored){}
        return null;
    }
}
