package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;

@ModuleInformation(moduleName = "Removals", moduleCategory = ModuleCategory.RENDER)
public class NoRender extends Module {

    public final ModeListSetting elements = new ModeListSetting("Убрать элементы",
            new BooleanSetting("Огонь",true),
            new BooleanSetting("Размытие в воде",true),
            new BooleanSetting("Зрение в блоках",true),
            new BooleanSetting("Камераклип",true),
            new BooleanSetting("Тряска камеры",true)
    );

    @Subscribe
    public void onUpdate(EventTick e) {
        if (elements.isEnabled("Тряска камеры")) {
            mc.options.getDamageTiltStrength().setValue(0.0);
        } else {
            mc.options.getDamageTiltStrength().setValue(0.5);
        }
    }

    @Override
    public void onDisable() {
        mc.options.getDamageTiltStrength().setValue(0.5);
        super.onDisable();
    }
}