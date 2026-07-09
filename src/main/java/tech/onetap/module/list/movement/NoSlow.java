package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventNoSlow;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.player.simulate.SimulatedPlayer;

@ModuleInformation(moduleName = "No Slow", moduleCategory = ModuleCategory.MOVEMENT)
public class NoSlow extends Module {

    private final ModeSetting mode = new ModeSetting("Мод", "Vanilla", "Vanilla", "Grim");

    @Subscribe
    private void onNoSlow(EventNoSlow e) {
        switch (mode.getValue()) {
            case "Vanilla" -> {
                if (!(Onetap.getInstance().getModuleStorage().get(KillAura.class).getTarget() != null && SimulatedPlayer.simulateLocalPlayer(1).fallDistance > 0)) mc.player.setSprinting(true);
                e.cancelEvent();
            }
            case "Grim" -> {
                mc.player.setSprinting(mc.player.getItemUseTime() > 4 && !(Onetap.getInstance().getModuleStorage().get(KillAura.class).getTarget() != null && SimulatedPlayer.simulateLocalPlayer(1).fallDistance > 0) && Onetap.getInstance().getServerManager().getSprintingChangeTicks() > 0);
                if (mc.player.getItemUseTime() % 2 == 0) e.cancelEvent();
            }
        }
    }
}