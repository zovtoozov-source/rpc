package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.math.StopWatch;
import tech.onetap.util.player.other.SlownessManager;

@ModuleInformation(moduleName = "Teleport Back", moduleCategory = ModuleCategory.PLAYER)
public class TeleportBack extends Module {

    private final StopWatch stopWatch = new StopWatch();
    private boolean dead;

    @Subscribe
    private void onUpdate(EventTick e) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isAlive() && !dead && stopWatch.isReached(500)) {
            mc.player.networkHandler.sendChatCommand("sethome gavno");
            SlownessManager.addTimeTask(new SlownessManager.TimeTask(100, () -> {
                if (mc.player == null) return;
                mc.player.requestRespawn();
                mc.player.networkHandler.sendChatCommand("home gavno");
                dead = false;
            }, true));
            stopWatch.reset();
        }
    }
}