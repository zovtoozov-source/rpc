package tech.onetap.util.neuro.rotation;

import lombok.Data;

import java.io.Serializable;

/**
 * Один записанный тик человеческого прицеливания в формате (состояние → действие).
 *
 * <p>Состояние: ошибка до цели ({@link #yawError}/{@link #pitchError}), записанная
 * точка прицеливания внутри хитбокса ({@link #aimX}/{@link #aimY}/{@link #aimZ})
 * и контекст боя ({@link #distance}, {@link #targetSpeed}, {@link #targetType}).</p>
 *
 * <p>Действие: реальная дельта движения мыши за этот тик
 * ({@link #deltaYaw}/{@link #deltaPitch}).</p>
 */
@Data
public class NeuroPattern implements Serializable {
    private static final long serialVersionUID = 5L;

    /** Ошибка по yaw до цели ПЕРЕД движением (currentYaw - perfectYaw), в градусах. */
    private final float yawError;
    /** Ошибка по pitch до цели ПЕРЕД движением (currentPitch - perfectPitch), в градусах. */
    private final float pitchError;
    /** Реальная дельта движения мыши по yaw за этот тик, в градусах. */
    private final float deltaYaw;
    /** Реальная дельта движения мыши по pitch за этот тик, в градусах. */
    private final float deltaPitch;
    /** Записанное смещение точки прицеливания по X внутри bounding box [-1..1]. */
    private final float aimX;
    /** Записанное смещение точки прицеливания по Y внутри bounding box [-1..1]. */
    private final float aimY;
    /** Записанное смещение точки прицеливания по Z внутри bounding box [-1..1]. */
    private final float aimZ;
    /** Дистанция до цели в момент записи. */
    private final double distance;
    /** Горизонтальная скорость цели в момент записи. */
    private final double targetSpeed;
    /** Тип цели: "player" или "mob". */
    private final String targetType;
    /** Время записи. */
    private final long timestamp;

    public NeuroPattern(float yawError, float pitchError,
                        float deltaYaw, float deltaPitch,
                        float aimX, float aimY, float aimZ,
                        double distance, double targetSpeed, String targetType) {
        this.yawError = yawError;
        this.pitchError = pitchError;
        this.deltaYaw = deltaYaw;
        this.deltaPitch = deltaPitch;
        this.aimX = aimX;
        this.aimY = aimY;
        this.aimZ = aimZ;
        this.distance = distance;
        this.targetSpeed = targetSpeed;
        this.targetType = targetType;
        this.timestamp = System.currentTimeMillis();
    }
}
