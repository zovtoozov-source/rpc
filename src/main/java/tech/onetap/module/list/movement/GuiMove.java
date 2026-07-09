package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.movement.Sprint;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.util.base.Instance;
import tech.onetap.util.player.move.MoveUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ModuleInformation(moduleName = "GuiMove", moduleCategory = ModuleCategory.MOVEMENT)
public class GuiMove extends Module {

    public final BooleanSetting noMoveInChest = new BooleanSetting("Не двигаться в сундуках", false);

    private int pauseTicks = 0;
    private Screen lastScreen = null;
    private final List<ClickSlotC2SPacket> pendingPackets = new CopyOnWriteArrayList<>();
    private boolean sendingPackets = false;

    @Subscribe
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) return;

        if (lastScreen instanceof InventoryScreen && !(mc.currentScreen instanceof InventoryScreen)) {
            onInventoryClosed();
        }
        lastScreen = mc.currentScreen;

        final KeyBinding[] moveKeys = {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
                mc.options.sprintKey
        };

        if (pauseTicks > 0) {
            for (KeyBinding keyBinding : moveKeys) {
                keyBinding.setPressed(false);
            }
            pauseTicks--;
            return;
        }

        long handle = mc.getWindow().getHandle();
        for (KeyBinding keyBinding : moveKeys) {
            boolean pressed = InputUtil.isKeyPressed(handle, keyBinding.getDefaultKey().getCode());
            keyBinding.setPressed(pressed);
        }

        if (mc.currentScreen == null) return;
        if (mc.currentScreen instanceof ChatScreen) return;
        if (mc.currentScreen instanceof SignEditScreen) return;
        if (noMoveInChest.getValue() && mc.currentScreen instanceof GenericContainerScreen) return;
    }

    @Subscribe
    public void onPacket(EventPacket event) {
        if (mc.player == null) return;
        if (event.getType() != EventPacket.Type.SEND) return;
        if (sendingPackets) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ClickSlotC2SPacket clickPacket) {
            if (MoveUtil.hasPlayerMovement() && mc.currentScreen instanceof InventoryScreen) {
                pendingPackets.add(clickPacket);
                event.cancelEvent();
            }
        }

        if (packet instanceof CloseHandledScreenC2SPacket) {
            if (!pendingPackets.isEmpty() && MoveUtil.hasPlayerMovement()) {
                event.cancelEvent();
                sendPendingPackets();
            }
        }
    }

    private void sendPendingPackets() {
        if (pendingPackets.isEmpty()) return;

        List<ClickSlotC2SPacket> packetsToSend = new ArrayList<>(pendingPackets);
        pendingPackets.clear();

        // Сбрасываем спринт сразу на главном потоке, до задержки
        if (mc.player != null && mc.player.networkHandler != null
                && Onetap.getInstance().getServerManager().isServerSprinting()) {
            mc.player.setSprinting(false);
            mc.player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING)
            );
            Sprint sprintModule = Instance.get(Sprint.class);
            if (sprintModule == null || !sprintModule.isEnabled()) {
                mc.options.sprintKey.setPressed(false);
            }
        }

        pauseTicks = 10; // ~500мс блокировки движения на главном потоке

        new Thread(() -> {
            // Ждём 200мс чтобы сервер обработал сброс спринта
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            sendingPackets = true;

            for (ClickSlotC2SPacket pkt : packetsToSend) {
                if (mc.player == null || mc.player.networkHandler == null) break;

                mc.player.networkHandler.sendPacket(pkt);

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            sendingPackets = false;

            if (mc.player != null && mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            }
        }, "GuiMove-PacketSender").start();
    }

    private void onInventoryClosed() {
        if (!pendingPackets.isEmpty()) {
            sendPendingPackets();
        }
    }

    public void stopMovementTemporarily(float timeInSeconds) {
        if (!isEnabled()) return;
        int ticks = Math.max(1, (int) Math.ceil(timeInSeconds * 20.0f));
        pauseTicks = Math.max(pauseTicks, ticks);
    }

    public static void stopMovement(float timeInSeconds) {
        GuiMove gm = Instance.get(GuiMove.class);
        if (gm != null) gm.stopMovementTemporarily(timeInSeconds);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        pauseTicks = 3;
        lastScreen = mc.currentScreen;
        pendingPackets.clear();
        sendingPackets = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        pauseTicks = 0;
        lastScreen = null;
        pendingPackets.clear();
        sendingPackets = false;
    }
}
