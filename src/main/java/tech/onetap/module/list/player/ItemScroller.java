package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import tech.onetap.event.list.EventHandledScreen;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.math.StopWatch;

@ModuleInformation(moduleName = "ItemScroller", moduleCategory = ModuleCategory.PLAYER)
public class ItemScroller extends Module {
    private final StopWatch delay = new StopWatch();

    @Subscribe
    public void onHandledScreen(EventHandledScreen event) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        Slot slot = event.getSlotHover();
        if (slot == null || !slot.hasStack() || !slot.isEnabled()) return;
        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) return;
        if (!isShiftHeld()) return;
        if (!delay.isReached(80)) return;
        delay.reset();

        mc.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                slot.id,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
        );
    }

    private boolean isShiftHeld() {
        long handle = mc.getWindow().getHandle();
        return InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT) ||
               InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}
