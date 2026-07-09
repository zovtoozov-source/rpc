package tech.onetap.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.event.list.FireworkEvent;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {
    @Shadow
    public LivingEntity shooter;

    @Shadow
    protected abstract boolean wasShotAtAngle();

    @Shadow
    private java.util.List<net.minecraft.component.type.FireworkExplosionComponent> getExplosions() { return java.util.List.of(); }

    @Unique
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        var self = (FireworkRocketEntity) (Object) this;
        if (shooter == null) return;
        if (wasShotAtAngle()) return;
        if (getExplosions().isEmpty()) return;

        var event = new FireworkEvent(shooter, 1.5f);
        event.post();
        float speed = event.getSpeed();
        if (Math.abs(speed - 1.5f) < 0.001f) return;

        Vec3d rot = shooter.getRotationVector();
        Vec3d currVel = shooter.getVelocity();
        shooter.setVelocity(
                rot.x * 0.1 + rot.x * speed - currVel.x * 0.5,
                rot.y * 0.1 + rot.y * speed - currVel.y * 0.5,
                rot.z * 0.1 + rot.z * speed - currVel.z * 0.5
        );
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (mc.player == null || mc.world == null || !mc.player.isGliding()) return;

        if (shooter == mc.player) return;

        if (hasActiveOwnFirework()) return;
        
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        Vec3d fireworkPos = self.getPos();
        Vec3d playerPos = mc.player.getPos();
        
        double distance = fireworkPos.distanceTo(playerPos);

        if (distance < 5.0) {
            Vec3d fireworkVel = self.getVelocity();
            if (fireworkVel.lengthSquared() < 0.01) return;
            
            Vec3d toPlayer = playerPos.subtract(fireworkPos).normalize();

            double dot = fireworkVel.normalize().dotProduct(toPlayer);
            
            if (dot > 0.4) {
                Vec3d fireworkDir = fireworkVel.normalize();

                Vec3d right = new Vec3d(fireworkDir.z, 0, -fireworkDir.x).normalize();

                double dotRight = toPlayer.dotProduct(right);
                if (dotRight < 0) {
                    right = right.multiply(-1);
                }

                double urgency = 1.0 - (distance / 5.0);
                double strength = 0.25 * urgency;
                double upward = 0.1 * urgency;
                
                Vec3d currentVel = mc.player.getVelocity();
                Vec3d dodge = right.multiply(strength).add(0, upward, 0);
                
                mc.player.setVelocity(currentVel.add(dodge));
            }
        }
    }
    
    @Unique
    private boolean hasActiveOwnFirework() {
        for (var entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity firework) {
                IFireworkRocketEntityAccessor accessor = (IFireworkRocketEntityAccessor) firework;
                if (accessor.getShooter() == mc.player) {
                    return true;
                }
            }
        }
        return false;
    }
}
