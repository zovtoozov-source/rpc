package tech.onetap.module.list.render;

import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "SeeInvisible", moduleCategory = ModuleCategory.RENDER)
public class SeeInvisible extends Module {

    public static SeeInvisible instance;
    public final SliderSetting alpha = new SliderSetting("Прозрачность", 50.0, 10.0, 100.0, 1.0);

    public SeeInvisible() {
        instance = this;
    }

    public float getAlphaMultiplier() {
        return alpha.getFloatValue() / 100.0f;
    }
}
