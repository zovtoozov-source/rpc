package tech.onetap.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tech.onetap.Onetap;
import tech.onetap.module.list.render.SeeInvisible;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isInvisible()Z"))
    private boolean overrideInvisibility(boolean original, LivingEntity entity) {
        SeeInvisible seeInvisible = Onetap.getInstance().getModuleStorage().get(SeeInvisible.class);
        if (seeInvisible != null && seeInvisible.isEnabled()) {
            return false;
        }
        return original;
    }
}
