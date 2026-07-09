package tech.onetap.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.onetap.event.list.EventChangeSprint;
import tech.onetap.module.list.player.NoPush;
import tech.onetap.module.list.render.SwingAnimations;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.base.Instance;
import tech.onetap.util.rotation.FreeLookComponent;
import tech.onetap.util.rotation.RotationComponent;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void cancelPushAway(Entity entity, CallbackInfo ci) {
        if (Instance.get(NoPush.class).isEnabled() && Instance.get(NoPush.class).objects.isEnabled("Игроки")) ci.cancel();
    }

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void onSetSprinting(boolean sprinting, CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity && sprinting) {
            var event = new EventChangeSprint(true);
            event.post();

            if (!event.isSprinting()) ci.cancel();
        }
    }

    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    private void onGetHandSwingDuration(CallbackInfoReturnable<Integer> cir) {
        var swing = Instance.get(SwingAnimations.class);

        if (swing != null && swing.isEnabled()) {
            var speed = (int) swing.speed.getValue();
            cir.setReturnValue(25 - speed * 2);
        }
    }

    @Redirect(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"), require = 0)
    private float redirectJumpYaw(LivingEntity instance) {
        if ((Object) this != MinecraftClient.getInstance().player) return instance.getYaw();

        KillAura killAura = Instance.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            if (killAura.moveCorrection.is("Свободная")) {
                return FreeLookComponent.getFreeYaw();
            }
        }
        return instance.getYaw();
    }
}