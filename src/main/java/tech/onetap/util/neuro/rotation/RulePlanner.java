package tech.onetap.util.neuro.rotation;

import java.util.concurrent.CompletableFuture;

public class RulePlanner {

    private boolean ready = true;

    public boolean isReady() { return ready; }
    public String getCurrentBackend() { return "rules"; }

    public void setup() {
        ready = true;
    }

    public CompletableFuture<LlmStyle> analyzeGameState(float dist, float tSpd, float yawErr, String targetType, int samples) {
        return CompletableFuture.completedFuture(calcStyle(dist, tSpd, yawErr, samples));
    }

    private LlmStyle calcStyle(float dist, float tSpd, float yawErr, int samples) {
        float spring = 0.20f;
        if (dist > 10) spring = 0.12f;
        else if (dist > 5) spring = 0.18f;
        else if (dist < 2) spring = 0.28f;
        if (Math.abs(yawErr) > 8) spring += 0.08f;
        if (Math.abs(yawErr) < 1) spring -= 0.04f;
        if (tSpd > 0.3) spring += 0.05f;
        if (samples < 50) spring *= 0.7f;
        spring = clamp(spring, 0.05f, 0.50f);

        float noise = 0.15f;
        if (dist > 10 || Math.abs(yawErr) > 5) noise = 0.05f;
        else if (dist < 3 && Math.abs(yawErr) < 2) noise = 0.25f;
        if (samples > 200) noise *= 0.6f;
        noise = clamp(noise, 0f, 0.5f);

        float speed = 0.5f;
        if (dist < 3 || Math.abs(yawErr) > 8) speed = 0.8f;
        else if (dist > 15) speed = 0.3f;
        if (tSpd > 0.4) speed += 0.2f;
        speed = clamp(speed, 0.2f, 1.5f);

        String desc;
        if (spring > 0.3) desc = "агрессивно";
        else if (spring < 0.15) desc = "плавно";
        else desc = "сбалансировано";

        return new LlmStyle(spring, noise, speed, desc);
    }

    private float clamp(double v, double min, double max) {
        return (float) Math.max(min, Math.min(max, v));
    }

    public void shutdown() {
        ready = false;
    }
}
