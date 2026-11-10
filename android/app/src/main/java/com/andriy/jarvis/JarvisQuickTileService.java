package com.andriy.jarvis;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Dedicated Android Quick Settings shortcut: does NOT open MainActivity. */
public final class JarvisQuickTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(FloatingJarvisService.isShowing()
            ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("Джарвіс");
        tile.setContentDescription("Відкрити плаваюче обличчя Джарвіса");
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_launcher));
        tile.updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        android.util.Log.i("JARVIS_FLOATING_QA", "tile_clicked=true");
        if (isLocked()) {
            unlockAndRun(this::openBubble);
        } else {
            openBubble();
        }
    }

    private void openBubble() {
        android.util.Log.i("JARVIS_FLOATING_QA", "tile_open_requested=true");
        Intent intent = new Intent(this, JarvisBubbleLauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pending = PendingIntent.getActivity(this, 612,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pending);
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
