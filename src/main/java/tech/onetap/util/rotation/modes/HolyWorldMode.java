package tech.onetap.util.rotation.modes;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.render.math.GCDFixer;

/** Adapted from Rockstar HolyWorldRotationMode. */
public final class HolyWorldMode implements IMinecraft {
    private static final float MAX_CORRECTION_SPEED = 30.0F;
    private static final float MIN_GAIN = 1.0F;
    private static final float PITCH_FORCE_SCALE = 0.23F;
    private static final float NOISE_FORCE_SCALE = 0.13F;
    private static final float EPSILON = 1.0E-4F;
    private static final float PITCH_OFFSET = -1.75F;

    private LivingEntity target;
    private boolean active;
    private long lastFrameNanos;

    private float yawVelocity;
    private float pitchVelocity;
    private float yawNoiseVelocity;
    private float pitchNoiseVelocity;
    private float gcdYawRemainder;
    private float gcdPitchRemainder;
    private float lastPlayerYaw = Float.NaN;
    private float lastPlayerPitch = Float.NaN;
    private float yawCompensation;
    private float pitchCompensation;
    private float lastAppliedYawDelta;
    private float lastAppliedPitchDelta;

    private double smoothedTargetX;
    private double smoothedTargetY;
    private double smoothedTargetZ;
    private boolean hasSmoothedTarget;
    private double eyeX;
    private double eyeY;
    private double eyeZ;

    private final float[] gaussianNoise = new float[2];
    private final float[] noiseDelta = new float[2];
    private final float[] appliedDelta = new float[2];
    private final float[] targetDelta = new float[2];

    private float noiseGain;
    private float noiseTimer;
    private float noisePeriod;
    private float noiseRamp;
    private boolean noiseBurst;
    private float burstShortMin;
    private float burstShortMax;
    private float burstLongMin;
    private float burstLongMax;
    private float burstHighGain;
    private float burstLowGain;
    private float noiseRampSpeed;
    private float noiseRampMax;
    private float nearTargetDistance;
    private float settleDistance;
    private float settleScale;

    public HolyWorldMode() {
        resetNoiseProfile();
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
        this.active = target != null;
        if (!active && (this.target != null || hasSmoothedTarget)) {
            resetState();
        }
    }

    public boolean isActive() {
        return active;
    }

    public void tick() {
        if (!active || target == null || mc.player == null || mc.world == null || mc.player.isSleeping()) {
            if (target != null || hasSmoothedTarget) {
                resetState();
            }
            return;
        }

        long now = System.nanoTime();
        float frameSeconds = frameSeconds(now);
        lastFrameNanos = now;
        if (frameSeconds < EPSILON || frameSeconds > 0.1F) {
            frameSeconds = 0.016666668F;
        }

        updateCompensation();
        lastAppliedPitchDelta = 0.0F;
        lastAppliedYawDelta = 0.0F;
        saveCurrentRotation();

        Vec3d eyes = mc.player.getEyePos();
        eyeX = eyes.x;
        eyeY = eyes.y;
        eyeZ = eyes.z;
        updateTargetDelta(target, frameSeconds);

        float yawDelta = targetDelta[0];
        float pitchDelta = targetDelta[1];
        if (!Float.isFinite(yawDelta) || !Float.isFinite(pitchDelta)) {
            decayMotion(frameSeconds);
            saveCurrentRotation();
            return;
        }

        float distance = (float) Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
        if (distance < 0.1F) {
            decayMotion(frameSeconds);
            return;
        }

        pitchDelta += PITCH_OFFSET;
        float adjustedDistance = (float) Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
        float inverseDistance = adjustedDistance > EPSILON ? 1.0F / adjustedDistance : 0.0F;
        float yawDirection = yawDelta * inverseDistance;
        float pitchDirection = pitchDelta * inverseDistance;
        float forceScale = Math.min(adjustedDistance / 10.0F, 1.0F);
        float yawForce = yawDirection * 600.0F * forceScale;
        float pitchForce = pitchDirection * 600.0F * forceScale * PITCH_FORCE_SCALE;
        float noiseForce = NOISE_FORCE_SCALE * adaptiveNoiseGain(adjustedDistance, frameSeconds);

        if (noiseForce > EPSILON) {
            applyNoise(yawForce, pitchForce, noiseForce, frameSeconds);
            yawForce += noiseDelta[0];
            pitchForce += noiseDelta[1] * 2.0F;
        }

        yawForce = dampenOppositeMotion(yawForce, yawCompensation, frameSeconds);
        pitchForce = dampenOppositeMotion(pitchForce, pitchCompensation, frameSeconds);
        updateVelocity(yawForce, pitchForce, frameSeconds);
        applyFrame(frameSeconds);
        saveCurrentRotation();
    }

    private void updateTargetDelta(LivingEntity target, float frameSeconds) {
        double targetX = target.getX();
        double targetY = target.getY() + target.getHeight() * 0.5F;
        double targetZ = target.getZ();

        if (!hasSmoothedTarget) {
            smoothedTargetX = targetX;
            smoothedTargetY = targetY;
            smoothedTargetZ = targetZ;
            hasSmoothedTarget = true;
        } else {
            double blend = MathHelper.clamp(1.0 - Math.exp(-25.0 * frameSeconds), 0.05, 0.95);
            smoothedTargetX += (targetX - smoothedTargetX) * blend;
            smoothedTargetY += (targetY - smoothedTargetY) * blend;
            smoothedTargetZ += (targetZ - smoothedTargetZ) * blend;
        }

        double deltaX = smoothedTargetX - eyeX;
        double deltaY = smoothedTargetY - eyeY;
        double deltaZ = smoothedTargetZ - eyeZ;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontal < EPSILON && Math.abs(deltaY) < EPSILON) {
            targetDelta[0] = 0.0F;
            targetDelta[1] = 0.0F;
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float pitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(deltaY, horizontal)), -90.0F, 90.0F);
        targetDelta[0] = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
        targetDelta[1] = pitch - mc.player.getPitch();
    }

    private void updateVelocity(float yawForce, float pitchForce, float frameSeconds) {
        float blend = MathHelper.clamp(1.0F - (float) Math.exp(-20.0F * frameSeconds), 0.05F, 0.95F);
        yawVelocity = lerp(yawVelocity, yawForce, blend);
        pitchVelocity = lerp(pitchVelocity, pitchForce, blend * 0.75F);
        float maxYaw = Math.max(Math.abs(targetDelta[0]) / frameSeconds * 1.5F, MIN_GAIN);
        float maxPitch = Math.max(Math.abs(targetDelta[1]) / frameSeconds * 1.5F, MIN_GAIN);
        yawVelocity = MathHelper.clamp(yawVelocity, -Math.min(maxYaw, MAX_CORRECTION_SPEED / frameSeconds), Math.min(maxYaw, MAX_CORRECTION_SPEED / frameSeconds));
        pitchVelocity = MathHelper.clamp(pitchVelocity, -Math.min(maxPitch, MAX_CORRECTION_SPEED / frameSeconds), Math.min(maxPitch, MAX_CORRECTION_SPEED / frameSeconds));
    }

    private float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }

    private void applyFrame(float frameSeconds) {
        if (mc.player == null) return;
        applyMouseStep(yawVelocity * frameSeconds, pitchVelocity * frameSeconds);
        float appliedYaw = Float.isFinite(appliedDelta[0]) ? appliedDelta[0] : 0.0F;
        float appliedPitch = Float.isFinite(appliedDelta[1]) ? appliedDelta[1] : 0.0F;
        float yaw = mc.player.getYaw() + appliedYaw;
        float pitch = MathHelper.clamp(mc.player.getPitch() + appliedPitch, -90.0F, 90.0F);
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.player.prevYaw = yaw;
        mc.player.lastRenderYaw = yaw;
        lastAppliedYawDelta = appliedYaw;
        lastAppliedPitchDelta = appliedPitch;
        lastPlayerYaw = yaw;
        lastPlayerPitch = pitch;
    }

    private float adaptiveNoiseGain(float distance, float frameSeconds) {
        noiseTimer += frameSeconds;
        if (noiseTimer >= noisePeriod) {
            noiseBurst = !noiseBurst;
            noiseTimer = 0.0F;
            noisePeriod = noiseBurst ? AuraRotationSupport.random(burstShortMin, burstShortMax) : AuraRotationSupport.random(burstLongMin, burstLongMax);
        }
        float speed = noiseBurst ? 7.0F : 2.5F;
        float targetGain = noiseBurst ? burstHighGain : burstLowGain;
        noiseGain += (targetGain - noiseGain) * (1.0F - (float) Math.exp(-speed * frameSeconds));
        float gain = noiseGain;
        float rampSpeed = noiseRampSpeed * 0.23333333F;
        noiseRamp = MathHelper.clamp(noiseRamp + frameSeconds * rampSpeed, 0.0F, noiseRampMax);
        gain += noiseRamp;
        if (distance < nearTargetDistance) {
            gain *= settleScale;
        } else if (distance < settleDistance) {
            float factor = (distance - nearTargetDistance) / (settleDistance - nearTargetDistance);
            gain *= settleScale + (1.0F - settleScale) * factor;
        }
        return Math.max(gain, 0.0F);
    }

    private void applyNoise(float yawForce, float pitchForce, float noiseForce, float frameSeconds) {
        float decay = (float) Math.exp(-frameSeconds / 0.04F);
        randomizeGaussianNoise();
        yawNoiseVelocity = yawNoiseVelocity * decay + gaussianNoise[0] * noiseForce * Math.abs(yawForce) * (1.0F - decay);
        pitchNoiseVelocity = pitchNoiseVelocity * decay + gaussianNoise[1] * noiseForce * Math.abs(pitchForce) * (1.0F - decay);
        noiseDelta[0] = yawNoiseVelocity;
        noiseDelta[1] = pitchNoiseVelocity;
    }

    private void randomizeGaussianNoise() {
        double first = Math.max(1.0E-10, Math.random());
        double second = Math.random();
        double radius = Math.sqrt(-2.0 * Math.log(first));
        gaussianNoise[0] = (float) (radius * Math.cos(Math.PI * 2 * second));
        gaussianNoise[1] = (float) (radius * Math.sin(Math.PI * 2 * second));
    }

    private void updateCompensation() {
        if (!Float.isNaN(lastPlayerYaw) && mc.player != null) {
            yawCompensation = MathHelper.wrapDegrees(mc.player.getYaw() - lastPlayerYaw) - lastAppliedYawDelta;
            pitchCompensation = mc.player.getPitch() - lastPlayerPitch - lastAppliedPitchDelta;
        } else {
            pitchCompensation = 0.0F;
            yawCompensation = 0.0F;
        }
    }

    private void saveCurrentRotation() {
        if (mc.player != null) {
            lastPlayerYaw = mc.player.getYaw();
            lastPlayerPitch = mc.player.getPitch();
        }
    }

    private float dampenOppositeMotion(float force, float compensation, float frameSeconds) {
        if (Math.abs(force) < EPSILON) return force;
        float compensationSpeed = frameSeconds > EPSILON ? compensation / frameSeconds : 0.0F;
        if (Math.abs(compensationSpeed) < 0.1F) return force;
        if (force > 0.0F == compensationSpeed > 0.0F) return force * 0.95F;
        float scale = 1.0F - MathHelper.clamp(Math.abs(compensationSpeed) / Math.abs(force), 0.0F, 0.8F);
        return force * scale;
    }

    private void applyMouseStep(float yawDelta, float pitchDelta) {
        float step = GCDFixer.getGCDValue();
        if (step < EPSILON) {
            appliedDelta[0] = yawDelta;
            appliedDelta[1] = pitchDelta;
            return;
        }
        gcdYawRemainder += yawDelta;
        gcdPitchRemainder += pitchDelta;
        float snappedYaw = Math.round(gcdYawRemainder / step) * step;
        float snappedPitch = Math.round(gcdPitchRemainder / step) * step;
        gcdYawRemainder = MathHelper.clamp(gcdYawRemainder - snappedYaw, -step * 2.0F, step * 2.0F);
        gcdPitchRemainder = MathHelper.clamp(gcdPitchRemainder - snappedPitch, -step * 2.0F, step * 2.0F);
        appliedDelta[0] = snappedYaw;
        appliedDelta[1] = snappedPitch;
    }

    private float frameSeconds(long now) {
        if (lastFrameNanos == 0L) return 0.016666668F;
        long delta = now - lastFrameNanos;
        return delta <= 0L ? 0.016666668F : Math.min((float) delta / 1.0E9F, 0.1F);
    }

    private void decayMotion(float frameSeconds) {
        float decay = (float) Math.exp(-20.0F * frameSeconds);
        yawVelocity *= decay;
        pitchVelocity *= decay;
        gcdYawRemainder *= decay;
        gcdPitchRemainder *= decay;
        yawNoiseVelocity *= decay;
        pitchNoiseVelocity *= decay;
    }

    private void resetState() {
        target = null;
        active = false;
        hasSmoothedTarget = false;
        gcdPitchRemainder = 0.0F;
        gcdYawRemainder = 0.0F;
        pitchNoiseVelocity = 0.0F;
        yawNoiseVelocity = 0.0F;
        lastPlayerPitch = Float.NaN;
        lastPlayerYaw = Float.NaN;
        pitchCompensation = 0.0F;
        yawCompensation = 0.0F;
        lastAppliedPitchDelta = 0.0F;
        lastAppliedYawDelta = 0.0F;
        noiseRamp = 0.0F;
        pitchVelocity = 0.0F;
        yawVelocity = 0.0F;
        lastFrameNanos = 0L;
        smoothedTargetZ = 0.0;
        smoothedTargetY = 0.0;
        smoothedTargetX = 0.0;
    }

    public void reset() {
        resetState();
        resetNoiseProfile();
    }

    private void resetNoiseProfile() {
        burstShortMin = 0.1F + AuraRotationSupport.random(0.0F, 0.5F);
        burstShortMax = 0.5F + AuraRotationSupport.random(0.0F, 0.75F);
        burstLongMin = 0.5F + AuraRotationSupport.random(0.0F, 0.5F);
        burstLongMax = 3.0F + AuraRotationSupport.random(0.0F, 0.25F);
        burstHighGain = 0.5F + AuraRotationSupport.random(0.0F, 0.7F);
        burstLowGain = 0.08F + AuraRotationSupport.random(0.0F, 0.12F);
        noiseRampSpeed = 0.018F + AuraRotationSupport.random(0.0F, 0.005F);
        noiseRampMax = 0.25F + AuraRotationSupport.random(0.0F, 0.05F);
        nearTargetDistance = 2.0F + AuraRotationSupport.random(0.0F, 0.5F);
        settleDistance = 6.0F + AuraRotationSupport.random(0.0F, 3.0F);
        settleScale = 0.15F + AuraRotationSupport.random(0.0F, 0.05F);
        noisePeriod = AuraRotationSupport.random(burstLongMin, burstLongMax);
    }
}
