package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.util.friend.Friend;
import tech.onetap.util.friend.FriendRepository;

import java.util.Locale;

@ModuleInformation(moduleName = "Auto Tpaccept", moduleCategory = ModuleCategory.PLAYER)
public class AutoTpaccept extends Module {
    private final BooleanSetting onlyFriends = new BooleanSetting("Только друзья", false);

    @Subscribe
    public void onPacket(EventPacket event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.getPacket() instanceof GameMessageS2CPacket packet)) return;

        String raw = packet.content().getString();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!isTeleportRequest(lower)) return;
        if (onlyFriends.getValue() && !containsFriend(lower)) return;

        mc.getNetworkHandler().sendChatCommand("tpaccept");
    }

    private boolean isTeleportRequest(String text) {
        return text.contains("телепортироваться")
                || text.contains("телепорт к вам")
                || text.contains("к вам телепорт")
                || text.contains("просит") && text.contains("телепорт")
                || text.contains("has requested teleport")
                || text.contains("requested to teleport")
                || text.contains("wants to teleport")
                || text.contains("/tpaccept")
                || text.contains("tpaccept");
    }

    private boolean containsFriend(String text) {
        for (Friend friend : FriendRepository.getFriends()) {
            if (text.contains(friend.name().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
