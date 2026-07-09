package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.OminousBottleAmplifierComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

import java.util.Map;

@ModuleInformation(moduleName = "AutoOminous", moduleCategory = ModuleCategory.MISC)
public class AutoOminous extends Module {

    private static final int HOLD_TICKS = 4;
    private static final int COOLDOWN_TICKS = 60;
    private static final int VICTORY_MISSING_THRESHOLD = 40;

    private int prevHotbarSlot = -1;
    private int bottleSourceSlot = -1;
    private int holdTimer = 0;
    private long lastTriggerTick = -1000;
    private boolean victoryVisibleLastTick = false;
    private boolean firedThisVictory = false;
    private int victoryMissingTicks = 0;
    private int triggerLockTicks = 0;

    @Subscribe
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (triggerLockTicks > 0) {
            triggerLockTicks--;
        }

        if (holdTimer > 0) {
            holdTimer--;
            if (holdTimer == 0) {
                mc.options.useKey.setPressed(false);
                swapBack();
            } else {
                mc.options.useKey.setPressed(true);
            }
            return;
        }

        boolean victoryVisible = isRaidVictoryBarVisible();

        if (victoryVisible) {
            victoryMissingTicks = 0;
            if (!victoryVisibleLastTick && !firedThisVictory && triggerLockTicks <= 0) {
                swapIn();
            }
        } else {
            victoryMissingTicks++;
            if (victoryMissingTicks >= VICTORY_MISSING_THRESHOLD) {
                firedThisVictory = false;
            }
        }

        victoryVisibleLastTick = victoryVisible;
    }

    private boolean isRaidVictoryBarVisible() {
        try {
            BossBarHud bossBarHud = mc.inGameHud.getBossBarHud();
            for (java.lang.reflect.Field field : BossBarHud.class.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(bossBarHud);
                if (value instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) value;
                    for (Object entry : map.values()) {
                        if (entry instanceof ClientBossBar bar) {
                            String name = bar.getName().getString().toLowerCase();
                            if ((name.contains("raid") && name.contains("victory")) || (name.contains("рейд") && name.contains("побед"))) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void swapIn() {
        firedThisVictory = true;
        triggerLockTicks = COOLDOWN_TICKS;

        long now = mc.world.getTime();
        if (now - lastTriggerTick <= 0) return;
        lastTriggerTick = now;

        int currentSlot = mc.player.getInventory().selectedSlot;
        prevHotbarSlot = currentSlot;

        int hotbarBottleSlot = selectBottleHotbarSlot();

        if (hotbarBottleSlot == currentSlot) {
            bottleSourceSlot = -1;
            holdTimer = HOLD_TICKS;
            mc.options.useKey.setPressed(true);
            return;
        }

        if (hotbarBottleSlot != -1) {
            bottleSourceSlot = 36 + hotbarBottleSlot;
        } else {
            int invSlot = selectBottleInventorySlot();
            if (invSlot == -1) {
                mc.player.sendMessage(net.minecraft.text.Text.literal("No Ominous Bottles found!"), false);
                return;
            }
            bottleSourceSlot = invSlot;
        }

        try {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, bottleSourceSlot, currentSlot, SlotActionType.SWAP, mc.player);
        } catch (Exception ignored) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("Failed to swap bottle!"), false);
            bottleSourceSlot = -1;
            return;
        }

        mc.player.getInventory().selectedSlot = currentSlot;
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
        }

        holdTimer = HOLD_TICKS;
        mc.options.useKey.setPressed(true);
    }

    private void swapBack() {
        if (prevHotbarSlot >= 0) {
            mc.player.getInventory().selectedSlot = prevHotbarSlot;
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevHotbarSlot));
            }
            prevHotbarSlot = -1;
        }
        if (bottleSourceSlot >= 0) {
            try {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, bottleSourceSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            } catch (Exception ignored) {
            }
            bottleSourceSlot = -1;
        }
    }

    private int selectBottleHotbarSlot() {
        int bestSlot = -1;
        int bestLevel = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isOminousBottle(stack)) {
                int level = getOminousLevel(stack);
                if (bestSlot == -1 || level > bestLevel) {
                    bestSlot = i;
                    bestLevel = level;
                }
            }
        }
        return bestSlot;
    }

    private int selectBottleInventorySlot() {
        int bestSlot = -1;
        int bestLevel = -1;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isOminousBottle(stack)) {
                int level = getOminousLevel(stack);
                if (bestSlot == -1 || level > bestLevel) {
                    bestSlot = i;
                    bestLevel = level;
                }
            }
        }
        return bestSlot;
    }

    private boolean isOminousBottle(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == Items.OMINOUS_BOTTLE;
    }

    private int getOminousLevel(ItemStack stack) {
        try {
            OminousBottleAmplifierComponent component = stack.get(DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER);
            return component != null ? component.value() + 1 : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    @Override
    public void onDisable() {
        if (holdTimer > 0) {
            mc.options.useKey.setPressed(false);
        }
        holdTimer = 0;
        prevHotbarSlot = -1;
        bottleSourceSlot = -1;
        firedThisVictory = false;
        victoryVisibleLastTick = false;
        super.onDisable();
    }
}
