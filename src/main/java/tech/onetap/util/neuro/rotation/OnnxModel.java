package tech.onetap.util.neuro.rotation;

import ai.onnxruntime.*;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

public class OnnxModel {

    private OrtSession session;
    private OrtEnvironment env;
    private final int inputSize;
    private final int outputSize;

    public OnnxModel(String path, int inputSize, int outputSize) {
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        try {
            env = OrtEnvironment.getEnvironment();
            var opts = new OrtSession.SessionOptions();
            session = env.createSession(path, opts);
        } catch (OrtException e) {
            throw new RuntimeException("Failed to load ONNX model: " + path, e);
        }
    }

    public float[] predict(float[] input) {
        if (input.length != inputSize) throw new IllegalArgumentException("Expected " + inputSize + " inputs, got " + input.length);
        try {
            long[] shape = {1, inputSize};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape);
            OrtSession.Result result = session.run(Collections.singletonMap("input", inputTensor));
            OnnxTensor outputTensor = (OnnxTensor) result.get(0);
            float[] output = outputTensor.getFloatBuffer().array();
            result.close();
            return output;
        } catch (OrtException e) {
            throw new RuntimeException("ONNX inference failed", e);
        }
    }

    public void close() {
        try { if (session != null) session.close(); } catch (OrtException e) { e.printStackTrace(); }
    }
}
