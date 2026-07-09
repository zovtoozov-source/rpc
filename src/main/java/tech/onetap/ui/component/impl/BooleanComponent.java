package tech.onetap.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.ui.component.Component;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

public class BooleanComponent extends Component {
    private final BooleanSetting setting;
    private boolean binding;

    public BooleanComponent(BooleanSetting setting) {
        this.setting = setting;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float alpha = Math.max(Math.min(getAlphaAnimSetting().getValue(), 1), 0);
        int alphaInt = (int) (255 * alpha);

        float toggleW = 15f;
        float toggleH = 8f;
        float toggleX = x + width - toggleW - 2.5f;
        float toggleY = y + 4f;

        if (HoverUtil.isHovered(mouseX, mouseY, toggleX, toggleY, toggleW, toggleH)) {
            CursorManager.requestHand();
        }

        DrawUtil.drawText(Fonts.SFREGULAR.get(), binding ? "Binding..." : setting.getName(),
                x + 4.5f, y + 5f, ColorProvider.setAlpha(ColorProvider.getColorText(), alphaInt), 6.5f, 0.4f, 1f, width - toggleW - 8f);

        float anim = (float) setting.getAnimation().getValue();

        if (anim > 0.01f) {
            // Активный индикатор - colorIndicator
            int indicator = ColorProvider.getColorIndicator();
            int darkIndicator = ColorProvider.rgba(
                Math.max(0, (int)(((indicator >> 16) & 0xFF) * 0.45f)),
                Math.max(0, (int)(((indicator >> 8) & 0xFF) * 0.45f)),
                Math.max(0, (int)((indicator & 0xFF) * 0.45f)),
                alphaInt
            );
            int lightIndicator = ColorProvider.setAlpha(indicator, alphaInt);
            // Неактивный индикатор - colorInactiveIndicator
            int inactiveIndicator = ColorProvider.setAlpha(ColorProvider.getColorInactiveIndicator(), alphaInt);
            int bgLeft = ColorProvider.interpolateColor(inactiveIndicator, darkIndicator, anim);
            int bgRight = ColorProvider.interpolateColor(inactiveIndicator, lightIndicator, anim);
            DrawUtil.drawRound(toggleX, toggleY, toggleW, toggleH, 1.5f, bgLeft, bgRight);
        } else {
            // Неактивный индикатор - colorInactiveIndicator
            DrawUtil.drawRound(toggleX, toggleY, toggleW, toggleH, 1.5f, ColorProvider.setAlpha(ColorProvider.getColorInactiveIndicator(), alphaInt));
        }

        float knobSize = toggleH - 1f;
        float knobMinX = toggleX + 0.5f;
        float knobMaxX = toggleX + toggleW - knobSize - 0.5f;
        float knobX = knobMinX + (knobMaxX - knobMinX) * anim;
        float knobY = toggleY + 0.5f;
        // Круг слайдера используем для кнопки тогла
        DrawUtil.drawRound(knobX, knobY, knobSize, knobSize, 1.5f, ColorProvider.setAlpha(ColorProvider.getColorSliderCircle(), alphaInt));

        setHeight(16);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        float toggleW = 15f;
        float toggleH = 8f;
        float toggleX = x + width - toggleW - 2.5f;
        float toggleY = y + 4f;

        if (HoverUtil.isHovered(mouseX, mouseY, toggleX, toggleY, toggleW, toggleH)) {
            if (button == 0) setting.setValue(!setting.getValue());
            if (button == 2 && binding) {
                binding = false;
                return;
            }
            if (binding) {
                setting.setKey(button);
                binding = false;
            }
            if (button == 2) binding = true;
        }
    }

    @Override
    public float getPreferredHeight(float width) {
        return 16f;
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                binding = false;
                return;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                setting.setKey(-1);
                return;
            }
            setting.setKey(keyCode);
            binding = false;
        }
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}
