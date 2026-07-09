package tech.onetap.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import tech.onetap.module.settings.StringSetting;
import tech.onetap.ui.component.Component;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

public class StringComponent extends Component {
    private static final int MAX_LENGTH = 32;

    private final StringSetting setting;
    private boolean focused;

    public StringComponent(StringSetting setting) {
        this.setting = setting;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float alpha = Math.max(Math.min(getAlphaAnimSetting().getValue(), 1), 0);
        int alphaInt = (int) (255 * alpha);
        float boxX = x + 4.5f;
        float boxY = y + 11f;
        float boxWidth = width - 9f;
        float boxHeight = 10f;

        if (HoverUtil.isHovered(mouseX, mouseY, boxX, boxY, boxWidth, boxHeight)) {
            CursorManager.requestHand();
        }

        String value = setting.getValue();
        if (value == null || value.isEmpty()) value = "...";
        if (focused) value += "_";

        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + 4.5f, y + 3f,
                ColorProvider.rgba(255, 255, 255, alphaInt), 6.5f);
        DrawUtil.drawRound(boxX - 0.5f, boxY - 0.5f, boxWidth + 1f, boxHeight + 1f, 2f,
                ColorProvider.rgba(60, 60, 65, (int) (150 * alpha)));
        DrawUtil.drawRound(boxX, boxY, boxWidth, boxHeight, 2f,
                ColorProvider.rgba(20, 20, 25, alphaInt));
        DrawUtil.drawText(Fonts.SFREGULAR.get(), value, boxX + 3f, boxY + 1.5f,
                ColorProvider.rgba(200, 200, 200, alphaInt), 6.5f, 0.4f, 1f, boxWidth - 6f);

        setHeight(24f);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        float boxX = x + 4.5f;
        float boxY = y + 11f;
        float boxWidth = width - 9f;
        focused = button == 0 && HoverUtil.isHovered(mouseX, mouseY, boxX, boxY, boxWidth, 10f);
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            focused = false;
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            String value = setting.getValue();
            if (value != null && !value.isEmpty()) {
                setting.setValue(value.substring(0, value.length() - 1));
            }
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            setting.setValue("");
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (!focused || Character.isISOControl(chr)) return;
        String value = setting.getValue();
        if (value == null) value = "";
        if (value.length() < MAX_LENGTH) {
            setting.setValue(value + chr);
        }
    }

    @Override
    public float getPreferredHeight(float width) {
        return 24f;
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}
