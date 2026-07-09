package tech.onetap.util.neuro.rotation;

import lombok.Getter;
import java.io.Serializable;

@Getter
public class NeuroSequence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final float[] yawErrors;
    private final float[] pitchErrors;
    private final double[] distances;
    private final double[] targetSpeeds;
    private final double[] playerSpeeds;
    private final double avgDistance;
    private final double avgTargetSpeed;
    private final double avgPlayerSpeed;
    private final long timestamp;
    private final String targetType;
    private final int length;

    private final float[] playerDeltasYaw;
    private final float[] playerDeltasPitch;

    private final double[] playerPosX;
    private final double[] playerPosY;
    private final double[] playerPosZ;
    private final double[] targetPosX;
    private final double[] targetPosY;
    private final double[] targetPosZ;

    public NeuroSequence(float[] yawErrors, float[] pitchErrors,
                         double[] distances, double[] targetSpeeds, double[] playerSpeeds,
                         double avgDistance, double avgTargetSpeed,
                         double avgPlayerSpeed, String targetType,
                         float[] playerDeltasYaw, float[] playerDeltasPitch,
                         double[] playerPosX, double[] playerPosY, double[] playerPosZ,
                         double[] targetPosX, double[] targetPosY, double[] targetPosZ) {
        this.yawErrors = yawErrors;
        this.pitchErrors = pitchErrors;
        this.distances = distances;
        this.targetSpeeds = targetSpeeds;
        this.playerSpeeds = playerSpeeds;
        this.avgDistance = avgDistance;
        this.avgTargetSpeed = avgTargetSpeed;
        this.avgPlayerSpeed = avgPlayerSpeed;
        this.timestamp = System.currentTimeMillis();
        this.targetType = targetType;
        this.length = yawErrors.length;
        this.playerDeltasYaw = playerDeltasYaw;
        this.playerDeltasPitch = playerDeltasPitch;
        this.playerPosX = playerPosX;
        this.playerPosY = playerPosY;
        this.playerPosZ = playerPosZ;
        this.targetPosX = targetPosX;
        this.targetPosY = targetPosY;
        this.targetPosZ = targetPosZ;
    }
}
