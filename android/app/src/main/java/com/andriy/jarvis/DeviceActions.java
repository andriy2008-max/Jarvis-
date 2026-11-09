package com.andriy.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;

/** Android implementation of the explicitly requested device commands. */
public final class DeviceActions {
    public static final int REQUEST_CAMERA_FOR_TORCH = 102;
    private static final String LAST_VOLUME_KEY = "lastNonzeroMusicVolume";
    private final Activity activity;
    private final AudioManager audio;
    private final CameraManager cameras;
    private String torchCameraId;
    private boolean torchOn;
    private boolean pendingTorchOn;
    private boolean cameraPermissionPending;

    private final CameraManager.TorchCallback torchCallback = new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (cameraId != null && cameraId.equals(torchCameraId)) torchOn = enabled;
        }
        @Override public void onTorchModeUnavailable(String cameraId) {
            if (cameraId != null && cameraId.equals(torchCameraId)) torchOn = false;
        }
    };

    public DeviceActions(Activity activity) {
        this.activity = activity;
        audio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        cameras = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        if (cameras != null) {
            try {
                cameras.registerTorchCallback(torchCallback, new Handler(Looper.getMainLooper()));
            } catch (RuntimeException ignored) {
                // Some devices do not expose torch callbacks; actual commands still handle failures.
            }
        }
    }

    public void close() {
        if (cameras != null) {
            try { cameras.unregisterTorchCallback(torchCallback); }
            catch (RuntimeException ignored) { }
        }
    }

    public String perform(DeviceCommands.Action command) {
        if (command == null) return "Команда не розпізнана.";
        switch (command.kind) {
            case TORCH_ON: return setTorch(true);
            case TORCH_OFF: return setTorch(false);
            case MUSIC_PAUSE: return media(KeyEvent.KEYCODE_MEDIA_PAUSE, "паузу");
            case MUSIC_PLAY: return media(KeyEvent.KEYCODE_MEDIA_PLAY, "продовження");
            case MUSIC_NEXT: return media(KeyEvent.KEYCODE_MEDIA_NEXT, "наступний трек");
            case MUSIC_PREVIOUS: return media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "попередній трек");
            case VOLUME_PERCENT: return setVolume(command.percent);
            case VOLUME_MUTE: return mute();
            case VOLUME_UNMUTE: return unmute();
            case DISPLAY_SETTINGS:
                try {
                    activity.startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                    return "Відкриваю налаштування екрана.";
                } catch (RuntimeException failure) {
                    return "Android не дозволив відкрити налаштування екрана.";
                }
            default: return "Команда не підтримується на цьому пристрої.";
        }
    }

    private String media(int keyCode, String action) {
        if (audio == null) return "Аудіокерування недоступне на цьому пристрої.";
        try {
            // Both key transitions are required; the system routes them to the active player.
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
            return "Надіслав команду «" + action + "» активному медіапрогравачу. Якщо музика не змінилася, відкрий програвач.";
        } catch (RuntimeException denied) {
            return "Android не дозволив керувати музикою. Перевір, чи працює медіапрогравач.";
        }
    }

    private String setVolume(int percent) {
        if (percent < 0 || percent > 100) return "Назви гучність від 0 до 100 відсотків.";
        if (audio == null || audio.isVolumeFixed()) return "Медіагучність на цьому пристрої недоступна для зміни.";
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) return "Медіагучність на цьому пристрої недоступна для зміни.";
            int previous = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (percent == 0 && previous > 0) rememberVolume(previous);
            int target = Math.max(0, Math.min(max, Math.round(max * percent / 100f)));
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
            int now = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (now > 0) rememberVolume(now);
            return "Медіагучність: " + Math.round(now * 100f / max) + " відсотків.";
        } catch (RuntimeException denied) {
            return "Android не дозволив змінити медіагучність.";
        }
    }

    private String mute() {
        return setVolume(0);
    }

    private String unmute() {
        if (audio == null || audio.isVolumeFixed()) return "Медіагучність на цьому пристрої недоступна для зміни.";
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) return "Неможливо відновити медіагучність.";
            int saved = activity.getPreferences(Activity.MODE_PRIVATE).getInt(LAST_VOLUME_KEY, Math.max(1, max / 2));
            saved = Math.min(max, Math.max(1, saved));
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, saved, AudioManager.FLAG_SHOW_UI);
            return "Медіагучність відновлено: " + Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max) + " відсотків.";
        } catch (RuntimeException denied) {
            return "Android не дозволив відновити медіагучність.";
        }
    }

    private void rememberVolume(int value) {
        activity.getPreferences(Activity.MODE_PRIVATE).edit().putInt(LAST_VOLUME_KEY, value).apply();
    }

    private String setTorch(boolean enabled) {
        if (cameras == null || !activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            return "На цьому пристрої немає доступного ліхтарика.";
        }
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingTorchOn = enabled;
            cameraPermissionPending = true;
            activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_FOR_TORCH);
            return "Для ліхтарика потрібен дозвіл на камеру. Підтверди запит Android.";
        }
        return applyTorch(enabled);
    }

    public String onCameraPermissionResult(boolean granted) {
        if (!cameraPermissionPending) return null;
        cameraPermissionPending = false;
        if (!granted) return "Дозвіл на камеру не надано. Ліхтарик не перемикався.";
        return applyTorch(pendingTorchOn);
    }

    private String applyTorch(boolean enabled) {
        try {
            if (torchCameraId == null) torchCameraId = findTorchCamera();
            if (torchCameraId == null) return "На пристрої не знайдено камеру з ліхтариком.";
            cameras.setTorchMode(torchCameraId, enabled);
            torchOn = enabled;
            return enabled ? "Ліхтарик увімкнено." : "Ліхтарик вимкнено.";
        } catch (Exception failed) {
            return "Не вдалося змінити ліхтарик: камера може бути зайнята або заблокована Android.";
        }
    }

    private String findTorchCamera() throws Exception {
        String firstWithFlash = null;
        for (String id : cameras.getCameraIdList()) {
            CameraCharacteristics info = cameras.getCameraCharacteristics(id);
            if (!Boolean.TRUE.equals(info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) continue;
            if (firstWithFlash == null) firstWithFlash = id;
            if (Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(info.get(CameraCharacteristics.LENS_FACING))) return id;
        }
        return firstWithFlash;
    }
}
