package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.movement.Sprint;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.util.base.Instance;
import tech.onetap.util.packet.NetworkUtils;

@ModuleInformation(moduleName = "Sunrise Helper", moduleDesc = "Помощник для Sunrise", moduleCategory = ModuleCategory.MISC)
public class SunriseHelper extends Module {

    // я ебаный долбаеб на винеку добавил эту хуйню чтобы не использовать =)))

    private final BindSetting bindTrap = new BindSetting("Трапка", -1);
    private final BindSetting bindFire = new BindSetting("Волна огня", -1);

    private boolean throwTrap;
    private boolean throwFire;

    @Subscribe
    public void onKey(EventKeyInput e) {
        if (mc.currentScreen != null || e.getAction() == 0 || e.getAction() == 2) return;
        if (e.getKey() == bindTrap.getValue()) {
            throwTrap = true;
        }
        if (e.getKey() == bindFire.getValue()) {
            throwFire = true;
        }
    }

    @Subscribe
    public void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null) return;

        if (throwTrap) {
            useItem("трапка");
            throwTrap = false;
        }

        if (throwFire) {
            useItem("волна огня");
            throwFire = false;
        }
    }

    private void useItem(String itemName) {
        int slotHotbar = findItemSlotHotbar(itemName);
        int slotInv = findItemSlotInventory(itemName);
        int previousSlot = mc.player.getInventory().selectedSlot;

        String mainHandName = mc.player.getMainHandStack().getName().getString().toLowerCase();
        if (mainHandName.contains(itemName)) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return;
        }

        String offHandName = mc.player.getOffHandStack().getName().getString().toLowerCase();
        if (offHandName.contains(itemName)) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }

        if (slotHotbar != -1) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slotHotbar));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            return;
        }

        if (slotInv != -1) {
            int slotCorrectable = -1;
            for (int slotNone = 0; slotNone < 8; slotNone++) {
                ItemStack stack = mc.player.getInventory().getStack(slotNone);
                if (stack.isEmpty()) slotCorrectable = slotNone;
                if (stack.getUseAction() == UseAction.NONE) {
                    slotCorrectable = slotNone;
                }
            }

            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
            if (Onetap.getInstance().getServerManager().isServerSprinting()) {
                mc.player.setSprinting(false);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                if (!Instance.get(Sprint.class).isEnabled()) mc.options.sprintKey.setPressed(false);
            }

            if (slotCorrectable == -1) {
                mc.interactionManager.clickSlot(0, slotInv, 8, SlotActionType.SWAP, mc.player);
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(8));
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            } else {
                mc.interactionManager.clickSlot(0, slotInv, slotCorrectable, SlotActionType.SWAP, mc.player);
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slotCorrectable));
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                mc.interactionManager.clickSlot(0, slotInv, slotCorrectable, SlotActionType.SWAP, mc.player);
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
            }

            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
        }
    }

    private int findItemSlotHotbar(String itemName) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String name = stack.getName().getString().toLowerCase();
            if (name.contains(itemName)) {
                return i;
            }
        }
        return -1;
    }

    private int findItemSlotInventory(String itemName) {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String name = stack.getName().getString().toLowerCase();
            if (name.contains(itemName)) {
                return i;
            }
        }
        return -1;
    }
}
