package com.andriy.jarvis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;

/** Explicit user-initiated direct Spotify playback, without screen taps.
 * Requires a developer Client ID configured locally and Spotify authorization.
 * Never stores a client secret or an OAuth token in Jarvis.
 */
public final class SpotifyAppRemoteBridge {
    public interface Listener { void completed(boolean success,String detail); }
    private static final long CONNECT_TIMEOUT_MS=17000L;
    private final Context context;
    private final String clientId;
    private final String uri;
    private final boolean resume;
    private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->finish(false,
        "Spotify App Remote не відповів. Перевір авторизацію Spotify.");
    private SpotifyAppRemote remote;
    private boolean finished;

    /** uri==null means only authorize/test connection, not play audio. */
    public SpotifyAppRemoteBridge(Context context,String clientId,
                                  String uri,Listener listener) {
        this(context,clientId,uri,false,listener);
    }
    public SpotifyAppRemoteBridge(Context context,String clientId,
                                  String uri,boolean resume,Listener listener) {
        this.context=context;
        this.clientId=clientId;
        this.uri=uri;
        this.resume=resume;
        this.listener=listener;
    }
    public void connect() {
        if(!SpotifyUriRules.validClientId(clientId) ||
            uri!=null && SpotifyUriRules.canonical(uri)==null) {
            finish(false,"Некоректні налаштування Spotify App Remote.");
            return;
        }
        try {
            ConnectionParams params=new ConnectionParams.Builder(clientId)
                .setRedirectUri(SpotifyDirectSettings.REDIRECT_URI)
                .showAuthView(true)
                .build();
            handler.postDelayed(timeout,CONNECT_TIMEOUT_MS);
            SpotifyAppRemote.connect(context,params,new Connector.ConnectionListener(){
                @Override public void onConnected(SpotifyAppRemote connected) {
                    handler.post(()->{
                        if(finished) {
                            SpotifyAppRemote.disconnect(connected);
                            return;
                        }
                        remote=connected;
                        if(uri==null && !resume) {
                            finish(true,"Spotify підключено. Дозвіл App Remote отримано.");
                            return;
                        }
                        try {
                            (resume ? connected.getPlayerApi().resume() :
                                connected.getPlayerApi().play(uri))
                                .setResultCallback(empty -> handler.post(()->
                                    finish(true,"Spotify прийняв команду прямого відтворення.")))
                                .setErrorCallback(error -> handler.post(()->
                                    finish(false,"Spotify не запустив вибраний трек або плейлист.")));
                        } catch(RuntimeException error) {
                            finish(false,"Spotify відмовив у прямому відтворенні.");
                        }
                    });
                }
                @Override public void onFailure(Throwable error) {
                    handler.post(()->finish(false,
                        "Spotify App Remote не підключився. Перевір Client ID, дозвіл та Spotify."));
                }
            });
        } catch(RuntimeException error) {
            finish(false,"Не вдалося відкрити підключення Spotify.");
        }
    }
    public void cancel() {
        handler.removeCallbacks(timeout);
        if(finished)return;
        finished=true;
        if(remote!=null){
            try{SpotifyAppRemote.disconnect(remote);}catch(RuntimeException ignored){}
            remote=null;
        }
    }
    private void finish(boolean success,String detail) {
        if(finished)return;
        finished=true;
        handler.removeCallbacks(timeout);
        if(remote!=null) {
            try{SpotifyAppRemote.disconnect(remote);}catch(RuntimeException ignored){}
            remote=null;
        }
        if(listener!=null)listener.completed(success,detail);
    }
}
