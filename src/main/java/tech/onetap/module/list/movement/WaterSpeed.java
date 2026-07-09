package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;

@ModuleInformation(moduleName = "WaterSpeed", moduleCategory = ModuleCategory.MOVEMENT)
public class WaterSpeed extends Module {

    private final ModeSetting type = new ModeSetting("Обход", "Обычный", "Funtime", "Holyworld", "StormHVH", "Обычный");

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null || !mc.player.isSwimming() || !mc.player.isTouchingWater()) return;

        double multiplier;
        if (type.is("Обычный")) {
            multiplier = 1.1;
        } else if (type.is("Funtime")) {
            multiplier = 1.0505;
        } else if (type.is("StormHVH")) {
            multiplier = 1.0747;
        } else if (type.is("Holyworld")) {
            multiplier = 1.0293;
        } else {
            return;
        }

        Vec3d motion = mc.player.getVelocity();
        mc.player.setVelocity(motion.x * multiplier, motion.y, motion.z * multiplier);
    }
}