package tech.onetap.util.rotation.modes;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.rotation.Rotation;

/** Sloth rotation adapted to this repository's Rotation/RotationUtil semantics. */
public final class SlothMode implements AuraRotationMode {
    private static final int UPDATE_INTERVAL = 5;
    private static final float CLOSE_YAW_DIFF = 8.0f;
    private static final float CLOSE_PITCH_DIFF = 6.0f;

    private float yawOffset;
    private float pitchOffset;
    private int tickCounter;
    private int stateTicks;
    private boolean ready;

    @Override
    public Rotation calculate(LivingEntity target) {
        if (mc.player == null || target == null) return new Rotation(mc.player);

        if (!ready) {
            ready = true;
            tickCounter = 0;
            stateTicks = 0;
            yawOffset = 0.0f;
            pitchOffset = 0.0f;
        }

        Vec3d aimPoint = slothPoint(target);
        Vec3d delta = aimPoint.subtract(mc.player.getEyePos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));

        float yawDiff = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float pitchDiff = targetPitch - mc.player.getPitch();
        updateOffsets(yawDiff, pitchDiff);

        return new Rotation(
                MathHelper.wrapDegrees(mc.player.getYaw() + yawDiff + yawOffset),
                MathHelper.clamp(mc.player.getPitch() + pitchDiff + pitchOffset, -90.0f, 90.0f)
        );
    }

    @Override
    public float yawSpeed() {
        return AuraRotationSupport.random(18.0f, 36.0f);
    }

    @Override
    public float pitchSpeed() {
        return AuraRotationSupport.random(10.0f, 24.0f);
    }

    @Override
    public void reset() {
        ready = false;
        yawOffset = 0.0f;
        pitchOffset = 0.0f;
        tickCounter = 0;
        stateTicks = 0;
    }

    private Vec3d slothPoint(LivingEntity target) {
        Box box = target.getBoundingBox();
        Vec3d nearest = AuraRotationSupport.aimPoint(target);
        double height = box.maxY - box.minY;
        return new Vec3d(
                nearest.x,
                MathHelper.clamp(nearest.y, box.minY + height * 0.45, box.minY + height * 0.78),
                nearest.z
        );
    }

    private void updateOffsets(float yawDelta, float pitchDelta) {
        tickCounter++;
        stateTicks++;
        if (tickCounter % UPDATE_INTERVAL != 0) return;

        float yawTarget = AuraRotationSupport.random(-2.2f, 2.2f);
        float pitchTarget = AuraRotationSupport.random(-1.2f, 1.2f);
        if (Math.abs(yawDelta) < CLOSE_YAW_DIFF && Math.abs(pitchDelta) < CLOSE_PITCH_DIFF) {
            yawTarget *= 0.25f;
            pitchTarget *= 0.25f;
        }

        float blend = stateTicks > 20 ? 0.35f : 0.18f;
        yawOffset = AuraRotationSupport.lerp(blend, yawOffset, yawTarget);
        pitchOffset = AuraRotationSupport.lerp(blend, pitchOffset, pitchTarget);
    }
}
