package tech.onetap.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import tech.onetap.util.ClientDataPaths;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.awt.Color;
import java.io.*;
import java.nio.file.Files;

@Getter
@Setter
public class ThemeManagerWindow implements IMinecraft {
    private static final float OPEN_SLIDE_OFFSET = 18f;

    private float x, y, width = 110, height = 250;
    private boolean open;
    private final Animation openAnim   = new Animation(Easing.QUINTIC_OUT, 320);

    private final Animation scrollAnim = new Animation(Easing.CUBIC_IN_OUT, 200);
    private final Animation pickerAnim = new Animation(Easing.QUINTIC_OUT, 250);
    private final Animation presetScrollAnim = new Animation(Easing.CUBIC_IN_OUT, 180);

    private float scroll, maxScroll;
    private float presetScroll, presetMaxScroll;

    private final ClickGuiFrame parent;

    private static final File THEME_DIR = ClientDataPaths.rootDirectory();
    private static final File THEME_FILE = ClientDataPaths.file("themes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final String[] COLOR_NAMES = {
        "Основной", "Визуальные модули", "Текст", "Неактивный текст",
        "Цвет заголовков", "Текст заголовков", "Слайдер", "Круг слайдера",
        "Окно слайдера", "Индикатор", "Неактивный индикатор", "Кнопка",
        "Неактивная кнопка", "Разделитель", "Поле", "Неактивное поле",
        "Текст в подсказках", "Фон окон", "Цвет иконок", "Цвет визуала"
    };
    static final int COLOR_COUNT = COLOR_NAMES.length;

    private static final int[][] PRESETS = {
        { 0xFFAA44FF, 0xFF0D0D10 }, { 0xFF44AAFF, 0xFF0A0A18 },
        { 0xFF44FFAA, 0xFF0A1810 }, { 0xFFFF4444, 0xFF1A0808 },
        { 0xFFFF9944, 0xFF1A1008 }, { 0xFF44FFEE, 0xFF081A18 },
        { 0xFFFF44AA, 0xFF1A0810 }, { 0xFF8844FF, 0xFF100A1A },
        { 0xFFFFDD44, 0xFF1A1808 }, { 0xFF44FF44, 0xFF081A08 },
        { 0xFF4488FF, 0xFF080A1A }, { 0xFFCCCCCC, 0xFF050505 },
        { 0xFF888888, 0xFF000000 },
    };

    private final java.util.List<SavedTheme> savedThemes = new java.util.ArrayList<>();

    public record SavedThemeView(String name, int c1, int c2) {}

    private static class SavedTheme {
        String name;
        int[] colors;
        SavedTheme(String name, int[] colors) { this.name = name; this.colors = colors; }
    }

    private int[] currentColors;
    private int editingSlot = -1;
    private float[] hsv = new float[3];
    private boolean draggingSV, draggingH;
    private float pickerPopupX, pickerPopupY;

    private String themeName = "";
    private boolean nameFieldFocused = false;
    private static final float HEADER_H     = 20f;
    private static final float ROW_H        = 16f;
    private static final float PRESET_D     = 9f;
    private static final float PRESET_GAP   = 1.5f;
    private static final float PRESET_ROW_H = PRESET_D + 4f;
    private static final float FIELD_H      = 16f;
    private static final float BTN_H        = 16f;
    private static final float FIXED_BOTTOM_H = 1f + 2f + PRESET_ROW_H + 2f + 1f + 2f + FIELD_H + 2f + BTN_H + 3f;
    private static final float SV_SIZE      = 60f;
    private static final float HUE_W        = 6f;
    private static final float PICKER_PAD   = 4f;
    private static final float PICKER_W     = SV_SIZE + PICKER_PAD + HUE_W;
    private static final float PICKER_H_TOT = SV_SIZE;

    public ThemeManagerWindow(ClickGuiFrame parent) {
        this.parent = parent;
        currentColors = buildDefaultColors(0xFFAA44FF, 0xFF0D0D10);
        load();
        applyColors();
    }

    public ThemeManagerWindow() {
        this((ClickGuiFrame) null);
    }

    public static void applySavedThemeOnStartup() {
        new ThemeManagerWindow((ClickGuiFrame) null);
    }

    static int[] buildDefaultColors(int c1, int c2) {
        int[] c = new int[COLOR_COUNT];
        c[0]=c2; c[1]=c1; c[2]=0xFFE8E8E8; c[3]=0xFF888899; c[4]=0xFF0A0A0D;
        c[5]=c1; c[6]=c1; c[7]=0xFFFFFFFF; c[8]=0xFF2A2A35; c[9]=c1;
        c[10]=0xFF2A2A35; c[11]=c1; c[12]=0xFF2A2A35; c[13]=0xFF2A2A35;
        c[14]=0xFF1A1A22; c[15]=c2; c[16]=0xFFFFFFFF; c[17]=c2; c[18]=c1; c[19]=c1;
        return c;
    }

    private void applyColors() {
        tech.onetap.module.settings.impl.Theme t =
            tech.onetap.module.settings.impl.ThemeManager.getInstance().getCurrentTheme();
        int[] c = currentColors;

        t.setAllColors(c[1], c[0],
                       c[0],  c[1],  c[2],  c[3],
                       c[4],  c[5],  c[6],  c[7],
                       c[8],  c[9],  c[10], c[11],
                       c[12], c[13], c[14], c[15],
                       c[16], c[17], c[18], c[19]);
    }

    /** Применяет тему по паре цветов (для нового GUI). */
    public void applyPresetColors(int c1, int c2) {
        currentColors = buildDefaultColors(c1, c2);
        applyColors();
        save();
    }

    public java.util.List<SavedThemeView> getSavedThemeViews() {
        java.util.List<SavedThemeView> result = new java.util.ArrayList<>();
        for (SavedTheme theme : savedThemes) {
            if (theme.colors != null && theme.colors.length > 1) {
                result.add(new SavedThemeView(theme.name, theme.colors[1], theme.colors[0]));
            }
        }
        return result;
    }

    public void applySavedTheme(String name) {
        if (name == null) return;
        for (SavedTheme theme : savedThemes) {
            if (theme.name.equalsIgnoreCase(name.trim())) {
                currentColors = theme.colors.clone();
                applyColors();
                save();
                return;
            }
        }
    }

    public boolean deleteSavedTheme(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) return false;
        for (int i = 0; i < savedThemes.size(); i++) {
            if (savedThemes.get(i).name.equalsIgnoreCase(name)) {
                savedThemes.remove(i);
                save();
                return true;
            }
        }
        return false;
    }

    public boolean saveCurrentAsNamedTheme(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) return false;
        for (SavedTheme theme : savedThemes) {
            if (theme.name.equalsIgnoreCase(name)) {
                theme.name = name;
                theme.colors = currentColors.clone();
                save();
                return true;
            }
        }
        savedThemes.add(new SavedTheme(name, currentColors.clone()));
        save();
        return true;
    }

    public void save() {
        try {
            if (!THEME_DIR.exists()) THEME_DIR.mkdirs();
            JsonObject json = new JsonObject();
            JsonArray arr = new JsonArray();
            for (int col : currentColors) arr.add(col);
            json.add("colors", arr);
            JsonArray saved = new JsonArray();
            for (SavedTheme t : savedThemes) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", t.name);
                JsonArray ca = new JsonArray();
                for (int col : t.colors) ca.add(col);
                obj.add("colors", ca);
                saved.add(obj);
            }
            json.add("savedThemes", saved);
            Files.writeString(THEME_FILE.toPath(), GSON.toJson(json));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void load() {
        if (!THEME_FILE.exists()) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(THEME_FILE.toPath())).getAsJsonObject();
            if (json.has("colors")) {
                JsonArray arr = json.getAsJsonArray("colors");
                for (int i = 0; i < Math.min(arr.size(), COLOR_COUNT); i++)
                    currentColors[i] = arr.get(i).getAsInt();
            }
            if (json.has("savedThemes")) {
                savedThemes.clear();
                for (com.google.gson.JsonElement el : json.getAsJsonArray("savedThemes")) {
                    JsonObject obj = el.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    JsonArray ca = obj.getAsJsonArray("colors");
                    int[] cols = new int[COLOR_COUNT];
                    for (int i = 0; i < Math.min(ca.size(), COLOR_COUNT); i++)
                        cols[i] = ca.get(i).getAsInt();
                    savedThemes.add(new SavedTheme(name, cols));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private float scrollAreaH() {
        return height - HEADER_H - FIXED_BOTTOM_H;
    }

    private float scrollContentH() {
        return COLOR_COUNT * ROW_H + 2f;
    }

    private void clampScroll() {
        maxScroll = Math.max(0, scrollContentH() - scrollAreaH());
        if (maxScroll > 0) scroll = MathHelper.clamp(scroll, -maxScroll, 0);
        else scroll = 0;
    }

    private int allPresetsCount() {
        return PRESETS.length + savedThemes.size();
    }

    public void setOpen(boolean open) {
        if (this.open == open) return;
        this.open = open;
        if (!open) {
            editingSlot = -1;
            nameFieldFocused = false;
            draggingSV = false;
            draggingH = false;
        }
    }

    public void toggleOpen() {
        setOpen(!open);
    }

    private void clampPresetScroll() {
        float areaW = width - 8f;
        float totalW = allPresetsCount() * PRESET_D + (allPresetsCount() - 1) * PRESET_GAP;
        presetMaxScroll = Math.max(0, totalW - areaW);
        if (presetMaxScroll > 0) presetScroll = MathHelper.clamp(presetScroll, -presetMaxScroll, 0);
        else presetScroll = 0;
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        boolean shouldRenderOpen = open && (parent == null || !parent.isClosing());
        openAnim.run(shouldRenderOpen);
        if (openAnim.getValue() < 0.01f) return;

        float anim = (float) openAnim.getValue();
        float cx = x - OPEN_SLIDE_OFFSET * (1 - anim);
        int alpha = (int)(255 * anim);

        DrawUtil.drawRoundBlur(cx, y, width, height, 8f, ColorProvider.rgba(75,75,75,alpha), 20f);
        DrawUtil.drawRound(cx, y, width, height, 8f, ColorProvider.setAlpha(ColorProvider.getColorWindowBg(), (int)(30*anim)));
        DrawUtil.drawRound(cx, y, width, HEADER_H,
            new org.joml.Vector4f(8f,0,0,8f), ColorProvider.rgba(0,0,0,(int)(80*anim)));

        String title = "Theme Editor";
        float titleW = Fonts.SFREGULAR.get().getWidth(title, 8.5f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), title,
            cx + width/2f - titleW/2f, y + 6f, ColorProvider.rgba(255,255,255,alpha), 8.5f);

        clampScroll();
        clampPresetScroll();
        scrollAnim.run(scroll);
        presetScrollAnim.run(presetScroll);

        float scrollZoneY = y + HEADER_H;
        float scrollZoneH = scrollAreaH();

        Scissor.push();
        Scissor.setFromComponentCoordinates(cx, scrollZoneY, width, scrollZoneH);

        float off = (float) scrollAnim.getValue();
        float curY = scrollZoneY + 2f + off;
        for (int i = 0; i < COLOR_COUNT; i++) {
            renderRow(i, cx + 3.5f, curY, width - 7f, mouseX, mouseY, anim);
            curY += ROW_H;
        }

        Scissor.unset();
        Scissor.pop();

        float fixedY = y + height - FIXED_BOTTOM_H;

        DrawUtil.drawRound(cx+4, fixedY, width-8, 0.5f, 0f, ColorProvider.rgba(255,255,255,(int)(25*anim)));
        fixedY += 1f + 2f;
        float presetAreaX = cx + 4f;
        float presetAreaW = width - 8f;
        float presetOff = (float) presetScrollAnim.getValue();
        Scissor.push();
        Scissor.setFromComponentCoordinates(presetAreaX, fixedY, presetAreaW, PRESET_ROW_H);
        float circleX = presetAreaX + presetOff;
        float circleY = fixedY + (PRESET_ROW_H - PRESET_D) / 2f;
        for (int[] preset : PRESETS) {
            boolean hov = HoverUtil.isHovered(mouseX, mouseY, circleX, circleY, PRESET_D, PRESET_D);
            if (hov) CursorManager.requestHand();
            DrawUtil.drawRound(circleX, circleY, PRESET_D, PRESET_D, PRESET_D/2f - 0.5f,
                ColorProvider.setAlpha(preset[0], alpha));
            circleX += PRESET_D + PRESET_GAP;
        }
        for (SavedTheme st : savedThemes) {
            boolean hov = HoverUtil.isHovered(mouseX, mouseY, circleX, circleY, PRESET_D, PRESET_D);
            if (hov) CursorManager.requestHand();
            DrawUtil.drawRound(circleX, circleY, PRESET_D, PRESET_D, PRESET_D/2f - 0.5f,
                ColorProvider.setAlpha(st.colors[1], alpha));
            circleX += PRESET_D + PRESET_GAP;
        }
        Scissor.unset();
        Scissor.pop();
        fixedY += PRESET_ROW_H + 2f;

        DrawUtil.drawRound(cx+4, fixedY, width-8, 0.5f, 0f, ColorProvider.rgba(255,255,255,(int)(25*anim)));
        fixedY += 1f + 2f;

        float fieldX = cx + 4f, fieldW = width - 8f;
        int accent    = ColorProvider.setAlpha(currentColors[1], (int)(55 * anim));
        int accentHov = ColorProvider.setAlpha(currentColors[1], (int)(90 * anim));

        DrawUtil.drawRound(fieldX, fixedY, fieldW, FIELD_H, 3f, accent);
        String displayName = themeName.isEmpty() ? "Название" : themeName;
        int nameColor = themeName.isEmpty()
            ? ColorProvider.rgba(200,200,200,(int)(160*anim))
            : ColorProvider.rgba(255,255,255,(int)(255*anim));
        drawCenteredButtonText(displayName, fieldX, fixedY, fieldW, FIELD_H, nameColor, 7f);
        fixedY += FIELD_H + 2f;

        boolean btnHov = HoverUtil.isHovered(mouseX, mouseY, fieldX, fixedY, fieldW, BTN_H);
        if (btnHov) CursorManager.requestHand();
        DrawUtil.drawRound(fieldX, fixedY, fieldW, BTN_H, 3f, btnHov ? accentHov : accent);
        String btnText = "Создать тему";
        drawCenteredButtonText(btnText, fieldX, fixedY, fieldW, BTN_H,
            ColorProvider.rgba(255,255,255,(int)(255*anim)), 7f);

        pickerAnim.run(editingSlot >= 0);
        if (pickerAnim.getValue() > 0.01f)
            renderPickerPopup(pickerPopupX, pickerPopupY, mouseX, mouseY,
                (float)pickerAnim.getValue() * anim);
    }

    private void drawCenteredButtonText(String text, float x, float y, float width, float height, int color, float size) {
        float textW = Fonts.SFREGULAR.get().getWidth(text, size);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), text, x + width / 2f - textW / 2f, y + height / 2f - size / 2f - 0.8f, color, size);
    }

    private void renderRow(int slot, float rx, float ry, float rw, int mouseX, int mouseY, float anim) {
        boolean hov = HoverUtil.isHovered(mouseX, mouseY, rx, ry, rw, ROW_H);
        boolean active = editingSlot == slot;
        if (hov || active) CursorManager.requestHand();
        if (active)
            DrawUtil.drawRound(rx, ry, rw, ROW_H, 2f, ColorProvider.rgba(60,60,80,(int)(60*anim)));
        DrawUtil.drawText(Fonts.SFREGULAR.get(), COLOR_NAMES[slot],
            rx+3f, ry+4f, ColorProvider.rgba(210,210,210,(int)(255*anim)), 7f);
        float cd = 8f;
        DrawUtil.drawRound(rx+rw-cd-3f, ry+ROW_H/2f-cd/2f, cd, cd, cd/2f - 1f,
            ColorProvider.setAlpha(currentColors[slot], (int)(255*anim)));
    }

    private void renderPickerPopup(float px, float py, int mouseX, int mouseY, float alphaAnim) {
        int ai = (int)(255 * alphaAnim);
        float p = PICKER_PAD;
        DrawUtil.drawRoundBlur(px-p, py-p, PICKER_W+p*2, PICKER_H_TOT+p*2, 5f,
            ColorProvider.rgba(75,75,75,(int)(255*alphaAnim)), 20f);
        DrawUtil.drawRound(px-p, py-p, PICKER_W+p*2, PICKER_H_TOT+p*2, 5f,
            ColorProvider.rgba(14,14,16,(int)(130*alphaAnim)));

        int cHue=ColorProvider.setAlpha(Color.HSBtoRGB(hsv[0],1F,1F),ai);
        int cW=ColorProvider.rgba(255,255,255,ai), cCW=ColorProvider.rgba(255,255,255,0);
        int cB=ColorProvider.rgba(0,0,0,ai),       cCB=ColorProvider.rgba(0,0,0,0);
        DrawUtil.drawRound(px,py,SV_SIZE,SV_SIZE,2f,cHue);
        DrawUtil.drawRound(px,py,SV_SIZE,SV_SIZE,2f,cW,cW,cCW,cCW);
        DrawUtil.drawRound(px,py,SV_SIZE,SV_SIZE,2f,cCB,cB,cB,cCB);

        float scx=px+hsv[1]*SV_SIZE, scy=py+(1-hsv[2])*SV_SIZE;
        DrawUtil.drawRound(scx-3f,scy-3f,6f,6f,3f,ColorProvider.rgba(0,0,0,(int)(180*alphaAnim)));
        DrawUtil.drawRound(scx-2f,scy-2f,4f,4f,2f,ColorProvider.rgba(255,255,255,ai));

        float hueX=px+SV_SIZE+4f;
        for (float i=0;i<=SV_SIZE;i+=0.5f)
            DrawUtil.drawRound(hueX,py+i,HUE_W,1f,0f,
                ColorProvider.setAlpha(Color.HSBtoRGB(i/SV_SIZE,1F,1F),ai));
        float hcy=py+hsv[0]*SV_SIZE;
        DrawUtil.drawRound(hueX-1.5f,hcy-2.5f,HUE_W+3f,5f,2f,ColorProvider.rgba(0,0,0,(int)(180*alphaAnim)));
        DrawUtil.drawRound(hueX-0.5f,hcy-1.5f,HUE_W+1f,3f,1f,ColorProvider.rgba(255,255,255,ai));

        if (draggingSV) {
            hsv[1]=Math.max(0,Math.min(1,(mouseX-px)/SV_SIZE));
            hsv[2]=1F-Math.max(0,Math.min(1,(mouseY-py)/SV_SIZE));
            applyHSV();
        } else if (draggingH) {
            hsv[0]=Math.max(0,Math.min(1,(mouseY-py)/SV_SIZE));
            applyHSV();
        }
    }

    private void applyHSV() {
        if (editingSlot < 0) return;
        currentColors[editingSlot] = Color.HSBtoRGB(hsv[0],hsv[1],hsv[2]) | 0xFF000000;
        applyColors();
    }

    private void loadHSV(int slot) {
        Color c = new Color(currentColors[slot], true);
        Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsv);
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || openAnim.getValue() < 0.9f || parent != null && parent.isClosing()) return;

        float anim = (float) openAnim.getValue();
        float cx = x - OPEN_SLIDE_OFFSET * (1 - anim);

        if (editingSlot >= 0 && pickerAnim.getValue() > 0.5f) {

            float px = pickerPopupX, py = pickerPopupY;
            if (HoverUtil.isHovered(mouseX, mouseY, px, py, SV_SIZE, SV_SIZE) && button == 0) {
                draggingSV = true; return;
            }
            if (HoverUtil.isHovered(mouseX, mouseY, px+SV_SIZE+4, py, HUE_W+4, SV_SIZE) && button == 0) {
                draggingH = true; return;
            }
            if (!HoverUtil.isHovered(mouseX, mouseY, px-PICKER_PAD, py-PICKER_PAD, PICKER_W+PICKER_PAD*2, PICKER_H_TOT+PICKER_PAD*2)) {
                editingSlot = -1;
            }
        }

        float scrollZoneY = y + HEADER_H;
        float scrollZoneH = scrollAreaH();
        if (HoverUtil.isHovered(mouseX, mouseY, cx, scrollZoneY, width, scrollZoneH)) {
            float off = (float) scrollAnim.getValue();
            float curY = scrollZoneY + 2f + off;
            for (int i = 0; i < COLOR_COUNT; i++) {
                float rx = cx + 3.5f, rw = width - 7f;
                if (HoverUtil.isHovered(mouseX, mouseY, rx, curY, rw, ROW_H) && button == 0) {
                    editingSlot = (editingSlot == i) ? -1 : i;
                    if (editingSlot >= 0) {
                        loadHSV(i);
                        pickerPopupX = cx + width + 4f;
                        pickerPopupY = curY + ROW_H/2f - PICKER_H_TOT/2f;
                    }
                    return;
                }
                curY += ROW_H;
            }
        }

        float fixedY = y + height - FIXED_BOTTOM_H + 1f + 2f;

        float presetAreaX = cx + 4f;
        float presetAreaW = width - 8f;
        float presetOff = (float) presetScrollAnim.getValue();
        float circleX = presetAreaX + presetOff;
        float circleY = fixedY + (PRESET_ROW_H - PRESET_D) / 2f;
        for (int[] preset : PRESETS) {
            if (HoverUtil.isHovered(mouseX, mouseY, circleX, circleY, PRESET_D, PRESET_D)
                    && circleX >= presetAreaX && circleX + PRESET_D <= presetAreaX + presetAreaW
                    && button == 0) {
                currentColors = buildDefaultColors(preset[0], preset[1]);
                applyColors(); save(); return;
            }
            circleX += PRESET_D + PRESET_GAP;
        }
        for (SavedTheme st : savedThemes) {
            if (HoverUtil.isHovered(mouseX, mouseY, circleX, circleY, PRESET_D, PRESET_D)
                    && circleX >= presetAreaX && circleX + PRESET_D <= presetAreaX + presetAreaW
                    && button == 0) {
                currentColors = st.colors.clone();
                applyColors(); save(); return;
            }
            circleX += PRESET_D + PRESET_GAP;
        }
        fixedY += PRESET_ROW_H + 2f + 1f + 2f;

        float fieldX = cx + 4f, fieldW = width - 8f;
        nameFieldFocused = HoverUtil.isHovered(mouseX, mouseY, fieldX, fixedY, fieldW, FIELD_H) && button == 0;
        fixedY += FIELD_H + 2f;

        if (HoverUtil.isHovered(mouseX, mouseY, fieldX, fixedY, fieldW, BTN_H) && button == 0) {
            String name = themeName.trim().isEmpty() ? "Theme " + (savedThemes.size() + 1) : themeName.trim();
            savedThemes.add(new SavedTheme(name, currentColors.clone()));
            save();
            themeName = "";
            nameFieldFocused = false;
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSV || draggingH) save();
        draggingSV = false; draggingH = false;
    }

    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!open || parent != null && parent.isClosing()) return;
        float anim = (float) openAnim.getValue();
        float cx = x - OPEN_SLIDE_OFFSET * (1 - anim);

        float presetFixedY = y + height - FIXED_BOTTOM_H + 1f + 2f;
        if (HoverUtil.isHovered(mouseX, mouseY, cx + 4f, presetFixedY, width - 8f, PRESET_ROW_H)) {
            presetScroll += (float)(verticalAmount * 12f);
            clampPresetScroll();
            return;
        }

        if (HoverUtil.isHovered(mouseX, mouseY, cx, y + HEADER_H, width, scrollAreaH())) {
            scroll += (float)(verticalAmount * 20f);
            clampScroll();
        }
    }

    public void charTyped(char chr) {
        if (!open || !nameFieldFocused) return;

        if (chr >= 32 && themeName.length() < 24) themeName += chr;
    }

    public void keyPressed(int keyCode) {
        if (!open || !nameFieldFocused) return;

        if (keyCode == 259 && !themeName.isEmpty())
            themeName = themeName.substring(0, themeName.length() - 1);
        if (keyCode == 256) nameFieldFocused = false;
    }
}
