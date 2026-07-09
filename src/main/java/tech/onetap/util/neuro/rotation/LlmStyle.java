package tech.onetap.util.neuro.rotation;

public class LlmStyle {
    public final float springStrength;   // 0.05 - 0.50
    public final float noiseScale;       // 0.0 - 1.0
    public final float speedScale;       // 0.5 - 2.0
    public final String description;

    public static final LlmStyle DEFAULT = new LlmStyle(0.20f, 0.3f, 1.0f, "default");

    public LlmStyle(float springStrength, float noiseScale, float speedScale, String description) {
        this.springStrength = springStrength;
        this.noiseScale = noiseScale;
        this.speedScale = speedScale;
        this.description = description;
    }
}
