package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;

@ModuleInformation(moduleName = "Sprint", moduleDesc = "Автоматический спринт", moduleCategory = ModuleCategory.MOVEMENT)
public class Sprint extends Module {

    public final ModeSetting mode = new ModeSetting("Режим", "Всегда", "Всегда", "При движении");

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null) return;
        if (mc.player.isUsingItem() && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)) return;

        switch (mode.getValue()) {
            case "Всегда" -> mc.player.setSprinting(true);
            case "При движении" -> {
                if (mc.player.forwardSpeed > 0 || mc.player.sidewaysSpeed != 0) {
                    mc.player.setSprinting(true);
                }
            }
        }
    }
}
