package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.Setting;
import tech.onetap.util.packet.NetworkUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ModuleInformation(moduleName = "ServerHelper", moduleCategory = ModuleCategory.MISC)
public class ServerHelper extends Module {

    private final List<ItemBind> itemBinds = new ArrayList<>();
    private final List<TimerEntry> timers = new ArrayList<>();

    public final ModeSetting server = new ModeSetting("Сервер", "ReallyWorld", "LonyGrief", "Raidmine", "FunTime");
    public final BindSetting antiFly = new BindSetting("Клавиша юза анти-полета", -1).setVisible(() -> server.is("ReallyWorld"));
    public final BooleanSetting notifications = new BooleanSetting("Уведомления", true);
    public final BooleanSetting itemTimer = new BooleanSetting("Таймер предметов", true).setVisible(() -> server.is("Raidmine"));

    private boolean useAntiFly;
    private boolean backpackOpen;
    private int backpackOpenTicks;
    private boolean backpackScreenSeen;
    private int backpackSlot = -1;
    private boolean backpackInHotbar;
    private int backpackPrevSlot;
    private Item backpackItem;

    private ScriptTask currentTask;

    public ServerHelper() {
        itemBinds.add(new ItemBind(Items.PRISMARINE_SHARD, new BindSetting("Взрывная трапка", -1).setVisible(() -> server.is("Raidmine")), "Взрывная трапка"));
        itemBinds.add(new ItemBind(Items.POPPED_CHORUS_FRUIT, new BindSetting("Обыч трапка", -1).setVisible(() -> server.is("Raidmine")), "Обыч трапка"));
        itemBinds.add(new ItemBind(Items.NETHER_STAR, new BindSetting("Оглушение", -1).setVisible(() -> server.is("Raidmine")), "Оглушение"));
        itemBinds.add(new ItemBind(Items.FIRE_CHARGE, new BindSetting("Взрывная штука", -1).setVisible(() -> server.is("Raidmine")), "Взрывная штука"));
        itemBinds.add(new ItemBind(Items.SNOWBALL, new BindSetting("Снежок", -1).setVisible(() -> server.is("Raidmine")), "Снежок"));
        itemBinds.add(new ItemBind(Items.PINK_SHULKER_BOX, new BindSetting("Рюкзак 1 лвл", -1).setVisible(() -> server.is("Raidmine")), "Рюкзак 1 лвл"));
        itemBinds.add(new ItemBind(Items.MAGENTA_SHULKER_BOX, new BindSetting("Рюкзак 2 лвл", -1).setVisible(() -> server.is("Raidmine")), "Рюкзак 2 лвл"));
        itemBinds.add(new ItemBind(Items.PURPLE_SHULKER_BOX, new BindSetting("Рюкзак 3 лвл", -1).setVisible(() -> server.is("Raidmine")), "Рюкзак 3 лвл"));
        itemBinds.add(new ItemBind(Items.RED_SHULKER_BOX, new BindSetting("Рюкзак 4 лвл", -1).setVisible(() -> server.is("Raidmine")), "Рюкзак 4 лвл"));

        itemBinds.add(new ItemBind(Items.SNOWBALL, new BindSetting("Снежок", -1).setVisible(() -> server.is("FunTime")), "Снежок"));
        itemBinds.add(new ItemBind(Items.PHANTOM_MEMBRANE, new BindSetting("Божья аура", -1).setVisible(() -> server.is("FunTime")), "Божья аура"));
        itemBinds.add(new ItemBind(Items.NETHERITE_SCRAP, new BindSetting("Трапка", -1).setVisible(() -> server.is("FunTime")), "Трапка"));
        itemBinds.add(new ItemBind(Items.DRIED_KELP, new BindSetting("Пласт", -1).setVisible(() -> server.is("FunTime")), "Пласт"));
        itemBinds.add(new ItemBind(Items.SUGAR, new BindSetting("Явная пыль", -1).setVisible(() -> server.is("FunTime")), "Явная пыль"));
        itemBinds.add(new ItemBind(Items.FIRE_CHARGE, new BindSetting("Огненный смерч", -1).setVisible(() -> server.is("FunTime")), "Огненный смерч"));
        itemBinds.add(new ItemBind(Items.ENDER_EYE, new BindSetting("Дезорент", -1).setVisible(() -> server.is("FunTime")), "Дезорент"));
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> s = super.getSettings();
        for (ItemBind bind : itemBinds) s.add(bind.setting);
        return s;
    }

    @Override
    public void onDisable() {
        currentTask = null;
        super.onDisable();
    }

    @Subscribe
    private void onKey(EventKeyInput e) {
        if (e.getAction() != 0) return;
        if (mc.currentScreen != null) return;

        if (server.is("ReallyWorld") && e.getKey() == antiFly.getValue()) {
            useAntiFly = true;
            return;
        }

        for (ItemBind bind : itemBinds) {
            if (e.getKey() == bind.setting.getValue()) {
                if (isBackpackItem(bind.item)) {
                    handleBackpack(bind);
                } else {
                    swapAndUseWithReset(bind.item);
                    addTimer(bind.item);
                }
            }
        }
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        Iterator<TimerEntry> it = timers.iterator();
        while (it.hasNext()) {
            TimerEntry timer = it.next();
            if (now - timer.startTime >= timer.duration) {
                it.remove();
            }
        }

        if (currentTask != null) {
            currentTask.tick();
        }

        if (server.is("ReallyWorld") && useAntiFly) {
            useAntiFly = false;
            doAntiFly();
        }

        if (backpackOpen) {
            backpackOpenTicks++;
            if (mc.currentScreen != null) backpackScreenSeen = true;
            if (backpackScreenSeen && mc.currentScreen == null) returnBackpack();
            if (!backpackScreenSeen && backpackOpenTicks > 40) returnBackpack();
        }
    }

    private void addTimer(Item item) {
        if (mc.player == null) return;
        if (!server.is("Raidmine") || !itemTimer.getValue()) return;
        if (item != Items.NETHER_STAR && item != Items.PRISMARINE_SHARD && item != Items.POPPED_CHORUS_FRUIT) return;

        Vec3d pos = mc.player.getPos();
        float dur = 12.0F;
        if (item == Items.NETHER_STAR) dur = 13.0F;
        long durationMs = (long) (dur * 1000.0F);
        timers.removeIf(t -> t.position.squaredDistanceTo(pos) < 1.0D);
        timers.add(new TimerEntry(pos, System.currentTimeMillis(), durationMs, item));
    }

    private void swapAndUseWithReset(Item item) {
        if (mc.player == null || currentTask != null) return;

        Slot slot = getSlot(item);
        if (slot == null) return;

        if (mc.player.getItemCooldownManager().getCooldownProgress(item.getDefaultStack(), 0f) > 0) return;

        currentTask = new ScriptTask();
        int prevSlot = mc.player.getInventory().selectedSlot;
        boolean inHotbar = slot.id >= 36 && slot.id <= 44;
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        currentTask.addStep(() -> lockMovement());
        currentTask.addStep(() -> lockMovement());

        if (inHotbar) {
            int hotbarSlot = slot.id - 36;
            currentTask.addStep(() -> mc.player.getInventory().selectedSlot = hotbarSlot);
            currentTask.addStep(() -> {});
            currentTask.addStep(() -> mc.interactionManager.sendSequencedPacket(mc.world,
                i -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, i, yaw, pitch)));
            currentTask.addStep(() -> mc.player.getInventory().selectedSlot = prevSlot);
        } else {
            currentTask.addStep(() -> {
                int syncId = mc.player.currentScreenHandler.syncId;
                mc.interactionManager.clickSlot(syncId, slot.id, prevSlot, SlotActionType.SWAP, mc.player);
                if (syncId != 0) mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
            });
            currentTask.addStep(() -> {});
            currentTask.addStep(() -> mc.interactionManager.sendSequencedPacket(mc.world,
                i -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, i, yaw, pitch)));
            currentTask.addStep(() -> lockMovement());
            currentTask.addStep(() -> {
                int syncId = mc.player.currentScreenHandler.syncId;
                mc.interactionManager.clickSlot(syncId, slot.id, prevSlot, SlotActionType.SWAP, mc.player);
                if (syncId != 0) mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
            });
        }
        currentTask.addStep(() -> {});
        currentTask.addStep(() -> lockMovement());
        currentTask.addStep(() -> restoreMoveKeys());
    }

    private void handleBackpack(ItemBind bind) {
        if (mc.player == null || currentTask != null) return;
        if (backpackOpen) return;

        Slot slot = getSlot(bind.item);
        if (slot == null) return;

        backpackSlot = slot.id;
        backpackInHotbar = slot.id >= 36 && slot.id <= 44;
        backpackPrevSlot = mc.player.getInventory().selectedSlot;
        backpackItem = bind.item;
        backpackScreenSeen = false;

        currentTask = new ScriptTask();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        currentTask.addStep(() -> lockMovement());
        currentTask.addStep(() -> lockMovement());

        if (backpackInHotbar) {
            currentTask.addStep(() -> mc.player.getInventory().selectedSlot = backpackSlot - 36);
            currentTask.addStep(() -> {});
            currentTask.addStep(() -> {
                mc.interactionManager.sendSequencedPacket(mc.world,
                    i -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, i, yaw, pitch));
                backpackOpen = true;
                backpackOpenTicks = 0;
            });
        } else {
            currentTask.addStep(() -> {
                int syncId = mc.player.currentScreenHandler.syncId;
                mc.interactionManager.clickSlot(syncId, backpackSlot, 8, SlotActionType.SWAP, mc.player);
                if (syncId != 0) mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
            });
            currentTask.addStep(() -> {});
            currentTask.addStep(() -> mc.player.getInventory().selectedSlot = 8);
            currentTask.addStep(() -> {
                mc.interactionManager.sendSequencedPacket(mc.world,
                    i -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, i, yaw, pitch));
                backpackOpen = true;
                backpackOpenTicks = 0;
            });
        }
    }

    private void returnBackpack() {
        backpackOpen = false;
        backpackOpenTicks = 0;
        if (mc.player == null) return;

        if (backpackItem != null) {
            Slot slot = getSlot(backpackItem);
            if (slot != null) {
                int currentSlot = slot.id;
                if (!backpackInHotbar && currentSlot != backpackSlot) {
                    int syncId = mc.player.currentScreenHandler.syncId;
                    mc.interactionManager.clickSlot(syncId, currentSlot, backpackSlot, SlotActionType.SWAP, mc.player);
                    if (syncId != 0) mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
                }
            }
        }

        mc.player.getInventory().selectedSlot = backpackPrevSlot;
        backpackSlot = -1;
        backpackItem = null;
        restoreMoveKeys();
    }

    private void doAntiFly() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        int slot = searchItem(Items.FIREWORK_STAR, 9, 45);
        int slotHotbar = searchItem(Items.FIREWORK_STAR, 0, 8);

        if (mc.player.getOffHandStack().getItem() == Items.FIREWORK_STAR) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        } else {
            if (slotHotbar != -1) {
                boolean wasSprinting = false;
                if (mc.player.isSprinting()) {
                    mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
                    mc.player.setSprinting(false);
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    wasSprinting = true;
                }
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 45, slotHotbar, SlotActionType.SWAP, mc.player);
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 45, slotHotbar, SlotActionType.SWAP, mc.player);
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                if (wasSprinting) {
                    mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
                }
            }
            if (slotHotbar == -1 && slot != -1) {
                boolean wasSprinting = false;
                if (mc.player.isSprinting()) {
                    mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
                    mc.player.setSprinting(false);
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    wasSprinting = true;
                }
                mc.interactionManager.clickSlot(0, slot, 40, SlotActionType.SWAP, mc.player);
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
                mc.interactionManager.clickSlot(0, slot, 40, SlotActionType.SWAP, mc.player);
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                if (wasSprinting) {
                    mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
                }
            }
        }
    }

    private Slot getSlot(Item item) {
        if (mc.player == null) return null;
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory() && slot.hasStack() && slot.getStack().isOf(item)) {
                return slot;
            }
        }
        return null;
    }

    private int searchItem(Item item, int start, int end) {
        if (mc.player == null) return -1;
        for (int i = start; i < end; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private boolean isBackpackItem(Item item) {
        return item == Items.PINK_SHULKER_BOX || item == Items.MAGENTA_SHULKER_BOX
            || item == Items.PURPLE_SHULKER_BOX || item == Items.RED_SHULKER_BOX;
    }

    private void lockMovement() {
        mc.options.sprintKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
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

    private class ScriptTask {
        private final List<Runnable> steps = new ArrayList<>();
        private int index = 0;
        private int tickCounter = 0;

        void addStep(Runnable step) {
            steps.add(step);
        }

        void tick() {
            if (index >= steps.size()) {
                currentTask = null;
                return;
            }
            if (tickCounter == 0) {
                steps.get(index).run();
            }
            tickCounter++;
            if (tickCounter >= 1) {
                tickCounter = 0;
                index++;
            }
        }
    }

    private static class TimerEntry {
        final Vec3d position;
        final long startTime;
        final long duration;
        final Item item;

        TimerEntry(Vec3d position, long startTime, long duration, Item item) {
            this.position = position;
            this.startTime = startTime;
            this.duration = duration;
            this.item = item;
        }
    }

    private record ItemBind(Item item, BindSetting setting, String name) {}
}
