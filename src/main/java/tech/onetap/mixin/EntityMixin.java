package tech.onetap.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tech.onetap.Onetap;
import tech.onetap.module.list.render.SeeInvisible;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @ModifyExpressionValue(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isControlledByPlayer()Z"))
    private boolean fixFallDistanceCalculation(boolean original) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            return false;
        }

        return original;
    }

    @ModifyExpressionValue(method = "isInvisibleTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isInvisible()Z"))
    private boolean seeInvisible(boolean original) {
        SeeInvisible seeInvisible = Onetap.getInstance().getModuleStorage().get(SeeInvisible.class);
        if (seeInvisible != null && seeInvisible.isEnabled()) {
            return false;
        }
        return original;
    }
}
