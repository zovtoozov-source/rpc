package tech.onetap.util.neuro.rotation;

import java.io.*;
import java.util.Random;
import java.util.function.Consumer;

public class NeuralNet implements Serializable {

    private static final long serialVersionUID = 2L;

    public final int[] layers;
    public final float[][][] weights;
    public final float[][] biases;
    /** Standard deviation of residuals (pred - target) per output, set after training. Used for stochastic inference. */
    public float residualStdYaw = 0.05f;
    public float residualStdPitch = 0.05f;
    private transient float[] inputCache;
    private transient float[][] preAct;
    private transient float[][] act;

    public NeuralNet(int... layers) {
        this.layers = layers;
        int L = layers.length - 1;
        weights = new float[L][][];
        biases = new float[L][];
        Random rng = new Random(42);
        for (int l = 0; l < L; l++) {
            int in = layers[l], out = layers[l + 1];
            weights[l] = new float[out][in];
            biases[l] = new float[out];
            float scale = (float) Math.sqrt(2.0 / in);
            for (int i = 0; i < out; i++) {
                for (int j = 0; j < in; j++)
                    weights[l][i][j] = (rng.nextFloat() - 0.5f) * 2 * scale;
                biases[l][i] = 0;
            }
        }
    }

    public float[] forward(float[] input) {
        inputCache = input.clone();
        int L = weights.length;
        preAct = new float[L][];
        act = new float[L][];
        float[] cur = input;

        for (int l = 0; l < L; l++) {
            int out = layers[l + 1];
            float[] z = new float[out];
            for (int i = 0; i < out; i++) {
                float sum = biases[l][i];
                for (int j = 0; j < cur.length; j++)
                    sum += weights[l][i][j] * cur[j];
                z[i] = sum;
            }
            preAct[l] = z;
            boolean isLast = (l == L - 1);
            float[] a = new float[out];
            for (int i = 0; i < out; i++)
                a[i] = isLast ? z[i] : tanh(z[i]);
            act[l] = a;
            cur = a;
        }
        return cur;
    }

    public float[] predict(float[] input) {
        return forward(input);
    }

    private transient float[][][] velocityW;
    private transient float[][] velocityB;

    public void train(float[][] inputs, float[][] targets, float lr, int epochs) {
        int L = weights.length;
        velocityW = new float[L][][];
        velocityB = new float[L][];
        for (int l = 0; l < L; l++) {
            velocityW[l] = new float[weights[l].length][weights[l][0].length];
            velocityB[l] = new float[biases[l].length];
        }
        float momentum = 0.9f;
        int n = inputs.length;
        for (int ep = 0; ep < epochs; ep++) {
            float loss = 0;
            for (int s = 0; s < n; s++) {
                float[] out = forward(inputs[s]);
                float[] grad = new float[out.length];
                for (int i = 0; i < out.length; i++) {
                    float diff = out[i] - targets[s][i];
                    loss += diff * diff;
                    grad[i] = diff;
                }
                backwardMomentum(grad, lr, momentum);
            }
            loss = (float) Math.sqrt(loss / n);
            if (ep % 25 == 0 || ep == epochs - 1)
                System.out.println("[Neuro] ep " + ep + " RMSE=" + String.format("%.4f", loss));
        }
        velocityW = null;
        velocityB = null;
    }

    private void backwardMomentum(float[] gradOutput, float lr, float momentum) {
        int L = weights.length;
        float[] grad = gradOutput;
        for (int l = L - 1; l >= 0; l--) {
            float[] z = preAct[l];
            float[] a = act[l];
            float[] delta = new float[grad.length];
            boolean isLast = (l == L - 1);
            for (int i = 0; i < grad.length; i++) {
                float dz = isLast ? 1f : tanhDeriv(z[i]);
                delta[i] = grad[i] * dz;
            }
            float[] inp = (l == 0) ? inputCache : act[l - 1];
            for (int i = 0; i < weights[l].length; i++) {
                for (int j = 0; j < inp.length; j++) {
                    velocityW[l][i][j] = momentum * velocityW[l][i][j] - lr * delta[i] * inp[j];
                    weights[l][i][j] += velocityW[l][i][j];
                }
                velocityB[l][i] = momentum * velocityB[l][i] - lr * delta[i];
                biases[l][i] += velocityB[l][i];
            }
            if (l > 0) {
                float[] nextGrad = new float[layers[l]];
                for (int j = 0; j < layers[l]; j++) {
                    float sum = 0;
                    for (int i = 0; i < weights[l].length; i++)
                        sum += weights[l][i][j] * delta[i];
                    nextGrad[j] = sum;
                }
                grad = nextGrad;
            }
        }
    }

    public void trainWithDecay(float[][] inputs, float[][] targets, float lr, int epochs, float decayFactor) {
        trainWithDecay(inputs, targets, lr, epochs, decayFactor, null);
    }

    public void trainWithDecay(float[][] inputs, float[][] targets, float lr, int epochs, float decayFactor, Consumer<Float> onProgress) {
        int L = weights.length;
        velocityW = new float[L][][];
        velocityB = new float[L][];
        for (int l = 0; l < L; l++) {
            velocityW[l] = new float[weights[l].length][weights[l][0].length];
            velocityB[l] = new float[biases[l].length];
        }
        float momentum = 0.9f;
        int n = inputs.length;
        int phaseSize = epochs / 3;
        float currentLr = lr;
        for (int ep = 0; ep < epochs; ep++) {
            if (ep > 0 && ep % phaseSize == 0) currentLr *= decayFactor;
            float loss = 0;
            for (int s = 0; s < n; s++) {
                float[] out = forward(inputs[s]);
                float[] grad = new float[out.length];
                for (int i = 0; i < out.length; i++) {
                    float diff = out[i] - targets[s][i];
                    loss += diff * diff;
                    grad[i] = diff;
                }
                backwardMomentum(grad, currentLr, momentum);
            }
            loss = (float) Math.sqrt(loss / n);
            if (ep % 50 == 0 || ep == epochs - 1 || ep == 0) {
                System.out.println("[Neuro] ep " + ep + " lr=" + String.format("%.5f", currentLr) + " RMSE=" + String.format("%.4f", loss));
                if (onProgress != null) onProgress.accept((float) ep / epochs);
            }
        }
        velocityW = null;
        velocityB = null;
    }

    public void computeResidualStd(float[][] inputs, float[][] targets) {
        int n = inputs.length;
        double sumDY = 0, sumDP = 0, sumDY2 = 0, sumDP2 = 0;
        for (int s = 0; s < n; s++) {
            float[] out = predict(inputs[s]);
            float dy = out[0] - targets[s][0];
            float dp = out[1] - targets[s][1];
            sumDY += dy;
            sumDP += dp;
            sumDY2 += dy * dy;
            sumDP2 += dp * dp;
        }
        double meanDY = sumDY / n;
        double meanDP = sumDP / n;
        residualStdYaw = (float) Math.sqrt(Math.max(1e-6, sumDY2 / n - meanDY * meanDY));
        residualStdPitch = (float) Math.sqrt(Math.max(1e-6, sumDP2 / n - meanDP * meanDP));
        System.out.println("[Neuro] residual std: yaw=" + String.format("%.4f", residualStdYaw) + " pitch=" + String.format("%.4f", residualStdPitch) + " samples=" + n);
    }

    private float tanh(float x) {
        if (x > 20f) return 1f;
        if (x < -20f) return -1f;
        float e2x = (float) Math.exp(2 * x);
        return (e2x - 1) / (e2x + 1);
    }

    private float tanhDeriv(float x) {
        float t = tanh(x);
        return 1f - t * t;
    }

    public void save(String path) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static NeuralNet load(String path) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            return (NeuralNet) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
}
