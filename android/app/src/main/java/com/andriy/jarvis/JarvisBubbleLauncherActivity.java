package com.andriy.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Brief transparent permission trampoline, not the full Jarvis UI.
 * Starts microphone foreground service only while the activity is visible.
 */
public final class JarvisBubbleLauncherActivity extends Activity {
    /** Set only by an explicit user-triggered system assistant invocation. */
    public static final String EXTRA_AUTO_LISTEN = "com.andriy.jarvis.assist.AUTO_LISTEN";
    private static final int MICROPHONE_REQUEST = 621;
    private static final int OVERLAY_REQUEST = 622;
    private boolean requestingOverlay;
    private boolean started;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
            requestingOverlay = savedInstanceState.getBoolean("requestingOverlay", false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!started && !requestingOverlay) checkPermissionsAndOpen();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("requestingOverlay", requestingOverlay);
        super.onSaveInstanceState(state);
    }

    private void checkPermissionsAndOpen() {
        if (!Settings.canDrawOverlays(this)) {
            requestingOverlay = true;
            Toast.makeText(this, "Дозволь Джарвісу показувати маленьке кругле вікно поверх інших програм.",
                Toast.LENGTH_LONG).show();
            try {
                Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(permission, OVERLAY_REQUEST);
            } catch (Exception unavailable) {
                requestingOverlay = false;
                Toast.makeText(this, "Системні налаштування плаваючих вікон недоступні.",
                    Toast.LENGTH_LONG).show();
                finish();
            }
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                MICROPHONE_REQUEST);
            return;
        }
        openBubble();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != OVERLAY_REQUEST) return;
        requestingOverlay = false;
        if (Settings.canDrawOverlays(this)) checkPermissionsAndOpen();
        else {
            Toast.makeText(this, "Дозвіл плаваючого вікна не надано.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions,
                                                      int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != MICROPHONE_REQUEST) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            openBubble();
        else {
            Toast.makeText(this, "Потрібен дозвіл мікрофона для голосових команд.",
                Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void openBubble() {
        if (started) return;
        started = true;
        try {
            Intent show = new Intent(this, FloatingJarvisService.class);
            show.setAction(getIntent() != null &&
                getIntent().getBooleanExtra(EXTRA_AUTO_LISTEN,false)
                ? FloatingJarvisService.ACTION_LISTEN : FloatingJarvisService.ACTION_SHOW);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(show);
            else startService(show);
        } catch (RuntimeException failure) {
            started = false;
            Toast.makeText(this, "Android не дозволив відкрити плаваюче вікно.",
                Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
