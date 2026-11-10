package com.andriy.jarvis;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;

/**
 * 40 ms PCM16 energy frames from the actual synthesized WAV. Read before
 * playback, then index by MediaPlayer.getCurrentPosition for lip sync.
 * No microphone data is read or stored.
 */
public final class JarvisWavEnvelope {
    private static final int FRAME_MS = 40;
    private final byte[] levels;

    private JarvisWavEnvelope(byte[] levels) { this.levels = levels; }

    public int mouthAt(long playbackMs) {
        if (playbackMs < 0 || playbackMs / FRAME_MS >= levels.length) return 0;
        return levels[(int) (playbackMs / FRAME_MS)];
    }

    public static JarvisWavEnvelope fromFile(File file) {
        if (file == null || !file.isFile() || file.length() > 24L * 1024 * 1024)
            return null;
        try (RandomAccessFile wav = new RandomAccessFile(file, "r")) {
            if (wav.length() < 44 || readFour(wav).equals("RIFF") == false) return null;
            readLE32(wav); // RIFF byte size
            if (!readFour(wav).equals("WAVE")) return null;
            int sampleRate = 0, channels = 0, bits = 0, format = 0;
            long dataStart = -1, dataLength = 0;
            while (wav.getFilePointer() + 8 <= wav.length()) {
                String id = readFour(wav);
                long size = readLE32(wav) & 0xffffffffL;
                long start = wav.getFilePointer();
                if (size < 0 || start + size > wav.length()) return null;
                if ("fmt ".equals(id) && size >= 16) {
                    format = readLE16(wav);
                    channels = readLE16(wav);
                    sampleRate = readLE32(wav);
                    readLE32(wav);
                    readLE16(wav);
                    bits = readLE16(wav);
                } else if ("data".equals(id)) {
                    dataStart = start;
                    dataLength = size;
                }
                long next = start + size + (size & 1);
                if (next > wav.length()) break;
                wav.seek(next);
            }
            if (format != 1 || bits != 16 || channels < 1 || channels > 2 ||
                sampleRate < 8000 || sampleRate > 96000 || dataStart < 0)
                return null;
            long bytesPerFrame = channels * 2L;
            int samplesPerBucket = Math.max(1, sampleRate * FRAME_MS / 1000);
            long totalFrames = dataLength / bytesPerFrame;
            int buckets = (int) Math.min(5000L, (totalFrames + samplesPerBucket - 1) / samplesPerBucket);
            if (buckets == 0) return null;
            float[] peaks = new float[buckets];
            wav.seek(dataStart);
            for (long index = 0; index < totalFrames && index / samplesPerBucket < buckets; index++) {
                int amplitude = 0;
                for (int ch = 0; ch < channels; ch++)
                    amplitude = Math.max(amplitude, Math.abs((short) readLE16(wav)));
                int bucket = (int) (index / samplesPerBucket);
                peaks[bucket] = Math.max(peaks[bucket], amplitude);
            }
            float loudest = 0;
            for (float peak : peaks) loudest = Math.max(peak, loudest);
            byte[] levels = new byte[buckets];
            float floor = Math.max(120f, loudest * .045f);
            for (int i = 0; i < buckets; i++) {
                float v = peaks[i];
                levels[i] = (byte) (v < floor ? 0 :
                    v < loudest * .20f ? 1 : v < loudest * .53f ? 2 : 3);
            }
            return new JarvisWavEnvelope(levels);
        } catch (Exception invalidWav) {
            return null;
        }
    }

    private static String readFour(RandomAccessFile file) throws Exception {
        byte[] chars = new byte[4];
        file.readFully(chars);
        return new String(chars, java.nio.charset.StandardCharsets.US_ASCII);
    }
    private static int readLE16(RandomAccessFile f) throws Exception {
        int lo = f.readUnsignedByte(), hi = f.readUnsignedByte();
        return lo | (hi << 8);
    }
    private static int readLE32(RandomAccessFile f) throws Exception {
        return readLE16(f) | (readLE16(f) << 16);
    }
}
