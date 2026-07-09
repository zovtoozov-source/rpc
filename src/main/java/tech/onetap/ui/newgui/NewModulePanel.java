package tech.onetap.ui.newgui;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import tech.onetap.Onetap;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.settings.*;
import tech.onetap.ui.component.Component;
import tech.onetap.ui.component.impl.*;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public class NewModulePanel {

    public final ModuleCategory category;
    private final NewClickGuiFrame parent;
    private final List<Entry> entries = new ArrayList<>();

    private static final int COLS = 2;
    private static final float COL_GAP = 5f;

    public NewModulePanel(ModuleCategory category, NewClickGuiFrame parent) {
        this.category = category;
        this.parent = parent;
        Onetap.getInstance().getModuleStorage().getModules().stream()
                .filter(m -> m.getCategory() == category)
                .sorted(Comparator.comparing(m -> m.getName().toLowerCase()))
                .forEach(m -> entries.add(new Entry(m)));
    }

    /** Возвращает maxScroll. */
    public float render(DrawContext ctx, float x, float y, float w, float h,
                        int mouseX, int mouseY, float a, float scroll) {
        float used = layoutAndRender(ctx, x, y, w, h, mouseX, mouseY, a, y + scroll, null);
        return Math.max(0, used - h);
    }

    public void renderFlowColumns(DrawContext ctx, float x, float y, float w, float h,
                                  int mouseX, int mouseY, float a, float startY, float[] colY) {
        layoutAndRender(ctx, x, y, w, h, mouseX, mouseY, a, startY, colY);
    }

    private float layoutAndRender(DrawContext ctx, float x, float y, float w, float h,
                                  int mouseX, int mouseY, float a, float flowStartY, float[] sharedColY) {
        float colW = (w - COL_GAP * (COLS - 1)) / COLS;
        float childWidth = colW - 6f;

        float[] colY = sharedColY != null ? sharedColY : new float[COLS];
        if (sharedColY == null) {
            for (int i = 0; i < COLS; i++) colY[i] = flowStartY;
        }

        Scissor.push();
        Scissor.setFromComponentCoordinates(x, y, w, h);

        for (Entry entry : entries) {
            if (parent.searchCheck(entry.module.getName())) continue;

            int col = 0;
            for (int i = 1; i < COLS; i++) if (colY[i] < colY[col]) col = i;

            float cx = x + col * (colW + COL_GAP);
            float cyPos = colY[col];

            // Тикаем анимации видимости для КАЖДОГО компонента (даже за экраном)
            // чтобы значения были актуальными для расчёта высоты
            for (Component comp : entry.components) {
                comp.getAlphaAnimSetting().run(comp.isVisible());
                comp.setWidth(childWidth);
            }

            // Считаем высоту ЧЕРЕЗ ТЕ ЖЕ значения что используются при рендере
            float compH = entry.calcHeight(childWidth);

            if (cyPos + compH >= y && cyPos <= y + h) {
                // фон карточки
                drawModuleCard(cx, cyPos, colW, compH, a);
                // рендер содержимого
                entry.render(ctx.getMatrices(), cx, cyPos, colW, compH, mouseX, mouseY, a, childWidth);
            }

            colY[col] += compH + 5f;
        }

        Scissor.setFromComponentCoordinates(x, y, w, h);
        Scissor.unset();
        Scissor.pop();

        float maxColY = flowStartY;
        for (float cyv : colY) if (cyv > maxColY) maxColY = cyv;
        return maxColY - flowStartY;
    }

    private void drawModuleCard(float x, float y, float w, float h, float a) {
        int bg = ColorProvider.getColorWindowBg();
        int accent = ColorProvider.getColorVisualModules();

        DrawUtil.drawRoundBlur(x, y + 1f, w, h, 4f,
                ColorProvider.rgba(6, 6, 10, (int) (70 * a)), 12f);
        DrawUtil.drawRound(x - 0.5f, y - 0.5f, w + 1f, h + 1f, 4.5f,
                ColorProvider.rgba(255, 255, 255, (int) (16 * a)));
        int top = ColorProvider.setAlpha(ColorProvider.interpolateColor(bg, accent, 0.16f), (int) (170 * a));
        int bottom = ColorProvider.setAlpha(bg, (int) (145 * a));
        DrawUtil.drawRound(x, y, w, h, 4f, top, top, bottom, bottom);
        DrawUtil.drawRound(x + 2f, y + 1f, w - 4f, 0.6f, 0.3f,
                ColorProvider.rgba(255, 255, 255, (int) (30 * a)));
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        for (Entry entry : entries) {
            if (parent.searchCheck(entry.module.getName())) continue;
            entry.mouseClicked(mouseX, mouseY, button);
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (Entry entry : entries) entry.mouseReleased(mouseX, mouseY, button);
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        for (Entry entry : entries) entry.keyPressed(keyCode, scanCode, modifiers);
    }

    public void charTyped(char chr, int modifiers) {
        for (Entry entry : entries) entry.charTyped(chr, modifiers);
    }

    // ─────────────────────────────────────────────────────────────────────────

    static class Entry {
        final Module module;
        final ObjectArrayList<Component> components = new ObjectArrayList<>();
        final Animation hoverAnim   = new Animation(Easing.QUINTIC_OUT, 250);
        final Animation enabledAnim = new Animation(Easing.QUINTIC_OUT, 300);
        boolean open;
        boolean binding;
        boolean hovered;

        Entry(Module module) {
            this.module = module;
            for (Setting setting : module.getSettings()) {
                switch (setting) {
                    case BooleanSetting opt   -> components.add(new BooleanComponent(opt));
                    case ModeSetting opt      -> components.add(new ModeComponent(opt));
                    case ModeListSetting opt  -> components.add(new ModeListComponent(opt));
                    case SliderSetting opt    -> components.add(new SliderComponent(opt));
                    case BindSetting opt      -> components.add(new BindComponent(opt));
                    case StringSetting opt    -> components.add(new StringComponent(opt));
                    case ThemeSetting opt     -> components.add(new ThemeComponent(opt));
                    case ColorSetting opt     -> components.add(new ColorPickerComponent(opt));
                    case BlockListSetting opt -> components.add(new BlockListComponent(opt));
                    default -> {}
                }
            }
        }

        /** Считает высоту карточки. Должна вызываться ПОСЛЕ tick анимаций компонентов. */
        float calcHeight(float childWidth) {
            float h = 19f;
            for (Component comp : components) {
                if (!comp.isVisible()) {
                    // Компонент скрыт — тикаем анимацию к 0 и учитываем fade
                    float vis = MathHelper.clamp((float) comp.getAlphaAnimSetting().getValue(), 0f, 1f);
                    if (vis > 0f) h += comp.getPreferredHeight(childWidth) * vis;
                } else {
                    h += comp.getPreferredHeight(childWidth);
                }
            }
            return h;
        }

        void render(MatrixStack matrices, float x, float y, float w, float h,
                    int mouseX, int mouseY, float a, float childWidth) {
            hovered = HoverUtil.isHovered(mouseX, mouseY, x, y, w, 19f);
            hoverAnim.run(hovered);
            enabledAnim.run(module.isEnabled());
            if (hovered) CursorManager.requestHand();

            int alpha = (int)(255 * a);

            // Фон модуля — интерполяция colorMain ↔ colorVisualModules на всю карточку
            int innerColor = ColorProvider.interpolateColor(
                    ColorProvider.setAlpha(ColorProvider.getColorMain(), (int)(70 * a)),
                    ColorProvider.setAlpha(ColorProvider.getColorVisualModules(), (int)(50 * a)),
                    (float) enabledAnim.getValue()
            );
            DrawUtil.drawRound(x, y, w, h, 3f, innerColor);

            if (hoverAnim.getValue() > 0.01f) {
                DrawUtil.drawRound(x, y, w, h, 4f,
                        ColorProvider.rgba(255, 255, 255, (int)(10 * hoverAnim.getValue() * a)));
            }

            String text = binding ? "Нажмите клавишу..." : module.getName();
            int textColor = ColorProvider.interpolateColor(
                    ColorProvider.setAlpha(ColorProvider.getColorInactiveText(), alpha),
                    ColorProvider.setAlpha(ColorProvider.getColorText(), alpha),
                    (float) enabledAnim.getValue());
            DrawUtil.drawText(Fonts.SFREGULAR.get(), text, x + 4.5f, y + 5.25f, textColor, 7.5f);

            float cy = y + 19f;
            for (Component comp : components) {
                // alphaAnimSetting уже тикнут снаружи
                float vis = MathHelper.clamp((float) comp.getAlphaAnimSetting().getValue(), 0f, 1f);
                if (vis > 0f) {
                    comp.getAlphaAnim().setValue(a);
                    comp.setX(x + 3f);
                    comp.setY(cy);
                    comp.setWidth(childWidth);
                    comp.render(matrices, mouseX, mouseY, 0f);
                    cy += comp.getPreferredHeight(childWidth) * vis;
                }
            }
        }

        void mouseClicked(double mouseX, double mouseY, int button) {
            if (hovered) {
                if (button == 0) module.setEnabled(!module.isEnabled());
                if (button == 2) binding = !binding;
            }
            for (Component comp : components) comp.mouseClicked(mouseX, mouseY, button);
        }

        void mouseReleased(double mouseX, double mouseY, int button) {
            for (Component comp : components) comp.mouseReleased(mouseX, mouseY, button);
        }

        void keyPressed(int keyCode, int scanCode, int modifiers) {
            if (binding) {
                module.setKey(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode);
                binding = false;
                return;
            }
            for (Component comp : components) comp.keyPressed(keyCode, scanCode, modifiers);
        }

        void charTyped(char chr, int modifiers) {
            for (Component comp : components) comp.charTyped(chr, modifiers);
        }
    }
}
