package com.andriy.jarvis;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Local inference for ESPnet VITS (Ukrainian TTS v6.0.0).
 *
 * Supports an ESPnet VITS single-graph model and legacy Tacotron2 subgraphs.
 * The real upstream v6 checkpoint exports VITS despite an old source config.
 *
 * This implementation needs an ACTUALLY EXPORTED + TESTED v6 ONNX model
 * pack; merely compiling this class does not prove real audio performance.
 */
public final class OfflineOnnxSynthesizer implements AutoCloseable {
    public interface CancelCheck { void check() throws Exception; }

    private static final int MAX_CHARS_PER_CHUNK = 180;
    private static final int MAX_CHUNKS = 12;
    private static final int MAX_SAMPLES = 3_500_000;
    private final File modelDir;
    private final JSONObject manifest;
    private final JSONObject model;
    private final String modelType;
    private final OrtEnvironment environment;
    private final OrtSession.SessionOptions sessionOptions;
    private final Map<String, OrtSession> sessions = new HashMap<>();
    private final Map<String, Integer> tokenMap = new HashMap<>();
    private final Map<String, float[]> speakers = new HashMap<>();
    private final int sampleRate;
    private final int odim;
    private final int dlayers;
    private final int dunits;
    private final int factor;
    private final float threshold;
    private final double minlenratio;
    private final double maxlenratio;
    private final boolean cumulate;
    private final boolean normMeans;
    private final boolean normVars;
    private final float[] means;
    private final float[] stds;
    private volatile boolean closed;

    public OfflineOnnxSynthesizer(File directory) throws Exception {
        modelDir = directory;
        File manifestFile = new File(directory, "manifest.json");
        if (!manifestFile.isFile()) throw new Exception("Офлайн-модель не встановлена.");
        byte[] bytes = new byte[(int) manifestFile.length()];
        try (InputStream in = new FileInputStream(manifestFile)) {
            int offset = 0, n;
            while (offset < bytes.length && (n = in.read(bytes, offset, bytes.length - offset)) > 0)
                offset += n;
            if (offset != bytes.length) throw new Exception("Маніфест неповний.");
        }
        manifest = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        if (!OfflineVoicePackRules.FORMAT.equals(manifest.optString("format")) ||
            !manifest.optBoolean("smoke_verified"))
            throw new Exception("Потрібен перевірений пакет ONNX Дмитра й Лади.");
        model = manifest.getJSONObject("synthesis");
        modelType = model.optString("model_type");
        sampleRate = manifest.getInt("sample_rate");
        if (sampleRate < 16000 || sampleRate > 48000)
            throw new Exception("Непідтримувана частота звуку.");
        if ("VITS".equals(modelType)) {
            if (!"onnx/full/vits.onnx".equals(model.optString("graph")))
                throw new Exception("Неправильний шлях до VITS.");
            odim = 0;
            dlayers = 0;
            dunits = 0;
            factor = 0;
            threshold = 0;
            maxlenratio = 0;
            minlenratio = 0;
            cumulate = false;
            normMeans = false;
            normVars = false;
            means = new float[0];
            stds = new float[0];
        } else if ("Tacotron2".equals(modelType)) {
            odim = model.getInt("odim");
            dlayers = model.getInt("dlayers");
            dunits = model.getInt("dunits");
            factor = model.getInt("reduction_factor");
            threshold = (float) model.getDouble("threshold");
            maxlenratio = model.getDouble("maxlenratio");
            minlenratio = model.getDouble("minlenratio");
            cumulate = model.getBoolean("cumulate_att_w");
            normMeans = model.getBoolean("norm_means");
            normVars = model.getBoolean("norm_vars");
            if (factor != 1 || model.optBoolean("use_att_constraint") || dlayers < 1 || dlayers > 8 ||
                dunits < 64 || dunits > 2048 || odim < 32 || odim > 256 ||
                maxlenratio < 1.0 || maxlenratio > 20)
                throw new Exception("Непідтримувані параметри Tacotron2.");
            means = floats(model.getJSONArray("mean"));
            stds = floats(model.getJSONArray("std"));
            if (means.length != odim || stds.length != odim)
                throw new Exception("Неправильні параметри нормалізації.");
        } else {
            throw new Exception("Непідтримуваний тип моделі: " + modelType);
        }

        JSONArray vocab = manifest.getJSONArray("tokens");
        for (int i = 0; i < vocab.length(); i++) tokenMap.put(vocab.getString(i), i);
        if (!tokenMap.containsKey("<unk>")) throw new Exception("Відсутній <unk> токен.");
        JSONObject xvectors = manifest.getJSONObject("spembs");
        for (String speaker : new String[] {"dmytro", "lada"}) {
            float[] v = floats(xvectors.getJSONArray(speaker));
            if (v.length < 64 || v.length > 512) throw new Exception("Невірний вектор голосу.");
            speakers.put(speaker, v);
        }
        environment = OrtEnvironment.getEnvironment();
        sessionOptions = new OrtSession.SessionOptions();
        try {
            sessionOptions.setIntraOpNumThreads(2);
            sessionOptions.setInterOpNumThreads(1);
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            String[] graphFiles = "VITS".equals(modelType)
                ? new String[] {"vits.onnx"}
                : new String[] {
                    "tts_model_encoder.onnx", "predecoder_0.onnx",
                    "tts_model_decoder.onnx", "postdecoder.onnx", "HiFiGANVocoder.onnx"
                };
            for (String part : graphFiles) {
                File target = new File(modelDir, "onnx/full/" + part);
                if (!target.isFile()) throw new Exception("Не знайдено " + part + ".");
                sessions.put(part, environment.createSession(target.getAbsolutePath(), sessionOptions));
            }
        } catch (Exception error) {
            close();
            throw error;
        }
    }

    private static float[] floats(JSONArray data) throws Exception {
        float[] result = new float[data.length()];
        for (int i = 0; i < data.length(); i++) {
            float value = (float) data.getDouble(i);
            if (!Float.isFinite(value)) throw new Exception("Некоректне число у моделі.");
            result[i] = value;
        }
        return result;
    }

    /**
     * Converts a Ukrainian phrase to char-IDs. Manual stress marks are preserved.
     * Full dictionary stress and Ukrainian number inflection remain separate work.
     */
    private long[] tokenize(String raw) {
        String prepared = normalizeText(raw);
        ArrayList<Long> ids = new ArrayList<>();
        for (int offset = 0; offset < prepared.length();) {
            int cp = prepared.codePointAt(offset);
            offset += Character.charCount(cp);
            String symbol = cp == ' ' ? "<space>" : new String(Character.toChars(cp));
            Integer id = tokenMap.get(symbol);
            if (id == null) id = tokenMap.get("<unk>");
            ids.add(id.longValue());
        }
        long[] values = new long[ids.size()];
        for (int i = 0; i < values.length; i++) values[i] = ids.get(i);
        return values;
    }

    static String normalizeText(String text) {
        return UkrainianVoiceText.normalize(text);
    }

    private static ArrayList<String> chunks(String raw) throws Exception {
        ArrayList<String> chunks = new ArrayList<>();
        String normalized = normalizeText(raw);
        while (!normalized.isEmpty()) {
            if (chunks.size() >= MAX_CHUNKS)
                throw new Exception("Текст занадто довгий для офлайн-озвучення.");
            int end = Math.min(normalized.length(), MAX_CHARS_PER_CHUNK);
            if (end < normalized.length()) {
                int last = Math.max(normalized.lastIndexOf('.', end), normalized.lastIndexOf(' ', end));
                if (last >= end / 2) end = last + 1;
            }
            chunks.add(normalized.substring(0, end).trim());
            normalized = normalized.substring(end).trim();
        }
        return chunks;
    }

    public File synthesize(String text, String speaker, File cacheDir,
                           CancelCheck cancelled) throws Exception {
        if (closed) throw new Exception("Офлайн-рушій закрито.");
        if (!speakers.containsKey(speaker))
            throw new Exception("Непідтримуваний голос: " + speaker);
        if (text == null || text.trim().isEmpty())
            throw new Exception("Порожній текст.");
        ArrayList<float[]> waves = new ArrayList<>();
        int total = 0;
        for (String chunk : chunks(text)) {
            cancelled.check();
            float[] samples = synthesizeChunk(chunk, speakers.get(speaker), cancelled);
            total += samples.length;
            if (total > MAX_SAMPLES)
                throw new Exception("Відповідь занадто довга для синтезу.");
            waves.add(samples);
        }
        cancelled.check();
        if (total < 100) throw new Exception("Модель не згенерувала звук.");
        File result = File.createTempFile("jarvis-offline-voice-", ".wav", cacheDir);
        boolean completed = false;
        try (FileOutputStream out = new FileOutputStream(result)) {
            writeWavHeader(out, total * 2, sampleRate);
            for (float[] wave : waves) {
                byte[] pcm = new byte[wave.length * 2];
                for (int i = 0; i < wave.length; i++) {
                    float bounded = Math.max(-1f, Math.min(1f, wave[i]));
                    short v = (short) (bounded * 32767);
                    pcm[2 * i] = (byte) (v & 255);
                    pcm[2 * i + 1] = (byte) ((v >>> 8) & 255);
                }
                out.write(pcm);
            }
            completed = true;
        } finally {
            if (!completed) result.delete();
        }
        return result;
    }

    private float[] synthesizeChunk(String phrase, float[] speaker,
                                    CancelCheck cancelled) throws Exception {
        long[] tokens = tokenize(phrase);
        if (tokens.length == 0 || tokens.length > MAX_CHARS_PER_CHUNK + 20)
            throw new Exception("Неприпустима довжина фрази.");

        Map<String, Object> enc = new LinkedHashMap<>();
        enc.put("text", tokens);
        enc.put("spembs", speaker);
        if ("VITS".equals(modelType)) {
            cancelled.check();
            // Official ESPnet VITS ONNX export yields 1D float32 PCM waveform.
            Object audio = run(sessions.get("vits.onnx"), enc, "wav")[0];
            float[] result = flatten(audio);
            if (result.length < 50 || result.length > MAX_SAMPLES)
                throw new Exception("VITS повернув неправильну кількість семплів.");
            for (float value : result)
                if (!Float.isFinite(value))
                    throw new Exception("VITS повернув некоректні аудіодані.");
            cancelled.check();
            return result;
        }
        float[][] h = (float[][]) run(sessions.get("tts_model_encoder.onnx"), enc, "h")[0];
        int length = h.length;
        if (length < 2 || length > MAX_CHARS_PER_CHUNK + 24)
            throw new Exception("Неправильний розмір виходу енкодера.");
        float[][][] encH = new float[][][] {h};
        float[][][] pceh = (float[][][]) run(
            sessions.get("predecoder_0.onnx"),
            oneInput("enc_h", encH), "pre_compute_enc_h")[0];

        float[][] mask = new float[1][length];
        float[][] att = new float[1][length];
        Arrays.fill(att[0], 1.0f / length);
        float[][] prevOut = new float[1][odim];
        float[][][] cstates = new float[dlayers][1][dunits];
        float[][][] zstates = new float[dlayers][1][dunits];
        ArrayList<float[]> melFrames = new ArrayList<>();

        int minSteps = Math.max(0, (int) Math.ceil(length * minlenratio));
        int maxSteps = Math.min(1500, Math.max(minSteps + 1,
            (int) Math.ceil(length * maxlenratio)));
        for (int step = 0; step < maxSteps; step++) {
            if ((step % 10) == 0) cancelled.check();
            Map<String, Object> args = new LinkedHashMap<>();
            for (int i = 0; i < dlayers; i++) {
                args.put("c_prev_" + i, cstates[i]);
                args.put("z_prev_" + i, zstates[i]);
            }
            args.put("a_prev", att);
            args.put("pceh", pceh);
            args.put("enc_h", encH);
            args.put("mask", mask);
            args.put("prev_in", prevOut);
            // Some ESPnet revisions export an optional attention constraint input.
            OrtSession decoder = sessions.get("tts_model_decoder.onnx");
            if (decoder.getInputNames().contains("last_att_mask"))
                args.put("last_att_mask", new float[length]);

            ArrayList<String> outputs = new ArrayList<>(Arrays.asList(
                "out", "prob", "prev_att_w", "prev_out"));
            for (int i = 0; i < dlayers; i++) outputs.add("c_list_" + i);
            for (int i = 0; i < dlayers; i++) outputs.add("z_list_" + i);
            Object[] decoded = run(decoder, args, outputs.toArray(new String[0]));
            float[][][] out = (float[][][]) decoded[0];
            float[] probs = flatten(decoded[1]);
            float[][] lastAtt = (float[][]) decoded[2];
            prevOut = (float[][]) decoded[3];

            float[] frame = new float[odim];
            for (int d = 0; d < odim; d++) frame[d] = out[0][d][0];
            melFrames.add(frame);
            if (cumulate) {
                for (int j = 0; j < length; j++) att[0][j] += lastAtt[0][j];
            } else {
                att = lastAtt;
            }
            for (int i = 0; i < dlayers; i++) {
                cstates[i] = (float[][]) decoded[4 + i];
                zstates[i] = (float[][]) decoded[4 + dlayers + i];
            }
            // Terminate at predicted stop token only after minimum duration.
            boolean stop = false;
            for (float probability : probs) if (probability >= threshold) stop = true;
            if (stop && melFrames.size() >= minSteps) break;
        }
        int frames = melFrames.size();
        float[][][] postnetInput = new float[1][odim][frames];
        for (int i = 0; i < frames; i++)
            for (int d = 0; d < odim; d++) postnetInput[0][d][i] = melFrames.get(i)[d];
        float[][] features = (float[][]) run(
            sessions.get("postdecoder.onnx"), oneInput("x", postnetInput), "out")[0];
        if (features.length != frames)
            throw new Exception("Неправильна кількість мел-кадрів.");
        for (int i = 0; i < frames; i++) {
            for (int d = 0; d < odim; d++) {
                if (normVars) features[i][d] *= stds[d];
                if (normMeans) features[i][d] += means[d];
            }
        }
        Object wav = run(sessions.get("HiFiGANVocoder.onnx"),
            oneInput("c", features), "wav")[0];
        float[] audio = flatten(wav);
        if (audio.length < 50 || audio.length > MAX_SAMPLES)
            throw new Exception("Неправильна довжина WAV.");
        for (float f : audio)
            if (!Float.isFinite(f)) throw new Exception("Модель повернула некоректний WAV.");
        return audio;
    }

    private static Map<String, Object> oneInput(String key, Object value) {
        Map<String, Object> single = new LinkedHashMap<>();
        single.put(key, value);
        return single;
    }

    private Object[] run(OrtSession session, Map<String, Object> values,
                         String... wanted) throws OrtException {
        Map<String, OnnxTensor> tensors = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                // The exporter can remove optional inputs that are unused by the graph.
                if (session.getInputNames().contains(entry.getKey()))
                    tensors.put(entry.getKey(), OnnxTensor.createTensor(environment, entry.getValue()));
            }
            try (OrtSession.Result result = session.run(tensors)) {
                Object[] outputs = new Object[wanted.length];
                for (int i = 0; i < wanted.length; i++) {
                    OnnxValue v = result.get(wanted[i]).orElse(null);
                    if (v == null) throw new OrtException("Відсутній ONNX вихід: " + wanted[i]);
                    outputs[i] = v.getValue();
                }
                return outputs;
            }
        } finally {
            for (OnnxTensor t : tensors.values()) t.close();
        }
    }

    private static float[] flatten(Object object) throws Exception {
        if (object instanceof float[]) return (float[]) object;
        if (object instanceof float[][]) {
            float[][] rows = (float[][]) object;
            int size = 0;
            for (float[] row : rows) size += row.length;
            float[] flat = new float[size];
            int offset = 0;
            for (float[] row : rows) {
                System.arraycopy(row, 0, flat, offset, row.length);
                offset += row.length;
            }
            return flat;
        }
        throw new Exception("Непідтримуваний тип звукового тензора.");
    }

    private static void writeWavHeader(FileOutputStream o, int bytes,
                                       int rate) throws Exception {
        java.io.DataOutputStream d = new java.io.DataOutputStream(o);
        d.writeBytes("RIFF");
        writeIntLe(d, 36 + bytes);
        d.writeBytes("WAVEfmt ");
        writeIntLe(d, 16);
        writeShortLe(d, 1);
        writeShortLe(d, 1);
        writeIntLe(d, rate);
        writeIntLe(d, rate * 2);
        writeShortLe(d, 2);
        writeShortLe(d, 16);
        d.writeBytes("data");
        writeIntLe(d, bytes);
    }

    private static void writeIntLe(java.io.DataOutputStream out, int value) throws Exception {
        out.writeInt(Integer.reverseBytes(value));
    }

    private static void writeShortLe(java.io.DataOutputStream out, int value) throws Exception {
        out.writeShort(Short.reverseBytes((short) value));
    }

    @Override public void close() {
        closed = true;
        for (OrtSession session : sessions.values())
            try { session.close(); } catch (Exception ignored) {}
        sessions.clear();
        try { sessionOptions.close(); } catch (Exception ignored) {}
    }
}
