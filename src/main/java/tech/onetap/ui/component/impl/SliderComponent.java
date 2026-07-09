package tech.onetap.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.ui.component.Component;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SliderComponent extends Component {
    private final SliderSetting setting;
    private boolean drag;
    private final Animation sliderAnimation = new Animation(Easing.QUINTIC_OUT, 100);

    public SliderComponent(SliderSetting setting) {
        this.setting = setting;
    }

    private double round(double num, double increment) {
        var v = (double) Math.round(num / increment) * increment;
        return new BigDecimal(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatNumber(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float alpha = Math.min(getAlphaAnimSetting().getValue(), 1);
        int alphaInt = (int) (255 * alpha);

        // ИСПРАВЛЕНИЕ ЗДЕСЬ: Убрали вызов getUnit(), теперь выводится только чистое число
        String numberText = formatNumber(setting.getValue());
        float trackWidth = width - 9f;

        sliderAnimation.run((float) (trackWidth * (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin())));

        // Название слева
        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + 4.5f, y + 3f,
                ColorProvider.setAlpha(ColorProvider.getColorText(), alphaInt), 6.5f, 0.6f, 1.0f, trackWidth);

        // Цифра справа
        DrawUtil.drawText(Fonts.SFREGULAR.get(), numberText, x + width - 4.5f - Fonts.SFREGULAR.get().getWidth(numberText, 6.5f), y + 1f,
                ColorProvider.setAlpha(ColorProvider.getColorInactiveText(), alphaInt), 6.5f);

        // Основа слайдера
        float trackY = y + 14f;
        DrawUtil.drawRound(x + 3f, trackY - 3.5f, trackWidth + 1, 4, 1f, ColorProvider.setAlpha(ColorProvider.getColorSliderWindow(), (int)(100 * alpha)));
        DrawUtil.drawRound(x + 3.5f, trackY - 3, trackWidth, 3, 1f, ColorProvider.setAlpha(ColorProvider.getColorSliderWindow(), alphaInt));

        // Заполненная часть
        float fillWidth = MathHelper.clamp(sliderAnimation.getValue(), 0, trackWidth);
        int sliderColor = ColorProvider.setAlpha(ColorProvider.getColorSlider(), alphaInt);
        int sliderDark = ColorProvider.rgba(
            (int)(((ColorProvider.getColorSlider() >> 16) & 0xFF) * 0.25f),
            (int)(((ColorProvider.getColorSlider() >> 8) & 0xFF) * 0.25f),
            (int)((ColorProvider.getColorSlider() & 0xFF) * 0.25f),
            alphaInt
        );
        DrawUtil.drawRound(x + 3.5f, trackY - 3.5f, fillWidth, 4, 1f, sliderDark, sliderColor);

        // Ползунок (Кружок)
        float circleX = x + 3.5f + fillWidth;
        DrawUtil.drawRound(circleX - 2.5f, trackY - 4f, 5, 5, 1.75f, ColorProvider.setAlpha(ColorProvider.getColorSliderCircle(), alphaInt));

        if (drag) {
            double val = (mouseX - (x + 3.5f)) / trackWidth * (setting.getMax() - setting.getMin()) + setting.getMin();
            setting.setValue((float) MathHelper.clamp(round(val, setting.getStep()), setting.getMin(), setting.getMax()));
        }

        setHeight(15);
    }

    @Override
    public float getPreferredHeight(float width) {
        return 15f;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (HoverUtil.isHovered(mouseX, mouseY, x + 3f, y + 8f, width - 6f, 8f) && button == 0) {
            drag = true;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        drag = false;
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) drag = false;
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}