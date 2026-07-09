package tech.onetap.util.rotation.modes;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.rotation.Rotation;

final class AuraRotationSupport implements IMinecraft {

    static Rotation rotationTo(LivingEntity target) {
        Vec3d delta = target.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
        double h = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, h));
        return new Rotation(yaw, pitch);
    }

    static Vec3d aimPoint(LivingEntity target) {
        Box box = target.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        return new Vec3d(
                MathHelper.clamp(eye.x, box.minX, box.maxX),
                MathHelper.clamp(eye.y, box.minY, box.maxY),
                MathHelper.clamp(eye.z, box.minZ, box.maxZ)
        );
    }

    static Vec3d targetDelta(LivingEntity target, double yScale) {
        Vec3d center = target.getBoundingBox().getCenter();
        Vec3d point = new Vec3d(center.x, target.getY() + target.getEyeHeight(target.getPose()) * yScale, center.z);
        return point.subtract(mc.player.getEyePos());
    }

    static Rotation rotationFromDelta(Vec3d delta) {
        return new Rotation(delta);
    }

    static Rotation rotationDelta(Rotation base, Vec3d delta) {
        Rotation target = rotationFromDelta(delta);
        return new Rotation(MathHelper.wrapDegrees(target.getYaw() - base.getYaw()), target.getPitch() - base.getPitch());
    }

    static float random(float min, float max) {
        return min + (float) Math.random() * (max - min);
    }

    static float lerp(float delta, float start, float end) {
        return start + (end - start) * delta;
    }
}
