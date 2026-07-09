package tech.onetap.util.rotation.modes;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.rotation.Rotation;

/**
 * FunTime-style rotation: плавная наводка на реальную точку цели с малыми
 * периодическими отклонениями, без idle-кручения вокруг текущего yaw.
 */
public final class FunTimeMode implements AuraRotationMode {
    private static final float MIN_YAW_SPEED = 32.0f;
    private static final float MAX_YAW_SPEED = 72.0f;
    private static final float MIN_PITCH_SPEED = 8.0f;
    private static final float MAX_PITCH_SPEED = 18.0f;
    private static final float YAW_WAVE_AMPLITUDE = 2.6f;
    private static final float PITCH_WAVE_AMPLITUDE = 1.4f;
    private static final float ATTACK_WAVE_SCALE = 0.35f;
    private static final float TRACK_WAVE_SCALE = 0.18f;

    private boolean readyToAttack;

    public void setReadyToAttack(boolean readyToAttack) {
        this.readyToAttack = readyToAttack;
    }

    public void onHit() {
        // Метод оставлен для совместимости с KillAura; режим не должен уходить в idle-spin.
    }

    @Override
    public Rotation calculate(LivingEntity target) {
        if (mc.player == null || target == null) return new Rotation(mc.player);

        Box box = target.getBoundingBox();
        Vec3d aimPoint = highStablePoint(target, box);
        Vec3d predictedPlayer = mc.player.getPos()
                .add(mc.player.getVelocity().multiply(0.6).withAxis(Direction.Axis.Y, 0.0));

        double dx = aimPoint.x - predictedPlayer.x;
        double dy = aimPoint.y - (predictedPlayer.y + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = aimPoint.z - predictedPlayer.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        float waveScale = readyToAttack ? ATTACK_WAVE_SCALE : TRACK_WAVE_SCALE;
        yaw += wave(620L, 0.35f) * YAW_WAVE_AMPLITUDE * waveScale;
        pitch += wave(480L, 1.85f) * PITCH_WAVE_AMPLITUDE * waveScale;

        return new Rotation(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f));
    }

    @Override
    public float yawSpeed() {
        return readyToAttack ? AuraRotationSupport.random(48.0f, MAX_YAW_SPEED) : AuraRotationSupport.random(MIN_YAW_SPEED, 52.0f);
    }

    @Override
    public float pitchSpeed() {
        return readyToAttack ? AuraRotationSupport.random(12.0f, MAX_PITCH_SPEED) : AuraRotationSupport.random(MIN_PITCH_SPEED, 14.0f);
    }

    @Override
    public void reset() {
        readyToAttack = false;
    }

    private Vec3d highStablePoint(LivingEntity target, Box box) {
        Vec3d nearest = AuraRotationSupport.aimPoint(target);
        double height = box.maxY - box.minY;
        double minY = box.minY + height * 0.58;
        double maxY = box.minY + height * 0.86;
        return new Vec3d(nearest.x, MathHelper.clamp(nearest.y, minY, maxY), nearest.z);
    }

    private float wave(long durationMs, float phase) {
        double progress = (System.currentTimeMillis() % durationMs) / (double) durationMs;
        return (float) Math.sin(progress * Math.PI * 2.0 + phase);
    }
}
