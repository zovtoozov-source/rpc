package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventPlayerSync;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.packet.NetworkUtils;

import java.util.LinkedList;
import java.util.function.Predicate;

@ModuleInformation(moduleName = "Click Pearl", moduleCategory = ModuleCategory.PLAYER)
public class ClickPearl extends Module {
    private final ModeSetting mode = new ModeSetting("Мод", "Обычный", "Обычный", "Легитный");
    private final BindSetting key = new BindSetting("Клавиша броска", -98);

    private boolean pearlUsed;
    private int ticksExisted;
    private boolean pearlLocked;
    private final LinkedList<Runnable> taskQueue = new LinkedList<>();

    @Subscribe
    private void onKey(EventKeyInput e) {
        if (e.getAction() == 0) return;
        if (e.getKey() == key.getValue()) {
            if (mode.is("Обычный")) swapAndUseWithReset(Items.ENDER_PEARL);
            else pearlUsed = true;
        }
    }

    private void swapAndUseWithReset(Item item) {
        if (mc.player == null || pearlLocked) return;

        float cooldown = mc.player.getItemCooldownManager().getCooldownProgress(item.getDefaultStack(), 0f);
        if (cooldown > 0) return;

        Slot s = getSlot(item, sl -> sl.id >= 36 && sl.id <= 44);
        if (s == null) s = getSlot(item);
        if (s == null) return;

        final Slot slot = s;
        final int prevSlot = mc.player.getInventory().selectedSlot;
        final boolean inHotbar = slot.id >= 36 && slot.id <= 44;
        pearlLocked = true;

        taskQueue.add(() -> suppressKeys());
        taskQueue.add(() -> suppressKeys());

        if (inHotbar) {
            taskQueue.add(() -> mc.player.getInventory().selectedSlot = slot.id - 36);
            taskQueue.add(() -> {});
            taskQueue.add(() -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND));
            taskQueue.add(() -> mc.player.getInventory().selectedSlot = prevSlot);
        } else {
            taskQueue.add(() -> {
                suppressKeys();
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot.id, prevSlot, SlotActionType.SWAP, mc.player);
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
            });
            taskQueue.add(() -> suppressKeys());
            taskQueue.add(() -> {
                suppressKeys();
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            });
            taskQueue.add(() -> suppressKeys());
            taskQueue.add(() -> {
                suppressKeys();
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot.id, prevSlot, SlotActionType.SWAP, mc.player);
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
            });
        }

        taskQueue.add(() -> suppressMovement());
        taskQueue.add(() -> suppressMovement());
        taskQueue.add(() -> suppressMovement());
        taskQueue.add(() -> suppressMovement());
        taskQueue.add(() -> { restoreMoveKeys(); pearlLocked = false; });
    }

    private Slot getSlot(Item item) {
        return getSlot(item, null);
    }

    private Slot getSlot(Item item, Predicate<Slot> predicate) {
        if (mc.player == null) return null;
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory() && slot.hasStack() && slot.getStack().isOf(item)) {
                if (predicate == null || predicate.test(slot)) return slot;
            }
        }
        return null;
    }

    private void suppressKeys() {
        mc.options.sprintKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
    }

    private void suppressMovement() {
        mc.options.sprintKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private void restoreMoveKeys() {
        long win = mc.getWindow().getHandle();
        mc.options.sprintKey.setPressed(GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS);
        mc.options.forwardKey.setPressed(GLFW.glfwGetKey(win, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS);
        mc.options.leftKey.setPressed(GLFW.glfwGetKey(win, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS);
        mc.options.rightKey.setPressed(GLFW.glfwGetKey(win, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS);
        mc.options.backKey.setPressed(GLFW.glfwGetKey(win, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS);
        mc.options.jumpKey.setPressed(GLFW.glfwGetKey(win, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS);
    }

    @Subscribe
    private void onPlayerUpdate(final EventPlayerUpdate ignored) {
        if (mc.player == null) return;

        Runnable task = taskQueue.poll();
        if (task != null) task.run();

        if (!pearlUsed && ticksExisted > 0) ticksExisted--;
        if (pearlUsed || ticksExisted > 0) mc.player.setSprinting(false);
    }

    @Subscribe
    private void onPlayerSync(final EventPlayerSync ignored) {
        if (mc.player == null || !pearlUsed) return;
        var slotHotbar = InventoryUtil.searchItem(Items.ENDER_PEARL, 0, 9);
        if (slotHotbar != -1) InventoryUtil.swapAndUseLegit(Items.ENDER_PEARL);
        else {
            if (ticksExisted == 0) {
                ticksExisted++;
                return;
            }
            InventoryUtil.swapAndUseLegit(Items.ENDER_PEARL);
            ticksExisted = 2;
        }
        pearlUsed = false;
    }

    @Override
    public void onDisable() {
        taskQueue.clear();
        pearlLocked = false;
        pearlUsed = false;
        ticksExisted = 0;
        super.onDisable();
    }
}