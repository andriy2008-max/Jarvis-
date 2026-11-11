package com.andriy.jarvis;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Invoked by Android only when the user explicitly requests the chosen
 * default assistant (e.g. holding the Pixel power button).
 *
 * This is an ACTION_ASSIST activity, not an always-listening hotword service.
 * It hands control to the already permission-gated floating microphone flow.
 */
public final class JarvisAssistActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent received = getIntent();
        if (received == null || !Intent.ACTION_ASSIST.equals(received.getAction())) {
            finish();
            return;
        }

        // Do not let unrelated third-party ACTION_ASSIST broadcasts or
        // activities force a microphone session unless the user chose Jarvis.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = getSystemService(RoleManager.class);
            if (roles == null || !roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                finish();
                return;
            }
        }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard != null && keyguard.isKeyguardLocked()) {
            Toast.makeText(this, "Розблокуй телефон для голосової команди Джарвіса.",
                Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            Intent open = new Intent(this, JarvisBubbleLauncherActivity.class);
            open.putExtra(JarvisBubbleLauncherActivity.EXTRA_AUTO_LISTEN, true);
            open.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(open);
        } catch (RuntimeException error) {
            Toast.makeText(this,
                "Не вдалося запустити Джарвіса. Перевір доступ до мікрофона.",
                Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
