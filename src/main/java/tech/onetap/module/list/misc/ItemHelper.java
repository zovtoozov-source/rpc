package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.potion.Potions;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.component.DataComponentTypes;
import org.lwjgl.glfw.GLFW;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.Setting;
import tech.onetap.util.packet.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "ItemHelper", moduleCategory = ModuleCategory.MISC)
public class ItemHelper extends Module {

    private final List<KeyBind> keyBindings = new ArrayList<>();
    public final BooleanSetting netheritePriority = new BooleanSetting("Приоритет незерита", true);

    private Item currentItem;
    private String currentName;
    private boolean startSwap;
    private boolean swapped;
    private int swapTick;
    private int pendingSlot = -1;
    private int prevSlot = -1;
    private boolean wasInHotbar;
    private boolean isReturning;
    private int returnTick;

    public ItemHelper() {
        keyBindings.add(new KeyBind(Items.ENCHANTED_GOLDEN_APPLE, new BindSetting("Зачарованное яблоко", -1), "Зачарованное яблоко"));
        keyBindings.add(new KeyBind(Items.GOLDEN_APPLE, new BindSetting("Золотое яблоко", -1), "Золотое яблоко"));
        keyBindings.add(new KeyBind(Items.CHORUS_FRUIT, new BindSetting("Плод хоруса", -1), "Плод хоруса"));
        keyBindings.add(new KeyBind(Items.POTION, new BindSetting("Зелье исцеления", -1), "Зелье исцеления"));
        keyBindings.add(new KeyBind(Items.NETHERITE_AXE, new BindSetting("Топор", -1), "Топор"));
        keyBindings.add(new KeyBind(Items.NETHERITE_PICKAXE, new BindSetting("Кирка", -1), "Кирка"));
        keyBindings.add(new KeyBind(Items.NETHERITE_SWORD, new BindSetting("Меч", -1), "Меч"));
        keyBindings.add(new KeyBind(Items.NETHERITE_SHOVEL, new BindSetting("Лопата", -1), "Лопата"));
        keyBindings.add(new KeyBind(Items.END_CRYSTAL, new BindSetting("Кристал", -1), "Кристал"));
        keyBindings.add(new KeyBind(Items.OBSIDIAN, new BindSetting("Обсидиан", -1), "Обсидиан"));
        keyBindings.add(new KeyBind(Items.RESPAWN_ANCHOR, new BindSetting("Якорь", -1), "Якорь"));
        keyBindings.add(new KeyBind(Items.GLOWSTONE, new BindSetting("Светокаменный блок", -1), "Светокаменный блок"));
        keyBindings.add(new KeyBind(Items.SHIELD, new BindSetting("Щит", -1), "Щит"));
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> s = super.getSettings();
        s.add(netheritePriority);
        for (KeyBind bind : keyBindings) s.add(bind.setting);
        return s;
    }

    @Override
    public void onDisable() {
        startSwap = false;
        swapped = false;
        swapTick = 0;
        pendingSlot = -1;
        isReturning = false;
        returnTick = 0;
        prevSlot = -1;
        wasInHotbar = false;
        currentItem = null;
        currentName = null;
        super.onDisable();
    }

    @Subscribe
    private void onKey(EventKeyInput e) {
        if (e.getAction() != 1) return;

        for (KeyBind bind : keyBindings) {
            if (e.getKey() == bind.setting.getValue()) {
                if (!swapped && !startSwap && !isReturning) {
                    triggerSwap(bind.item, bind.name);
                } else if (swapped && !isReturning) {
                    triggerReturn();
                }
            }
        }
    }

    @Subscribe
    private void onTick(EventTick event) {
        if (mc.player == null) return;

        if (startSwap && pendingSlot != -1) {
            if (swapTick >= 3) {
                restoreMoveKeys();
                if (wasInHotbar) {
                    mc.player.getInventory().selectedSlot = pendingSlot;
                } else {
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        pendingSlot, prevSlot, SlotActionType.SWAP, mc.player);
                    NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                }
                startSwap = false;
                swapped = true;
                swapTick = 0;
            } else {
                swapTick++;
                lockMovement();
            }
        }

        if (isReturning) {
            if (returnTick >= 3) {
                restoreMoveKeys();
                if (wasInHotbar) {
                    mc.player.getInventory().selectedSlot = prevSlot;
                } else {
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        pendingSlot, prevSlot, SlotActionType.SWAP, mc.player);
                    NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                }
                isReturning = false;
                returnTick = 0;
                pendingSlot = -1;
                prevSlot = -1;
                wasInHotbar = false;
                swapped = false;
                currentItem = null;
                currentName = null;
            } else {
                returnTick++;
                lockMovement();
            }
        }
    }

    private void triggerSwap(Item item, String itemName) {
        if (mc.player == null || startSwap) return;

        if (mc.player.getItemCooldownManager().isCoolingDown(item.getDefaultStack())) return;

        Slot slot = getSlot(item);
        if (slot == null) return;

        if (item == Items.POTION) {
            if (!isHealingPotion(slot.getStack())) return;
        }

        currentItem = item;
        currentName = itemName;
        prevSlot = mc.player.getInventory().selectedSlot;
        wasInHotbar = slot.id >= 36 && slot.id <= 44;
        pendingSlot = wasInHotbar ? slot.id - 36 : slot.id;

        startSwap = true;
        swapTick = 0;
    }

    private void triggerReturn() {
        if (mc.player == null || !swapped) return;
        isReturning = true;
        returnTick = 0;
    }

    private Slot getSlot(Item item) {
        if (mc.player == null) return null;

        if (netheritePriority.getValue()) {
            Item fallback = getFallbackTool(item);
            if (fallback != null) {
                for (Slot slot : mc.player.currentScreenHandler.slots) {
                    if (slot.inventory == mc.player.getInventory() && slot.hasStack() && slot.getStack().isOf(item)) {
                        return slot;
                    }
                }
                for (Slot slot : mc.player.currentScreenHandler.slots) {
                    if (slot.inventory == mc.player.getInventory() && slot.hasStack() && slot.getStack().isOf(fallback)) {
                        return slot;
                    }
                }
                return null;
            }
        }

        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory() && slot.hasStack() && slot.getStack().isOf(item)) {
                return slot;
            }
        }
        return null;
    }

    private Item getFallbackTool(Item item) {
        if (item == Items.NETHERITE_AXE) return Items.DIAMOND_AXE;
        if (item == Items.NETHERITE_PICKAXE) return Items.DIAMOND_PICKAXE;
        if (item == Items.NETHERITE_SWORD) return Items.DIAMOND_SWORD;
        if (item == Items.NETHERITE_SHOVEL) return Items.DIAMOND_SHOVEL;
        return null;
    }

    private boolean isHealingPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var potion = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potion == null) return false;
        return potion.matches(Potions.HEALING) || potion.matches(Potions.STRONG_HEALING);
    }

    private void lockMovement() {
        mc.options.jumpKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    private void restoreMoveKeys() {
        long win = mc.getWindow().getHandle();
        if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) mc.options.forwardKey.setPressed(true);
        if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) mc.options.leftKey.setPressed(true);
        if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) mc.options.rightKey.setPressed(true);
        if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) mc.options.backKey.setPressed(true);
        if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) mc.options.jumpKey.setPressed(true);
        if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) mc.options.sprintKey.setPressed(true);
    }

    private record KeyBind(Item item, BindSetting setting, String name) {}
}
