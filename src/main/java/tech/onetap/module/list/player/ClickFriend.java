package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.friend.Friend;
import tech.onetap.util.friend.FriendRepository;

@ModuleInformation(moduleName = "ClickFriend", moduleCategory = ModuleCategory.PLAYER)
public class ClickFriend extends Module {
    private static final double MAX_DISTANCE = 5.0;

    private final BindSetting clickBind = new BindSetting("Кнопка", -1);

    @Subscribe
    public void onKey(EventKeyInput event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getAction() != 1 || event.getKey() != clickBind.getValue()) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hitResult)) return;
        if (!(hitResult.getEntity() instanceof PlayerEntity player)) return;
        if (mc.player.squaredDistanceTo(player) > MAX_DISTANCE * MAX_DISTANCE) return;

        String name = player.getGameProfile().getName();
        if (FriendRepository.isFriend(name)) {
            FriendRepository.removeFriend(name);
            ChatUtil.send("§c[ClickFriend] §fУдалён из друзей: §e" + name);
        } else {
            FriendRepository.addFriend(name);
            ChatUtil.send("§a[ClickFriend] §fДобавлен в друзья: §e" + name);
        }
    }
}
