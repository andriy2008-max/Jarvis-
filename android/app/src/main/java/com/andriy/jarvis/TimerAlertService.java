package com.andriy.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** A short, user-visible alarm sound and strong vibration when a timer expires. */
public final class TimerAlertService extends Service {
    public static final String START = "com.andriy.jarvis.TIMER_ALERT_START";
    public static final String STOP = "com.andriy.jarvis.TIMER_ALERT_STOP";
    private static final String CHANNEL = "jarvis_timer_ring_v1";
    private static final String FALLBACK_CHANNEL = "jarvis_timer_notification_v1";
    private static final long MAX_RING_MILLIS = 30_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private ToneGenerator tone;
    private Vibrator vibrator;
    private Runnable beep;
    private Runnable timeout;

    @Override public IBinder onBind(Intent intent) { return null; }

    private static NotificationManager notifications(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static AudioAttributes ringAttributes() {
        return new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    }

    private static void channels(Context c) {
        NotificationManager nm = notifications(c);
        if (nm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel service = new NotificationChannel(CHANNEL,
            "Джарвіс — сигнал таймера", NotificationManager.IMPORTANCE_HIGH);
        service.setDescription("Звуковий сигнал відтворюється через гучність дзвінка");
        service.setSound(null, null); // MediaPlayer supplies ring-channel sound, not a double beep.
        service.enableVibration(false);
        nm.createNotificationChannel(service);

        NotificationChannel backup = new NotificationChannel(FALLBACK_CHANNEL,
            "Джарвіс — резервне сповіщення таймера", NotificationManager.IMPORTANCE_HIGH);
        backup.enableVibration(true);
        backup.setVibrationPattern(new long[]{0L, 1000L, 250L, 1000L, 300L, 1200L});
        Uri ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (ring != null) backup.setSound(ring, ringAttributes());
        nm.createNotificationChannel(backup);
    }

    private static PendingIntent openApp(Context c) {
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 7720, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Notification buildNotification(Context c, String channel, boolean ongoing) {
        Intent stop = new Intent(c, TimerAlertService.class);
        stop.setAction(STOP);
        PendingIntent action = PendingIntent.getService(c, 7721, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Джарвіс — час вийшов!")
            .setContentText("Таймер завершено")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_HIGH)
            .setContentIntent(openApp(c))
            .setOngoing(ongoing)
            .addAction(android.R.drawable.ic_media_pause, "Зупинити сигнал", action)
            .build();
    }

    static void postFallback(Context c) {
        channels(c);
        NotificationManager nm = notifications(c);
        if (nm == null) return;
        try {
            nm.notify(TimerScheduler.ALERT_NOTIFICATION,
                buildNotification(c, FALLBACK_CHANNEL, false));
        } catch (SecurityException ignored) { /* User denied POST_NOTIFICATIONS. */ }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (timeout != null) return START_NOT_STICKY; // Prevent duplicate alerts.
        try {
            channels(this);
            Notification notice = buildNotification(this, CHANNEL, true);
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(TimerScheduler.ALERT_NOTIFICATION, notice,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(TimerScheduler.ALERT_NOTIFICATION, notice);
            }
            startVibration();
            startRing();
            timeout = this::stopSelf;
            handler.postDelayed(timeout, MAX_RING_MILLIS);
        } catch (RuntimeException error) {
            postFallback(this);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startVibration() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = new long[]{0L, 1000L, 200L, 1000L, 250L, 1400L, 500L};
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } catch (RuntimeException ignored) { /* Some watches disallow waveform amplitude. */ }
    }

    private void startRing() {
        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (ringtone != null) {
            try {
                player = new MediaPlayer();
                player.setAudioAttributes(ringAttributes());
                player.setDataSource(this, ringtone);
                player.setLooping(true);
                player.prepare();
                player.start();
                return;
            } catch (Exception error) {
                if (player != null) { player.release(); player = null; }
            }
        }
        // Device has no readable default ringtone: ring-stream beeps remain available.
        try {
            tone = new ToneGenerator(AudioManager.STREAM_RING, 95);
            beep = new Runnable() {
                @Override public void run() {
                    if (tone == null) return;
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 700);
                    handler.postDelayed(this, 1200L);
                }
            };
            handler.post(beep);
        } catch (RuntimeException ignored) { }
    }

    @Override public void onDestroy() {
        if (timeout != null) handler.removeCallbacks(timeout);
        if (beep != null) handler.removeCallbacks(beep);
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (RuntimeException ignored) { }
            player.release();
            player = null;
        }
        if (tone != null) { tone.release(); tone = null; }
        if (vibrator != null) { vibrator.cancel(); vibrator = null; }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
