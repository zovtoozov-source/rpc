package tech.onetap.util.rotation.modes;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.rotation.Rotation;

public final class SpookyTimeMode implements AuraRotationMode {
    private static final int POINT_SWITCH_TICKS = 4;
    private static final float YAW_JITTER = 2.2f;
    private static final float PITCH_JITTER = 1.4f;

    private int tickCounter;
    private int pointIndex;

    @Override
    public Rotation calculate(LivingEntity target) {
        if (mc.player == null || target == null) return new Rotation(mc.player);

        Box box = target.getBoundingBox();
        Vec3d hitPoint = spookyPoint(target, box);
        Vec3d playerPos = mc.player.getPos()
                .add(mc.player.getVelocity().multiply(1.35).withAxis(Direction.Axis.Y, 0.0));

        double dx = hitPoint.x - playerPos.x;
        double dy = hitPoint.y - (playerPos.y + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = hitPoint.z - playerPos.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        yaw += AuraRotationSupport.random(-YAW_JITTER, YAW_JITTER);
        pitch += AuraRotationSupport.random(-PITCH_JITTER, PITCH_JITTER);

        return new Rotation(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f));
    }

    @Override
    public float yawSpeed() {
        return AuraRotationSupport.random(60.0f, 82.0f);
    }

    @Override
    public float pitchSpeed() {
        return AuraRotationSupport.random(38.0f, 68.0f);
    }

    @Override
    public void reset() {
        tickCounter = 0;
        pointIndex = 0;
    }

    private Vec3d spookyPoint(LivingEntity target, Box box) {
        if (++tickCounter % POINT_SWITCH_TICKS == 0) {
            pointIndex = (pointIndex + 1) % 9;
        }

        double width = box.maxX - box.minX;
        double depth = box.maxZ - box.minZ;
        double height = box.maxY - box.minY;
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double high = box.minY + height * 0.74;
        double head = box.minY + height * 0.90;
        double shoulder = box.minY + height * 0.66;
        double x = cx;
        double y = high;
        double z = cz;
        double sx = width * 0.22;
        double sz = depth * 0.22;

        switch (pointIndex) {
            case 1 -> x = cx + sx;
            case 2 -> x = cx - sx;
            case 3 -> z = cz + sz;
            case 4 -> z = cz - sz;
            case 5 -> { x = cx + sx; y = head; }
            case 6 -> { x = cx - sx; y = head; }
            case 7 -> { z = cz + sz; y = shoulder; }
            case 8 -> { z = cz - sz; y = shoulder; }
            default -> y = head;
        }

        Vec3d nearest = AuraRotationSupport.aimPoint(target);
        x = MathHelper.lerp(0.35, x, nearest.x);
        z = MathHelper.lerp(0.35, z, nearest.z);
        return new Vec3d(x, MathHelper.clamp(y, box.minY, box.maxY), z);
    }
}
