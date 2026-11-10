package com.andriy.jarvis;

import static org.junit.Assert.*;

import android.content.Context;
import android.media.MediaPlayer;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Real-device / emulator integration test. Uses the genuine 145MB Ukrainian TTS v6
 * VITS graph, not mocked tensors or Android system TTS. Network is disabled by CI
 * before this test begins. Never ships in the user-facing APK.
 */
@RunWith(AndroidJUnit4.class)
public final class OfflineVoicesInstrumentedTest {

    private static void copy(File from, File to) throws Exception {
        if (!from.isFile()) throw new Exception("Missing ONNX fixture: " + from);
        File dir = to.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new Exception("mkdir failed: " + dir);
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] bytes = new byte[256 * 1024];
            int n;
            while ((n = in.read(bytes)) >= 0) {
                if (n != 0) out.write(bytes, 0, n);
            }
        }
    }

    private static byte[] digest(File file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int count;
            while ((count = in.read(buf)) != -1) sha.update(buf, 0, count);
        }
        return sha.digest();
    }

    private static void verifyManifest(File directory) throws Exception {
        Method verify = OfflineVoicePackManager.class.getDeclaredMethod("verifyPack", File.class, int.class);
        verify.setAccessible(true);
        try {
            verify.invoke(null, directory, 1);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Manifest and model checksums failed", e.getCause());
        }
    }

    private static void checkWave(File file, String voice) throws Exception {
        assertTrue(voice + " missing WAV", file.isFile());
        assertTrue(voice + " empty WAV", file.length() > 3000);
        try (InputStream in = new FileInputStream(file)) {
            byte[] hdr = new byte[44];
            assertEquals(44, in.read(hdr));
            assertEquals("RIFF", new String(hdr, 0, 4, "US-ASCII"));
            assertEquals("WAVE", new String(hdr, 8, 4, "US-ASCII"));
            assertEquals(1, hdr[20] & 0xff); // little-endian PCM format
            assertEquals(0, hdr[21] & 0xff);
            int hz = (hdr[24] & 0xff) | ((hdr[25] & 0xff) << 8) |
                ((hdr[26] & 0xff) << 16) | ((hdr[27] & 0xff) << 24);
            assertEquals(22050, hz);
            long sum = 0L;
            int samples = 0;
            byte[] block = new byte[8192];
            int read;
            while ((read = in.read(block)) > 0) {
                int even = read - (read % 2);
                for (int i = 0; i < even; i += 2) {
                    short value = (short) ((block[i] & 255) | ((block[i + 1] & 255) << 8));
                    sum += Math.abs((int) value);
                    samples++;
                }
            }
            assertTrue(voice + " appears silent", sum > samples * 30L);
        }
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            assertTrue("Android cannot decode generated WAV", player.getDuration() > 0);
            player.start();
            player.stop();
        } finally {
            player.release();
        }
    }

    @Test
    public void bothVoicesReallySpeakWithAirplaneModeAndSurviveReload() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("Emulator must disable network before offline test", 1,
            Settings.Global.getInt(app.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0));
        // Rooted CI emulator stages a signed model in test-only app-private storage.
        // Unlike /sdcard/Android/data, this directory is readable under scoped storage.
        File fixture = new File(app.getNoBackupFilesDir(), "voice-fixture");
        File installed = new File(app.getNoBackupFilesDir(), "jarvis-offline-voices/current");
        copy(new File(fixture, "manifest.json"), new File(installed, "manifest.json"));
        copy(new File(fixture, "onnx/full/vits.onnx"), new File(installed, "onnx/full/vits.onnx"));

        // Check the *production* pack validator against actual on-device binary bytes.
        verifyManifest(installed);
        OfflineVoicePackManager pack = new OfflineVoicePackManager(app);
        assertTrue("Model not recognized by production package manager", pack.hasInstalledFiles());
        byte[] dmytroHash;
        byte[] ladaHash;
        long start = android.os.SystemClock.elapsedRealtime();
        try (OfflineOnnxSynthesizer engine = new OfflineOnnxSynthesizer(installed)) {
            long loaded = android.os.SystemClock.elapsedRealtime();
            android.util.Log.i("JARVIS_OFFLINE_TEST", "model_load_ms=" + (loaded-start));
            android.util.Log.i("JARVIS_OFFLINE_TEST", "model_loaded_pss_kb=" + android.os.Debug.getPss());
            File dmytro = engine.synthesize("Привіт, це Дмитро.", "dmytro",
                app.getCacheDir(), () -> {});
            try {
                checkWave(dmytro, "dmytro");
                dmytroHash = digest(dmytro);
                android.util.Log.i("JARVIS_OFFLINE_TEST", "dmytro_wav_bytes=" + dmytro.length());
            } finally { dmytro.delete(); }

            File lada = engine.synthesize("Привіт, це Дмитро.", "lada",
                app.getCacheDir(), () -> {});
            try {
                checkWave(lada, "lada");
                ladaHash = digest(lada);
                android.util.Log.i("JARVIS_OFFLINE_TEST", "lada_wav_bytes=" + lada.length());
            } finally { lada.delete(); }
        }
        assertFalse("Speakers produced identical files", Arrays.equals(dmytroHash, ladaHash));
        assertTrue("ONNX model files not persisted", pack.hasInstalledFiles());
        try (OfflineOnnxSynthesizer reloaded = new OfflineOnnxSynthesizer(pack.installedDirectory())) {
            File wav = reloaded.synthesize("Тест офлайн.", "dmytro",
                app.getCacheDir(), () -> {});
            try { checkWave(wav, "reload"); } finally { wav.delete(); }
        }
        android.util.Log.i("JARVIS_OFFLINE_TEST", "after_both_voices_pss_kb=" + android.os.Debug.getPss());
        pack.close();
        android.util.Log.i("JARVIS_OFFLINE_TEST", "both_voices_airplane_mode_passed");
    }

    @Test
    public void offlineVoiceDataSurvivesInPlaceAppUpdate() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("Update check must remain offline", 1,
            Settings.Global.getInt(app.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0));
        OfflineVoicePackManager pack = new OfflineVoicePackManager(app);
        try {
            assertTrue("App update erased private offline voice package", pack.hasInstalledFiles());
            File dir = pack.installedDirectory();
            assertTrue("Model disappeared on app update", new File(dir, "onnx/full/vits.onnx").isFile());
            try (OfflineOnnxSynthesizer engine = new OfflineOnnxSynthesizer(dir)) {
                File wav = engine.synthesize("Лада працює без інтернету.", "lada",
                    app.getCacheDir(), () -> {});
                try {
                    checkWave(wav, "after_update");
                } finally {
                    wav.delete();
                }
            }
        } finally {
            pack.close();
        }
        android.util.Log.i("JARVIS_OFFLINE_TEST", "in_place_upgrade_preserved_voice_and_synthesis");
    }
}
