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

/** Audible phone alarm scheduled by a user voice request on Galaxy Watch7.
 * If FGS launch is restricted, the receiver posts a system alarm notification.
 */
public final class JarvisWearAlertService extends Service {
    public static final String START="com.andriy.jarvis.WEAR_ALARM_ALERT";
    public static final String STOP="com.andriy.jarvis.WEAR_ALARM_STOP";
    private static final String CHANNEL="jarvis_wear_alarm_ring_v1";
    private static final int NOTICE=16991;
    private static final long MAX_RING_MS=60000L;
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private ToneGenerator fallback;
    private Vibrator vibrator;
    private Runnable beep,timeout;
    private String message;

    @Override public IBinder onBind(Intent i){return null;}

    private Notification build(String text) {
        NotificationManager nm=(NotificationManager)getSystemService(
            Context.NOTIFICATION_SERVICE);
        if(nm!=null) {
            NotificationChannel channel=new NotificationChannel(CHANNEL,
                "Джарвіс — дзвінок будильника",NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null,null); // Alarm audio is played separately.
            channel.enableVibration(false);
            nm.createNotificationChannel(channel);
        }
        Intent stop=new Intent(this,JarvisWearAlertService.class);
        stop.setAction(STOP);
        PendingIntent stopPi=PendingIntent.getService(this,16992,stop,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(this,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi=PendingIntent.getActivity(this,16993,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Будильник — Джарвіс")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_pause,"Вимкнути",stopPi)
            .build();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && STOP.equals(intent.getAction())) {
            stopSelf();return START_NOT_STICKY;
        }
        if(timeout!=null)return START_NOT_STICKY;
        message=intent==null?"Будильник Джарвіса":
            intent.getStringExtra("message");
        if(message==null || message.isEmpty())message="Будильник Джарвіса";
        try {
            Notification notice=build(message);
            if(Build.VERSION.SDK_INT>=29)
                startForeground(NOTICE,notice,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTICE,notice);
            ring();
            vibrate();
            timeout=this::stopSelf;
            main.postDelayed(timeout,MAX_RING_MS);
        }catch(RuntimeException error) {
            stopSelf(); // Caller falls back to the system notification.
        }
        return START_NOT_STICKY;
    }

    private void ring() {
        Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if(sound!=null) {
            MediaPlayer candidate=null;
            try {
                candidate=new MediaPlayer();
                candidate.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                candidate.setDataSource(this,sound);
                candidate.setLooping(true);
                candidate.prepare();
                candidate.start();
                player=candidate;
                return;
            }catch(Exception ignored) {
                if(candidate!=null)candidate.release();
                player=null;
            }
        }
        try {
            fallback=new ToneGenerator(AudioManager.STREAM_ALARM,95);
            beep=new Runnable(){
                @Override public void run(){
                    if(fallback==null)return;
                    fallback.startTone(ToneGenerator.TONE_PROP_BEEP2,800);
                    main.postDelayed(this,1300L);
                }
            };
            main.post(beep);
        }catch(RuntimeException ignored){}
    }

    private void vibrate() {
        vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
        if(vibrator==null || !vibrator.hasVibrator())return;
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(
                new long[]{0,900,250,900,500,1400,300},0));
        }catch(RuntimeException ignored){}
    }

    @Override public void onDestroy() {
        if(timeout!=null)main.removeCallbacks(timeout);
        if(beep!=null)main.removeCallbacks(beep);
        if(player!=null) {
            try{if(player.isPlaying())player.stop();}catch(RuntimeException ignored){}
            player.release();player=null;
        }
        if(fallback!=null){fallback.release();fallback=null;}
        if(vibrator!=null)vibrator.cancel();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
