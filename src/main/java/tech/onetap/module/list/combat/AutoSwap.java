package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.movement.GuiMove;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.base.Instance;
import tech.onetap.util.packet.NetworkUtils;

@ModuleInformation(moduleName = "Auto Swap", moduleCategory = ModuleCategory.COMBAT)
public final class AutoSwap extends Module {
    private final ModeSetting itemType = new ModeSetting("Предмет", "Щит", "Щит", "Геплы", "Тотем", "Шар");
    private final ModeSetting swapType = new ModeSetting("Свапать на", "Щит", "Щит", "Геплы", "Тотем", "Шар");
    private final BindSetting keyToSwap = new BindSetting("Кнопка", -1);
    private final BooleanSetting onlyEnchanted = new BooleanSetting("Только зачарованные", false).setVisible(() -> itemType.is("Тотем") || swapType.is("Тотем"));

    private boolean startSwap;
    private int swapTick;
    private int pendingSlot = -1;

    @Subscribe
    private void onKey(EventKeyInput event) {
        if (event.getAction() == 0) return;
        if (event.getKey() == keyToSwap.getValue()) {
            if (mc.currentScreen != null) {
                if (!Instance.get(GuiMove.class).isEnabled()) return;
                if (isServerScreen()) return;
            }
            triggerSwap();
        }
    }

    @Subscribe
    private void onTick(EventTick event) {
        if (mc.player == null) return;
        if (!startSwap || pendingSlot == -1) return;

        if (swapTick >= 2) {
            restoreKeys();

            int screenSlot = pendingSlot >= 0 && pendingSlot <= 8 ? pendingSlot + 36 : pendingSlot;
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, screenSlot, 40, SlotActionType.SWAP, mc.player);
            NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
            startSwap = false;
            swapTick = 0;
            pendingSlot = -1;
        } else {
            swapTick++;
            suppressKeys();
        }
    }

    private void triggerSwap() {
        if (mc.player == null || startSwap) return;

        Item item1 = getItemByType(itemType.getValue());
        Item item2 = getItemByType(swapType.getValue());
        Item offhand = mc.player.getOffHandStack().getItem();

        int slot;

        if (item1 == item2) {
            slot = findSlotByItem(item1);
        } else {
            Item target = offhand == item1 ? item2 : item1;
            slot = findSlotByItem(target);
        }

        if (slot == -1) return;

        pendingSlot = slot;
        startSwap = true;
        swapTick = 0;
    }

    private int findSlotByItem(Item item) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != item) continue;
            if (item == Items.TOTEM_OF_UNDYING && onlyEnchanted.getValue()) {
                if (stack.getEnchantments().isEmpty()) continue;
            }
            return i;
        }
        return -1;
    }

    private void suppressKeys() {
        mc.options.jumpKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.backKey.setPressed(false);
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

    private boolean isServerScreen() {
        return mc.player.currentScreenHandler.slots.size() != 46;
    }

    private Item getItemByType(String type) {
        return switch (type) {
            case "Щит" -> Items.SHIELD;
            case "Тотем" -> Items.TOTEM_OF_UNDYING;
            case "Геплы" -> Items.GOLDEN_APPLE;
            case "Шар" -> Items.PLAYER_HEAD;
            default -> Items.AIR;
        };
    }

    @Override
    public void onDisable() {
        startSwap = false;
        swapTick = 0;
        pendingSlot = -1;
        super.onDisable();
    }
}
