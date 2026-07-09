package tech.onetap.event.list;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import tech.onetap.event.Event;

public class MoveInputEvent extends Event {
    public float forward, strafe;
    public boolean jump, sneak;
    public double sneakSlow;
    public boolean cancelled = false;

    public MoveInputEvent(float forward, float strafe, boolean jump, boolean sneak, double sneakSlow) {
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
        this.sneak = sneak;
        this.sneakSlow = sneakSlow;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public double getSneakSlow() {
        return sneakSlow;
    }

    public float getForward() {
        return forward;
    }

    public float getStrafe() {
        return strafe;
    }

    public boolean isJump() {
        return jump;
    }

    public boolean isSneaking() {
        return sneak;
    }

    public void setYaw(float yaw) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            setYaw(yaw, mc.player.getYaw());
        }
    }

    public void setYaw(float yaw, float directionYaw) {
        float inputForward = forward;
        float inputStrafe = strafe;
        if (inputForward == 0.0f && inputStrafe == 0.0f) return;

        double angle = MathHelper.wrapDegrees(Math.toDegrees(direction(directionYaw, inputForward, inputStrafe)));
        float bestForward = 0.0f;
        float bestStrafe = 0.0f;
        float bestDiff = Float.MAX_VALUE;

        for (float predictedForward = -1.0f; predictedForward <= 1.0f; predictedForward++) {
            for (float predictedStrafe = -1.0f; predictedStrafe <= 1.0f; predictedStrafe++) {
                if (predictedForward == 0.0f && predictedStrafe == 0.0f) continue;

                double predictedAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(yaw, predictedForward, predictedStrafe)));
                float diff = Math.abs(MathHelper.wrapDegrees((float) (angle - predictedAngle)));
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestForward = predictedForward;
                    bestStrafe = predictedStrafe;
                }
            }
        }

        forward = bestForward;
        strafe = bestStrafe;
    }

    private double direction(float rotationYaw, float moveForward, float moveStrafing) {
        if (moveForward < 0.0f) rotationYaw += 180.0f;
        float forwardFactor = 1.0f;
        if (moveForward < 0.0f) forwardFactor = -0.5f;
        if (moveForward > 0.0f) forwardFactor = 0.5f;
        if (moveStrafing > 0.0f) rotationYaw -= 90.0f * forwardFactor;
        if (moveStrafing < 0.0f) rotationYaw += 90.0f * forwardFactor;
        return Math.toRadians(rotationYaw);
    }
}