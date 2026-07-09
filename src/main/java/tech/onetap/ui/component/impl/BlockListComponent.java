package tech.onetap.ui.component.impl;

import net.minecraft.block.Block;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import tech.onetap.module.settings.BlockListSetting;
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

public class BlockListComponent extends Component {
    private final BlockListSetting setting;
    private boolean opened;
    private final Animation openAnim = new Animation(Easing.QUINTIC_OUT, 300);
    private final float ITEM_HEIGHT = 11f;

    public BlockListComponent(BlockListSetting setting) {
        this.setting = setting;
    }

    private List<Block> getItems() {
        List<Block> items = setting.candidates();
        if (items != null) return items;
        return setting.all();
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float animValue = getAlphaAnimSetting().getValue();
        float alpha = Math.max(Math.min(animValue * getAlphaAnim().getValue(), 1), 0);
        int alphaInt = (int) (255 * alpha);

        openAnim.run(opened);

        List<Block> items = getItems();

        float dropHeight = items.size() * ITEM_HEIGHT;
        float btnY = y + 9.5f;
        float btnHeight = 11f;
        float animHeight = openAnim.getValue() * dropHeight;
        float totalBoxHeight = btnHeight + animHeight;

        setHeight((22 + animHeight) * animValue);

        if (alpha < 0.02f) return;

        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + 4.5f, y + 1.5f, ColorProvider.rgba(255, 255, 255, alphaInt), 6.5f);

        if (HoverUtil.isHovered(mouseX, mouseY, x + 4, btnY, width - 8, btnHeight)) CursorManager.requestHand();

        DrawUtil.drawRound(x + 3.5f, btnY - 0.5f, width - 7, totalBoxHeight + 2.5f, 2.5f, ColorProvider.rgba(60, 60, 65, (int)(150 * alpha)));
        DrawUtil.drawRound(x + 4, btnY, width - 8, totalBoxHeight + 1.5f, 2.5f, ColorProvider.rgba(20, 20, 25, alphaInt));

        String textClosed = "Блоков: " + setting.all().size();
        String textOpened = "...";

        int textAlphaClosed = (int) (alphaInt * (1f - openAnim.getValue()));
        int textAlphaOpened = (int) (alphaInt * openAnim.getValue());

        if (textAlphaClosed > 0) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), textClosed, x + 7, btnY + 2.5f, ColorProvider.rgba(200, 200, 200, textAlphaClosed), 6.5f);
        }
        if (textAlphaOpened > 0) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), textOpened, x + 7, btnY + 2.5f, ColorProvider.rgba(200, 200, 200, textAlphaOpened), 6.5f);
        }

        if (openAnim.getValue() > 0.01f) {
            float listY = btnY + btnHeight;
            float currentY = listY;
            for (var blockObj : items) {
                boolean selected = setting.has(blockObj);
                if (HoverUtil.isHovered(mouseX, mouseY, x + 4, currentY, width - 8, ITEM_HEIGHT)) {
                    DrawUtil.drawRound(x + 4.5f, currentY, width - 9, ITEM_HEIGHT, 0, ColorProvider.rgba(255, 255, 255, (int)(15 * alpha)));
                }

                Identifier id = Registries.BLOCK.getId(blockObj);
                String name = id != null ? id.getPath() : "?";
                int textColor = selected ? ColorProvider.getThemeColor() : ColorProvider.rgba(180, 180, 180, 255);
                DrawUtil.drawText(Fonts.SFREGULAR.get(), name, x + 7, currentY + 2f, ColorProvider.setAlpha(textColor, (int)(255 * alpha * openAnim.getValue())), 6.5f);

                currentY += ITEM_HEIGHT;
            }
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;

        float btnY = y + 9.5f;
        float btnHeight = 11f;

        if (HoverUtil.isHovered(mouseX, mouseY, x + 4, btnY, width - 8, btnHeight)) {
            opened = !opened;
            return;
        }

        if (opened) {
            List<Block> items = getItems();
            float listY = btnY + btnHeight;
            float currentY = listY;

            for (var block : items) {
                if (HoverUtil.isHovered(mouseX, mouseY, x + 4, currentY, width - 8, ITEM_HEIGHT)) {
                    if (setting.has(block)) {
                        setting.remove(block);
                    } else {
                        setting.add(block);
                    }
                    break;
                }
                currentY += ITEM_HEIGHT;
            }
        }
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}
