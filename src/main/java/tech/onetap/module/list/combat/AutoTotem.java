package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.EventPopTotem;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.player.other.InventoryUtil;

import java.util.Iterator;

@ModuleInformation(moduleName = "Auto Totem", moduleCategory = ModuleCategory.COMBAT)
public final class AutoTotem extends Module {
    private final SliderSetting health = new SliderSetting("Здоровье", 20.0, 0.0, 20.0, 0.5);
    private final BooleanSetting elytra = new BooleanSetting("Элитры", true);
    private final SliderSetting elytraHealth = new SliderSetting("Здоровье на элитрах", 10.0, 0.0, 20.0, 0.5).setVisible(elytra::getValue);
    private final BooleanSetting fall = new BooleanSetting("Падение", true);
    private final SliderSetting fallDistance = new SliderSetting("Количество блоков", 20.0, 10.0, 50.0, 0.1).setVisible(fall::getValue);
    private final BooleanSetting crystals = new BooleanSetting("Кристалы", true);
    private final SliderSetting healthWithBall = new SliderSetting("Здоровье с шаром", 10.0, 0.0, 20.0, 0.5).setVisible(crystals::getValue);

    private int cooldownTicks;
    private int restoreCooldown;
    private Item swapBackItem;
    private int swapStage;
    private int swapSlotId = -1;

    @Subscribe
    private void onPlayerUpdate(EventPlayerUpdate event) {
        if (mc.player == null || mc.world == null) return;

        if (cooldownTicks > 0) cooldownTicks--;
        if (restoreCooldown > 0) restoreCooldown--;

        if (swapStage == 1) {
            swapStage = 2;
            return;
        }
        if (swapStage == 2) {
            swapStage = 0;
            restoreKeys();
            if (swapSlotId != -1) {
                int containerSlot = toContainerSlot(swapSlotId);
                mc.player.networkHandler.sendPacket(new ClickSlotC2SPacket(0, mc.player.currentScreenHandler.getRevision(), containerSlot, 40, SlotActionType.SWAP, mc.player.currentScreenHandler.getCursorStack(), Int2ObjectMaps.singleton(containerSlot, mc.player.currentScreenHandler.getSlot(containerSlot).getStack())));
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
                swapSlotId = -1;
            }
            cooldownTicks = 3;
            return;
        }

        ItemStack current = mc.player.getOffHandStack();
        ItemStack offHand = current.isEmpty() ? null : current;

        if (shouldUseTotem() && restoreCooldown == 0) {
            if (offHand == null || offHand.getItem() != Items.TOTEM_OF_UNDYING) {
                int totemSlot = InventoryUtil.searchItem(Items.TOTEM_OF_UNDYING);
                if (totemSlot != -1 && !isServerScreen()) {
                    if (swapBackItem == null && offHand != null) {
                        swapBackItem = offHand.getItem();
                    }
                    startSwap(totemSlot);
                }
            }
        } else if (swapBackItem != null && cooldownTicks == 0 && swapStage == 0) {
            int slot = InventoryUtil.searchItem(swapBackItem);
            if (slot != -1) {
                startSwap(slot);
            }
            swapBackItem = null;
            restoreCooldown = 20;
        }
    }

    @Subscribe
    private void onPopTotem(EventPopTotem e) {
        if (e.getPlayer() != mc.player) return;
        cooldownTicks = 5;
    }

    private void startSwap(int invSlot) {
        if (swapStage != 0) return;
        swapSlotId = invSlot;
        swapStage = 1;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    private void restoreKeys() {
        mc.options.forwardKey.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), mc.options.forwardKey.getDefaultKey().getCode()));
        mc.options.backKey.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), mc.options.backKey.getDefaultKey().getCode()));
        mc.options.leftKey.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), mc.options.leftKey.getDefaultKey().getCode()));
        mc.options.rightKey.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), mc.options.rightKey.getDefaultKey().getCode()));
        mc.options.jumpKey.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), mc.options.jumpKey.getDefaultKey().getCode()));
        mc.options.sneakKey.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), mc.options.sneakKey.getDefaultKey().getCode()));
        mc.options.sprintKey.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), mc.options.sprintKey.getDefaultKey().getCode()));
    }

    private boolean shouldUseTotem() {
        if (mc.player.isCreative() || mc.player.isSpectator()) return false;

        float healthValue = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (healthValue <= this.health.getFloatValue()) return true;

        if (fall.getValue() && mc.player.fallDistance >= fallDistance.getFloatValue() && !mc.player.isGliding()) return true;

        if (elytra.getValue() && mc.player.getInventory().getArmorStack(2).getItem() == Items.ELYTRA && healthValue <= elytraHealth.getFloatValue()) return true;

        if (crystals.getValue()) {
            Iterator<Entity> var2 = mc.world.getEntities().iterator();
            while (var2.hasNext()) {
                Entity entity = var2.next();
                if (entity instanceof EndCrystalEntity crystal) {
                    if (mc.player.getEyePos().distanceTo(crystal.getBoundingBox().getCenter()) <= 5.0D
                            && mc.player.getY() >= crystal.getY()
                            && !(mc.player.getOffHandStack().getItem() == Items.PLAYER_HEAD
                            && mc.player.getOffHandStack().hasEnchantments()
                            && !(mc.player.getHealth() <= healthWithBall.getFloatValue()))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isServerScreen() {
        return mc.player.currentScreenHandler.slots.size() != 46;
    }

    private int toContainerSlot(int invSlot) {
        if (invSlot >= 0 && invSlot <= 8) return invSlot + 36;
        return invSlot;
    }
}
