package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "Anti Bot", moduleCategory = ModuleCategory.COMBAT)
public class AntiBot extends Module {

    private final List<PlayerEntity> botsMap = new ArrayList<>();

    @Subscribe
    private void onUpdate(EventTick e) {
        if (mc.world == null || mc.player == null) return;

        for (var entity : mc.world.getPlayers()) {
            if (mc.player == entity) continue;

            var isBot = false;

            for (var i = 0; i < 4; i++) {
                var armorPiece = entity.getInventory().armor.get(i);

                if (!armorPiece.isEmpty()
                        && armorPiece.isEnchantable()
                        && !armorPiece.isDamaged()
                        && (entity.getInventory().armor.get(0).getItem() == Items.LEATHER_BOOTS
                        || entity.getInventory().armor.get(1).getItem() == Items.LEATHER_LEGGINGS
                        || entity.getInventory().armor.get(2).getItem() == Items.LEATHER_CHESTPLATE
                        || entity.getInventory().armor.get(3).getItem() == Items.LEATHER_HELMET
                        || entity.getInventory().armor.get(0).getItem() == Items.IRON_BOOTS
                        || entity.getInventory().armor.get(1).getItem() == Items.IRON_LEGGINGS
                        || entity.getInventory().armor.get(2).getItem() == Items.IRON_CHESTPLATE
                        || entity.getInventory().armor.get(3).getItem() == Items.IRON_HELMET)
                        && !entity.getMainHandStack().isEmpty()
                        && entity.getOffHandStack().isEmpty()
                        && entity.getHungerManager().getFoodLevel() == 20) {

                    isBot = true;
                    break;
                }
            }

            if (isBot) {
                if (!botsMap.contains(entity)) botsMap.add(entity);
            }
            else botsMap.remove(entity);
        }
    }

    public boolean isBot(PlayerEntity player) {
        return botsMap.contains(player);
    }
}