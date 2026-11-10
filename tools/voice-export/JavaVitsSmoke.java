import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Verify Java ORT can run the actual Dmytro/Lada ONNX graph, not only Python. */
public final class JavaVitsSmoke {
    private static long[] ints(Path path) throws Exception {
        String[] fields = Files.readString(path).trim().split(",");
        long[] a = new long[fields.length];
        for (int i = 0; i < a.length; i++) a[i] = Long.parseLong(fields[i]);
        return a;
    }
    private static float[] floats(Path path) throws Exception {
        String[] fields = Files.readString(path).trim().split(",");
        float[] a = new float[fields.length];
        for (int i = 0; i < a.length; i++) a[i] = Float.parseFloat(fields[i]);
        return a;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("model.onnx test-directory");
        Path root = Path.of(args[1]);
        long[] tokens = ints(root.resolve("tokens.csv"));
        if (tokens.length < 3) throw new IllegalStateException("No real tokens");
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            long init = System.nanoTime();
            try (OrtSession graph = env.createSession(args[0], options)) {
                System.out.println("Session init ms: " + ((System.nanoTime()-init)/1000000));
                System.out.println("Graph inputs: " + graph.getInputNames());
                System.out.println("Graph outputs: " + graph.getOutputNames());
                for (String speaker : new String[]{"dmytro", "lada"}) {
                    float[] vector = floats(root.resolve(speaker + ".csv"));
                    if (vector.length != 192) throw new IllegalStateException("Wrong real speaker embedding");
                    long start = System.nanoTime();
                    try (OnnxTensor txt = OnnxTensor.createTensor(env, tokens);
                         OnnxTensor emb = OnnxTensor.createTensor(env, vector)) {
                        Map<String, OnnxTensor> in = new LinkedHashMap<>();
                        in.put("text", txt);
                        in.put("spembs", emb);
                        try (OrtSession.Result out = graph.run(in)) {
                            Object wave = out.get("wav").orElseThrow().getValue();
                            if (!(wave instanceof float[]))
                                throw new IllegalStateException("Unexpected WAV tensor: " + wave.getClass());
                            float[] samples = (float[]) wave;
                            if (samples.length < 1000 || samples.length > 500000)
                                throw new IllegalStateException("Wrong WAV length: " + samples.length);
                            float peak=0;
                            for (float x : samples) {
                                if (!Float.isFinite(x)) throw new IllegalStateException("Nonfinite audio");
                                peak = Math.max(peak, Math.abs(x));
                            }
                            if (peak < 0.0001f) throw new IllegalStateException("Silent voice");
                            System.out.printf("%s JAVA ONNX OK: samples=%d peak=%.3f elapsedMs=%d%n",
                                speaker, samples.length, peak, (System.nanoTime()-start)/1000000);
                        }
                    }
                }
            }
        }
    }
}
