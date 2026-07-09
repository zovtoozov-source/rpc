package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import tech.onetap.Onetap;
import tech.onetap.event.list.ChatEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "Panic", moduleCategory = ModuleCategory.MISC)
public final class Panic extends Module {
    private final List<Module> disabledModules = new ArrayList<>();

    @Override
    public void onEnable() {
        super.onEnable();
        PanicFlag.BLOCKING = true;
        if (mc.player != null && mc.currentScreen != null) {
            mc.player.closeScreen();
        }
        disabledModules.clear();
        for (Module module : Onetap.getInstance().getModuleStorage().getModules()) {
            if (module != this && module.isEnabled()) {
                disabledModules.add(module);
                module.setEnabled(false);
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        PanicFlag.BLOCKING = false;
        for (Module module : disabledModules) {
            if (!module.isEnabled()) {
                module.setEnabled(true);
            }
        }
        disabledModules.clear();
    }

    @Subscribe
    private void onChat(ChatEvent e) {
        if (PanicFlag.BLOCKING) {
            e.setCancelled(true);
        }
    }
}
