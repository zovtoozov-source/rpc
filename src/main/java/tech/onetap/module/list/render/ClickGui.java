package tech.onetap.module.list.render;

import org.lwjgl.glfw.GLFW;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.misc.PanicFlag;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.ui.ClickGuiFrame;
import tech.onetap.ui.Panel;
import tech.onetap.ui.newgui.NewClickGuiCloseOverlay;
import tech.onetap.ui.newgui.NewClickGuiFrame;

@ModuleInformation(moduleName = "Click Gui", moduleCategory = ModuleCategory.RENDER, moduleKeybind = GLFW.GLFW_KEY_RIGHT_SHIFT)
public class ClickGui extends Module {

    private final ModeSetting mode = new ModeSetting("Mode", "Space", "Moon", "Space");
    private ClickGuiFrame moonFrame;
    private NewClickGuiFrame spaceFrame;

    public static void playNewGuiCloseOverlay(float x, float y, float width, float height) {
        NewClickGuiCloseOverlay.startStatic(x, y, width, height);
    }

    public static void renderNewGuiCloseOverlay(tech.onetap.event.list.EventHUD event) {
        NewClickGuiCloseOverlay.startStatic(0, 0, 0, 0); // Will be called from EventHUD
    }

    @Override
    public void onEnable() {
        if (mc == null || !mc.isRunning()) {
            toggle();
            return;
        }
        if (PanicFlag.BLOCKING) {
            toggle();
            return;
        }
        if (mode.is("Moon")) {
            if (moonFrame == null) moonFrame = new ClickGuiFrame();
            for (Panel panel : moonFrame.getPanels()) {
                panel.getAnimationAlpha().setValue(0);
                panel.getAnimationAlpha().reset();
            }
            mc.setScreen(moonFrame);
        } else {
            if (spaceFrame == null) spaceFrame = new NewClickGuiFrame();
            mc.setScreen(spaceFrame);
        }
        toggle();
    }
}
