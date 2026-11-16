package com.andriy.jarvis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.android.gms.wearable.Wearable;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** A live request heartbeat is not a final result and never acknowledges a write.
 * A separate path keeps older watch builds from treating progress as completion. */
final class JarvisWearProgress {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final Runnable pulse;

    JarvisWearProgress(Context context, String node, String id, String action) {
        Context app = context.getApplicationContext();
        pulse = new Runnable() {
            @Override public void run() {
                if (stopped.get()) return;
                try {
                    JSONObject data = new JSONObject();
                    data.put("id", id);
                    data.put("action", action);
                    data.put("detail", "Телефон продовжує виконання команди...");
                    Wearable.getMessageClient(app).sendMessage(node, JarvisWearProtocol.PROGRESS,
                        data.toString().getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) { }
                if (!stopped.get()) main.postDelayed(this, 5000L);
            }
        };
        if (JarvisWearProtocol.acceptsProgress(action)) main.postDelayed(pulse, 5000L);
    }

    void close() {
        stopped.set(true);
        main.removeCallbacks(pulse);
    }
}
