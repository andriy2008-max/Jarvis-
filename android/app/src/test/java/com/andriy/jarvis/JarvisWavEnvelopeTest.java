package com.andriy.jarvis;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class JarvisWavEnvelopeTest {
    private static void le16(FileOutputStream out, int value) throws IOException {
        out.write(value & 255);
        out.write((value >>> 8) & 255);
    }

    private static void le32(FileOutputStream out, int value) throws IOException {
        le16(out, value);
        le16(out, value >>> 16);
    }

    public static void main(String[] args) throws Exception {
        File wav = File.createTempFile("jarvis-mouth-test-", ".wav");
        try {
            int sampleRate = 8000, samples = 1600, dataBytes = samples * 2;
            try (FileOutputStream out = new FileOutputStream(wav)) {
                out.write("RIFF".getBytes("US-ASCII"));
                le32(out, 36 + dataBytes);
                out.write("WAVEfmt ".getBytes("US-ASCII"));
                le32(out, 16);
                le16(out, 1);  // PCM
                le16(out, 1);  // Mono
                le32(out, sampleRate);
                le32(out, sampleRate * 2);
                le16(out, 2);
                le16(out, 16);
                out.write("data".getBytes("US-ASCII"));
                le32(out, dataBytes);
                for (int i = 0; i < samples; i++) {
                    int value = (i >= 800 && i < 1200)
                        ? (i % 16 < 8 ? 12000 : -12000) : 0;
                    le16(out, value);
                }
            }
            JarvisWavEnvelope envelope = JarvisWavEnvelope.fromFile(wav);
            if (envelope == null)
                throw new AssertionError("Must decode valid 16-bit PCM WAV");
            if (envelope.mouthAt(10L) != 0 || envelope.mouthAt(45L) != 0 ||
                envelope.mouthAt(110L) < 2 || envelope.mouthAt(175L) != 0 ||
                envelope.mouthAt(200L) != 0)
                throw new AssertionError("Mouth did not follow actual WAV sound/silence");
        } finally {
            wav.delete();
        }
        File bad = File.createTempFile("jarvis-mouth-invalid-", ".wav");
        try {
            if (JarvisWavEnvelope.fromFile(bad) != null)
                throw new AssertionError("Invalid sound must not animate");
        } finally { bad.delete(); }
        System.out.println("PASS JarvisWavEnvelopeTest");
    }
}
