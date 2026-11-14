package com.andriy.jarvis;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Foreground watch-side connection to companion Android phone.
 * A successful send is NOT proof of execution. Only a matching phone ACK
 * activates the 8-bit phone state on the watch.
 */
public final class JarvisWearPhoneLink {
    public interface Callback { void finished(boolean ok, String message); }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MessageClient messageClient;
    private final MessageClient.OnMessageReceivedListener replies = this::onReply;
    private Task<Void> listenerRegistration;
    private boolean listenerReady;
    private boolean requesting;
    private boolean closed;
    private String activeSession = "";
    private String pendingId;
    private String pendingNode;
    private String pendingType;
    private Callback pendingCallback;
    private Runnable timeout;
    private long feedback;
    private final Runnable sync = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (listenerReady && !busy()) send(JarvisWearProtocol.SYNC, "", (ok, detail) -> {});
            main.postDelayed(this, 10000L);
        }
    };

    public JarvisWearPhoneLink(Context context) {
        this.context = context.getApplicationContext();
        this.messageClient = Wearable.getMessageClient(this.context);
        listenerRegistration = messageClient.addListener(replies,
            Uri.parse("wear://*" + JarvisWearProtocol.REPLY), MessageClient.FILTER_LITERAL);
        listenerRegistration.addOnSuccessListener(v -> {
            listenerReady = true;
            if (!closed) main.post(sync);
        });
    }

    public boolean busy() { return requesting || pendingId != null; }
    public boolean active() { return !activeSession.isEmpty() && JarvisPhoneMode.isActive(); }

    public boolean spotifyRunning() {
        return JarvisWearProtocol.SPOTIFY.equals(pendingType);
    }

    public boolean taxiRunning() {
        return JarvisWearProtocol.TAXI.equals(pendingType);
    }

    /** Local taxi command: phone controls Bolt without invoking conversational AI. */
    public void taxi(String voice, Callback callback) {
        if (!JarvisWearProtocol.validCommand(voice) ||
            JarvisTaxiCommands.destination(voice) == null) {
            callback.finished(false, "Невідома команда таксі.");
            return;
        }
        send(JarvisWearProtocol.TAXI, voice, callback);
    }

    public void spotify(String voice, Callback callback) {
        if (!JarvisWearProtocol.validCommand(voice) ||
            SpotifyMusicCommands.parse(voice) == null) {
            callback.finished(false,"Невідома команда Spotify.");
            return;
        }
        send(JarvisWearProtocol.SPOTIFY,voice,callback);
    }

    public void cancelSpotify(Callback callback) {
        if (!spotifyRunning() || pendingNode == null) {
            callback.finished(false,"Немає активного запиту Spotify.");
            return;
        }
        final String destination = pendingNode;
        clearPending();
        if (activeSession.isEmpty()) JarvisPhoneMode.setActive(false);
        try {
            JSONObject request = new JSONObject();
            request.put("id",UUID.randomUUID().toString());
            request.put("action",JarvisWearProtocol.CANCEL_SPOTIFY);
            request.put("session","");
            request.put("text","");
            messageClient.sendMessage(destination, JarvisWearProtocol.REQUEST,
                request.toString().getBytes(StandardCharsets.UTF_8))
                .addOnSuccessListener(result ->
                    callback.finished(true,"Надіслав на телефон скасування Spotify."))
                .addOnFailureListener(error ->
                    callback.finished(false,"Не вдалося надіслати скасування. " +
                        "Керування на телефоні завершиться за тайм-аутом."));
        } catch (Exception error) {
            callback.finished(false,"Помилка скасування Spotify на телефоні.");
        }
    }

    public void enter(Callback cb) { send(JarvisWearProtocol.ENTER, "", cb); }
    public void command(String voice, Callback cb) {
        if (!active()) {
            cb.finished(false, "Режим телефона не підключено. Скажи «Телефон».");
            return;
        }
        if (!JarvisWearProtocol.validCommand(voice)) {
            cb.finished(false, "Команда занадто довга або порожня.");
            return;
        }
        send(JarvisWearProtocol.COMMAND, voice, cb);
    }

    public void exit(Callback cb) {
        // The watch icon must immediately return to ordinary Jarvis even if
        // connection drops; the phone expires its own idle control session.
        if (!active()) {
            JarvisPhoneMode.setActive(false);
            cb.finished(true, "Звичайний Джарвіс.");
            return;
        }
        send(JarvisWearProtocol.EXIT, "", (ok, detail) -> {
            activeSession = "";
            JarvisPhoneMode.setActive(false);
            cb.finished(ok, ok ? "Звичайний Джарвіс." :
                "Годинник вийшов із режиму «Телефон». Телефон недоступний; " +
                "керування на ньому автоматично завершиться після паузи.");
        });
    }

    private void send(String type, String text, Callback cb) {
        if (closed) { cb.finished(false, "Зв'язок із телефоном вимкнено."); return; }
        // A background 10-second sync must never block a user's taxi command.
        if (!JarvisWearProtocol.SYNC.equals(type) &&
            JarvisWearProtocol.SYNC.equals(pendingType)) clearPending();
        if (busy()) { cb.finished(false, "Чекаю відповіді телефона на попередню команду."); return; }
        if (!JarvisWearProtocol.validAction(type)) {
            cb.finished(false, "Непідтримувана команда телефона."); return;
        }
        if (!listenerReady) {
            listenerRegistration.addOnSuccessListener(v -> {
                listenerReady = true;
                if (!closed) send(type,text,cb);
            });
            listenerRegistration.addOnFailureListener(err ->
                cb.finished(false, "Немає Wear OS Data Layer. Перевір сервіси Google."));
            return;
        }

        requesting = true;
        Wearable.getNodeClient(context).getConnectedNodes()
            .addOnSuccessListener(nodes -> {
                if (closed) { requesting = false; return; }
                Node target = selectPhone(nodes);
                if (target == null) {
                    requesting = false;
                    cb.finished(false, "Не знайшов підключений Android-телефон. " +
                        "На годиннику й телефоні має бути Джарвіс з однаковим підписом.");
                    return;
                }
                try {
                    JSONObject request = new JSONObject();
                    pendingId = UUID.randomUUID().toString();
                    requesting = false;
                    request.put("id",pendingId);
                    request.put("action",type);
                    request.put("text",text);
                    request.put("session",activeSession);
                    byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
                    pendingNode = target.getId();
                    pendingType = type;
                    pendingCallback = cb;
                    if (JarvisWearProtocol.COMMAND.equals(type) ||
                        JarvisWearProtocol.SPOTIFY.equals(type) ||
                        JarvisWearProtocol.TAXI.equals(type))
                        feedback = JarvisControlFeedback.begin();
                    if (data.length > JarvisWearProtocol.MAX_BYTES)
                        throw new IllegalArgumentException("Payload too large");
                    timeout = () -> failPending("Телефон не підтвердив виконання команди.");
                    main.postDelayed(timeout,
                        JarvisWearProtocol.SPOTIFY.equals(type) ?
                            JarvisWearProtocol.SPOTIFY_ACK_TIMEOUT_MS :
                        JarvisWearProtocol.TAXI.equals(type) ?
                            JarvisWearProtocol.TAXI_ACK_TIMEOUT_MS :
                            JarvisWearProtocol.ACK_TIMEOUT_MS);
                    if (JarvisWearProtocol.SPOTIFY.equals(type) ||
                        JarvisWearProtocol.TAXI.equals(type))
                        JarvisPhoneMode.setActive(true);
                    messageClient.sendMessage(target.getId(),JarvisWearProtocol.REQUEST,data)
                        .addOnFailureListener(err -> failPending(
                            "Не вдалося передати команду на телефон. Перевір Bluetooth/Wi-Fi."));
                } catch (Exception error) {
                    requesting = false;
                    if (pendingId != null)
                        failPending("Не вдалося підготувати команду для телефона.");
                    else cb.finished(false,"Не вдалося підготувати команду для телефона.");
                }
            })
            .addOnFailureListener(err -> {
                requesting = false;
                if (!closed) cb.finished(false,"Немає зв'язку з телефоном через Wear OS.");
            });
    }

    private static Node selectPhone(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) return null;
        if (nodes.size() == 1) return nodes.get(0);
        Node onlyNearby = null;
        for (Node node : nodes) {
            if (node.isNearby()) {
                if (onlyNearby != null) return null; // never pick a random phone
                onlyNearby = node;
            }
        }
        return onlyNearby;
    }

    private void onReply(MessageEvent event) {
        if (!JarvisWearProtocol.REPLY.equals(event.getPath()) ||
            pendingId == null || pendingNode == null ||
            !pendingNode.equals(event.getSourceNodeId())) return;
        if (event.getData() == null || event.getData().length > JarvisWearProtocol.MAX_BYTES) return;
        try {
            JSONObject data = new JSONObject(new String(event.getData(),StandardCharsets.UTF_8));
            if (!pendingId.equals(data.optString("id"))) return;
            final String type = pendingType;
            final Callback cb = pendingCallback;
            final boolean ok = data.optBoolean("ok",false);
            final String detail = data.optString("detail","Телефон не повернув результат.");
            if (JarvisWearProtocol.ENTER.equals(type) && ok) {
                String token = data.optString("session","");
                if (!JarvisWearProtocol.validId(token)) {
                    failPending("Телефон повернув некоректне підтвердження.");
                    return;
                }
                activeSession = token;
                JarvisPhoneMode.setActive(true);
            } else if (JarvisWearProtocol.SYNC.equals(type) && ok) {
                String token = data.optString("session", "");
                activeSession = JarvisWearProtocol.validId(token) ? token : "";
                JarvisPhoneMode.setActive(!activeSession.isEmpty());
            } else if (JarvisWearProtocol.COMMAND.equals(type) &&
                       data.optBoolean("session_ended",false)) {
                activeSession = "";
                JarvisPhoneMode.setActive(false);
            } else if (JarvisWearProtocol.SPOTIFY.equals(type) ||
                       JarvisWearProtocol.TAXI.equals(type)) {
                // Temporary task complete: restore the usual 8-bit face.
                if (activeSession.isEmpty()) JarvisPhoneMode.setActive(false);
            }
            JarvisControlFeedback.finish(feedback,ok,SystemClock.uptimeMillis());
            feedback = 0L;
            clearPending();
            if (cb != null) cb.finished(ok,detail);
        } catch (Exception ignored) { }
    }

    private void failPending(String reason) {
        Callback cb = pendingCallback;
        boolean exiting = JarvisWearProtocol.EXIT.equals(pendingType);
        boolean spotify = JarvisWearProtocol.SPOTIFY.equals(pendingType);
        boolean taxi = JarvisWearProtocol.TAXI.equals(pendingType);
        boolean syncing = JarvisWearProtocol.SYNC.equals(pendingType);
        clearPending();
        if (syncing) { activeSession = ""; JarvisPhoneMode.setActive(false); }
        if ((spotify || taxi) && activeSession.isEmpty()) JarvisPhoneMode.setActive(false);
        if (exiting) {
            activeSession = "";
            JarvisPhoneMode.setActive(false);
        }
        if (cb != null) cb.finished(false,reason);
    }

    private void clearPending() {
        JarvisControlFeedback.finish(feedback,false,SystemClock.uptimeMillis());
        feedback = 0L;
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = null;
        pendingId = null;
        pendingNode = null;
        pendingType = null;
        pendingCallback = null;
    }

    public void close() {
        closed = true;
        requesting = false;
        clearPending();
        main.removeCallbacks(sync);
        messageClient.removeListener(replies);
        activeSession = "";
        JarvisPhoneMode.setActive(false);
    }
}
