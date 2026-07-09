package tech.onetap.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.ui.component.Component;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.List;

public class ModeComponent extends Component {
    private final ModeSetting setting;
    private static final float NAME_HEIGHT = 10f;
    private static final float OPTION_H = 11f;
    private static final float GAP = 2f;
    private static final float PADDING = 4.5f;
    private static final float RADIUS = 1f;

    private final List<Animation> anims = new ArrayList<>();

    public ModeComponent(ModeSetting setting) {
        this.setting = setting;
        for (int i = 0; i < setting.getModes().size(); i++) {
            anims.add(new Animation(Easing.QUINTIC_OUT, 250));
        }
    }

    private float optionWidth(String mode) {
        return Fonts.SFREGULAR.get().getWidth(mode, 6.5f) + 8f;
    }

    private float calcHeight(float componentWidth) {
        float maxRowWidth = componentWidth - PADDING * 2;
        float rowWidth = 0;
        float totalHeight = NAME_HEIGHT + GAP;
        boolean firstInRow = true;

        for (String mode : setting.getModes()) {
            float ow = optionWidth(mode);
            if (!firstInRow && rowWidth + ow > maxRowWidth) {
                totalHeight += OPTION_H + GAP;
                rowWidth = ow + GAP;
            } else {
                rowWidth += ow + GAP;
                firstInRow = false;
            }
        }
        totalHeight += OPTION_H;
        return totalHeight;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float animValue = getAlphaAnimSetting().getValue();
        float alpha = Math.max(Math.min(animValue * getAlphaAnim().getValue(), 1), 0);
        int alphaInt = (int) (255 * alpha);

        if (alpha < 0.02f) return;

        float totalHeight = calcHeight(width);
        setHeight((totalHeight + 2f) * animValue);

        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + PADDING, y + 1.5f,
                ColorProvider.setAlpha(ColorProvider.getColorText(), alphaInt), 6.5f);

        float maxRowWidth = width - PADDING * 2;
        float curX = x + PADDING;
        float curY = y + NAME_HEIGHT + GAP;
        boolean firstInRow = true;
        int i = 0;

        for (String mode : setting.getModes()) {
            float ow = optionWidth(mode);
            if (!firstInRow && curX + ow > x + width - PADDING) {
                curX = x + PADDING;
                curY += OPTION_H + GAP;
            }
            firstInRow = false;

            boolean selected = setting.is(mode);
            Animation anim = anims.get(i);
            anim.run(selected);
            float av = (float) anim.getValue();

            if (HoverUtil.isHovered(mouseX, mouseY, curX, curY, ow, OPTION_H))
                CursorManager.requestHand();

            // фон кнопки - colorButton / colorInactiveButton
            int bgColor = ColorProvider.interpolateColor(
                    ColorProvider.setAlpha(ColorProvider.getColorInactiveButton(), alphaInt),
                    ColorProvider.setAlpha(ColorProvider.getColorButton(), alphaInt),
                    av
            );
            DrawUtil.drawRound(curX, curY, ow, OPTION_H, RADIUS, bgColor);

            // текст - colorText / colorInactiveText
            int textColor = ColorProvider.interpolateColor(
                    ColorProvider.setAlpha(ColorProvider.getColorInactiveText(), alphaInt),
                    ColorProvider.setAlpha(ColorProvider.getColorText(), alphaInt),
                    av
            );
            float tw = Fonts.SFREGULAR.get().getWidth(mode, 6.5f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), mode, curX + (ow - tw) / 2f, curY + 1.75f, textColor, 6.5f);

            curX += ow + GAP;
            i++;
        }
    }

    @Override
    public float getPreferredHeight(float width) {
        return calcHeight(width) + 2f;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;

        float maxRowWidth = width - PADDING * 2;
        float curX = x + PADDING;
        float curY = y + NAME_HEIGHT + GAP;
        boolean firstInRow = true;

        for (String mode : setting.getModes()) {
            float ow = optionWidth(mode);
            if (!firstInRow && curX + ow > x + width - PADDING) {
                curX = x + PADDING;
                curY += OPTION_H + GAP;
            }
            firstInRow = false;

            if (HoverUtil.isHovered(mouseX, mouseY, curX, curY, ow, OPTION_H)) {
                setting.setValue(mode);
                return;
            }
            curX += ow + GAP;
        }
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}
