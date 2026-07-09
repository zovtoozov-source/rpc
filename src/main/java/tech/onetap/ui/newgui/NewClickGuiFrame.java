package tech.onetap.ui.newgui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import tech.onetap.Onetap;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.list.render.ClickGui;
import tech.onetap.module.settings.impl.ThemeManager;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.config.ConfigManager;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.friend.Friend;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.render.builders.Builder;
import tech.onetap.util.render.builders.states.QuadColorState;
import tech.onetap.util.render.builders.states.QuadRadiusState;
import tech.onetap.util.render.builders.states.SizeState;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.render.renderers.GuiMetanoiseRenderer;
import tech.onetap.util.render.stencil.StencilUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class NewClickGuiFrame extends Screen implements IMinecraft {

    private static final float SIDEBAR_W = 96f;
    private static final float TOP_H = 24f;
    private static final float PAD = 6f;
    private static final float SECTION_GAP = 6f;
    private static final float RADIUS = 8f;
    private static final float INNER_RADIUS = 5f;
    private static final int METANOISE_PANEL_COLOR = 0xD30A0B16;
    private static final long METANOISE_OPEN_DURATION_MS = 1750L;

    private enum Tab {
        COMBAT("Combat", "a", ModuleCategory.COMBAT, "General"),
        MOVEMENT("Movement", "b", ModuleCategory.MOVEMENT, "General"),
        RENDER("Render", "c", ModuleCategory.RENDER, "General"),
        PLAYER("Player", "d", ModuleCategory.PLAYER, "General"),
        MISC("Misc", "e", ModuleCategory.MISC, "General"),
        FRIENDS("Friends", "e", null, "Misc"),
        CONFIGS("Configs", "e", null, "Misc"),
        INTERFACE("Interface", "c", null, "Misc"),
        EVENTS("Events", "e", null, "Misc");

        final String label;
        final String icon;
        final ModuleCategory category;
        final String section;

        Tab(String label, String icon, ModuleCategory category, String section) {
            this.label = label;
            this.icon = icon;
            this.category = category;
            this.section = section;
        }
    }

    private Tab activeTab = Tab.COMBAT;
    private final EventsTab eventsTab = new EventsTab();
    private String searchText = "";
    private boolean searchFocused;
    private float contentScroll, targetScroll, contentMaxScroll;
    private final Animation metanoiseAnimation = new Animation(Easing.CIRC_OUT, METANOISE_OPEN_DURATION_MS);
    private boolean closing;
    private float gx, gy, gw, gh;
    private float contentX, contentY, contentW, contentH;
    private final List<NewModulePanel> categoryPanels = new ArrayList<>();

    public NewClickGuiFrame() {
        super(Text.of("SpaceVisuals"));
        for (ModuleCategory cat : ModuleCategory.values()) {
            categoryPanels.add(new NewModulePanel(cat, this));
        }
    }

    @Override
    protected void init() {
        metanoiseAnimation.reset(0f);
        metanoiseAnimation.run(1f);
        closing = false;
        categoryPanels.clear();
        for (ModuleCategory cat : ModuleCategory.values()) {
            categoryPanels.add(new NewModulePanel(cat, this));
        }
        tech.onetap.util.sound.GuiSoundUtil.playMenuOpen();
    }

    @Override
    public void tick() {
        if (mc.player != null && mc.world != null && !closing) {
            long window = mc.getWindow().getHandle();
            mc.options.forwardKey.setPressed(GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS);
            mc.options.backKey.setPressed(GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS);
            mc.options.leftKey.setPressed(GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS);
            mc.options.rightKey.setPressed(GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS);
            mc.options.jumpKey.setPressed(GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS);
            mc.options.sneakKey.setPressed(GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS);
            mc.options.sprintKey.setPressed(GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS);
        }
        super.tick();
    }

    @Override
    public void close() {
        if (!closing) {
            closing = true;
            ClickGui.playNewGuiCloseOverlay(gx, gy, gw, gh);
            tech.onetap.util.sound.GuiSoundUtil.playMenuClose();
            super.close();
        }
    }

    public boolean isClosing() {
        return closing;
    }

    boolean searchCheck(String name) {
        return hasSearch() && !matchesSearch(name);
    }

    private boolean hasSearch() {
        return !searchText.trim().isEmpty();
    }

    private boolean matchesSearch(String text) {
        return normalizeSearch(text).contains(normalizeSearch(searchText));
    }

    private String normalizeSearch(String text) {
        return text.replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private void layout() {
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        gw = Math.min(sw - 20, 460);
        gh = Math.min(sh - 20, 260);
        gx = (sw - gw) / 2f;
        gy = (sh - gh) / 2f;
        contentX = gx + SIDEBAR_W + SECTION_GAP;
        contentY = gy + TOP_H + SECTION_GAP;
        contentW = gx + gw - PAD - contentX;
        contentH = gy + gh - PAD - contentY;
    }

    private void layoutThemeEditor() {
        float preferredX = gx + gw + THEME_EDITOR_GAP;
        float maxX = mc.getWindow().getScaledWidth() - THEME_EDITOR_W - PAD;
        themeManager.setX(Math.max(PAD, Math.min(preferredX, maxX)));
        themeManager.setY(gy);
        themeManager.setWidth(THEME_EDITOR_W);
        themeManager.setHeight(gh);
    }

    private boolean isThemeEditorArea(double mouseX, double mouseY) {
        if (!themeManager.isOpen()) return false;
        float ex = themeManager.getX() - THEME_EDITOR_HIT_PAD;
        return HoverUtil.isHovered(mouseX, mouseY, ex, gy, THEME_EDITOR_W + THEME_EDITOR_HIT_PAD, gh);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        metanoiseAnimation.run(1f);
        float smokeProgress = metanoiseAnimation.getValue();

        CursorManager.reset();
        CursorManager.resetIBeam();
        CursorManager.resetClick();
        contentScroll += (targetScroll - contentScroll) * 0.25f;
        layout();
        int a = 255;

        context.getMatrices().push();
        StencilUtil.push();
        GuiMetanoiseRenderer.draw(context.getMatrices(), gx, gy, gw, gh, smokeProgress, RADIUS,
                ColorProvider.setAlpha(METANOISE_PANEL_COLOR, 211),
                ColorProvider.setAlpha(ColorProvider.getColorClient(), 255));
        StencilUtil.read(1);

        DrawUtil.drawRoundBlur(gx, gy, gw, gh, RADIUS, ColorProvider.rgba(70, 70, 70, 255), 22f);
        GuiMetanoiseRenderer.draw(context.getMatrices(), gx, gy, gw, gh, smokeProgress, RADIUS,
                ColorProvider.setAlpha(METANOISE_PANEL_COLOR, 211),
                ColorProvider.setAlpha(ColorProvider.getColorClient(), 100));

        renderSidebar(context, mouseX, mouseY, a);
        renderTopBar(context, mouseX, mouseY, a);

        Scissor.push();
        Scissor.setFromComponentCoordinates((int) contentX, (int) contentY, (int) contentW, (int) contentH);
        if (hasSearch()) {
            renderGlobalSearch(context, mouseX, mouseY, a);
        } else {
            switch (activeTab) {
                case FRIENDS -> renderFriends(context, mouseX, mouseY, a);
                case CONFIGS -> renderConfigs(context, mouseX, mouseY, a);
                case INTERFACE -> renderInterface(context, mouseX, mouseY, a);
                case EVENTS -> renderEvents(context, mouseX, mouseY, a);
                default -> renderModules(context, mouseX, mouseY, a);
            }
        }
        Scissor.unset();
        Scissor.pop();

        StencilUtil.pop();
        // Рендерим ThemeManagerWindow поверх всего (вне stencil)
        layoutThemeEditor();
        themeManager.render(context.getMatrices(), mouseX, mouseY, delta);
        context.getMatrices().pop();

        long window = mc.getWindow().getHandle();
        if (CursorManager.shouldBeHand()) GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR));
        else GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR));
    }

    private void renderSidebar(DrawContext ctx, int mouseX, int mouseY, int alpha) {
        float a = alpha / 255f;
        float x = gx + PAD;
        float y = gy + PAD;
        float w = SIDEBAR_W - PAD;

        // Лого
        String logo = "BurmaldaClient";
        float logoTextW = Fonts.SFMEDIUM.get().getWidth(logo, 7f);
        float logoStartX = x + (w - PAD) / 2f - logoTextW / 2f;
        DrawUtil.drawRound(x, y, w - PAD, TOP_H - 2f, INNER_RADIUS, ColorProvider.rgba(255, 255, 255, (int) (8 * a)));
        float logoCenterY = y + (TOP_H - 2f) / 2f;
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), logo, logoStartX, logoCenterY - 5.5f,
                ColorProvider.setAlpha(ColorProvider.getColorText(), alpha), 7f);

        // Вкладки
        float cy = y + TOP_H + 4f;
        String section = null;
        for (Tab tab : Tab.values()) {
            if (!tab.section.equals(section)) {
                section = tab.section;
                DrawUtil.drawText(Fonts.SFREGULAR.get(), section, x + 8f, cy,
                        ColorProvider.rgba(120, 120, 135, alpha), 5.5f);
                cy += 12f;
            }
            boolean hovered = HoverUtil.isHovered(mouseX, mouseY, x, cy - 2.5f, w - PAD, 16f);
            boolean active = activeTab == tab;
            if (hovered) CursorManager.requestHand();
            if (active) {
                DrawUtil.drawRound(x, cy - 2.5f, w - PAD, 16f, 4f,
                        ColorProvider.setAlpha(ColorProvider.getColorVisualModules(), (int) (45 * a)));
                DrawUtil.drawRound(x, cy + 1.5f, 2f, 8f, 1f,
                        ColorProvider.setAlpha(ColorProvider.getColorClient(), alpha));
            } else if (hovered) {
                DrawUtil.drawRound(x, cy - 2.5f, w - PAD, 16f, 4f,
                        ColorProvider.rgba(255, 255, 255, (int) (10 * a)));
            }
            int txtColor = active
                    ? ColorProvider.setAlpha(ColorProvider.getColorText(), alpha)
                    : ColorProvider.rgba(165, 165, 178, alpha);
            int icoColor = active
                    ? ColorProvider.setAlpha(ColorProvider.getColorIcons(), alpha)
                    : ColorProvider.rgba(150, 150, 165, alpha);
            float tIconW = Fonts.ICONS_MINCED.get().getWidth(tab.icon, 6f);
            DrawUtil.drawText(Fonts.ICONS_MINCED.get(), tab.icon, x + 8f, cy + 3f, icoColor, 6f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), tab.label, x + 8f + tIconW + 5f, cy + 3.5f, txtColor, 6.5f);
            cy += 17f;
        }

        // Разделитель
        DrawUtil.drawRound(gx + SIDEBAR_W + 1f, gy + PAD, 0.5f, gh - PAD * 2f, 0f,
                ColorProvider.rgba(255, 255, 255, (int) (12 * a)));

        // Карточка пользователя снизу
        float ucH = 28f;
        float ucY = gy + gh - PAD - ucH;
        DrawUtil.drawRound(x, ucY, w - PAD, ucH, INNER_RADIUS, ColorProvider.rgba(255, 255, 255, (int)(8 * a)));
        String username = mc.getSession().getUsername();
        // Скин
        Identifier skin = DefaultSkinHelper.getSkinTextures(mc.getSession().getUuidOrNull() != null
                ? mc.getSession().getUuidOrNull()
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes())).texture();
        drawSkinHead(ctx, skin, x + 5f, ucY + ucH / 2f - 9f, 18f, a);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), username, x + 27f, ucY + 8f,
                ColorProvider.setAlpha(ColorProvider.getColorText(), alpha), 6.5f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), "BurmaldaClient", x + 27f, ucY + 17.5f,
                ColorProvider.rgba(120, 120, 135, alpha), 5.5f);
    }

    private void renderTopBar(DrawContext ctx, int mouseX, int mouseY, int alpha) {
        float a = alpha / 255f;
        float topX = contentX;
        float topY = gy + PAD;
        float fullW = gx + gw - PAD - topX;
        float searchW = 116f;
        float titleW = fullW - searchW - 4f;

        // Заголовок раздела
        DrawUtil.drawRound(topX, topY, titleW, TOP_H - 2f, INNER_RADIUS,
                ColorProvider.rgba(255, 255, 255, (int) (8 * a)));
        float catIconW = Fonts.ICONS_MINCED.get().getWidth(activeTab.icon, 7f);
        DrawUtil.drawText(Fonts.ICONS_MINCED.get(), activeTab.icon, topX + 9f, topY + 7.5f,
                ColorProvider.setAlpha(ColorProvider.getColorIcons(), alpha), 7f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), activeTab.label, topX + 9f + catIconW + 5f, topY + 8f,
                ColorProvider.setAlpha(ColorProvider.getColorText(), alpha), 7.5f);

        // Поиск
        float searchX = topX + titleW + 4f;
        DrawUtil.drawRound(searchX, topY, searchW, TOP_H - 2f, INNER_RADIUS,
                ColorProvider.rgba(255, 255, 255, (int) (8 * a)));
        if (searchFocused)
            DrawUtil.drawRound(searchX, topY, searchW, TOP_H - 2f, INNER_RADIUS,
                    ColorProvider.setAlpha(ColorProvider.getColorClient(), (int) (25 * a)));
        String searchDisplay = searchText.isEmpty() && !searchFocused
                ? "Search for elements..."
                : searchText + (searchFocused && (System.currentTimeMillis() / 500 % 2 == 0) ? "|" : "");
        int searchTextColor = searchText.isEmpty() && !searchFocused
                ? ColorProvider.rgba(135, 135, 148, alpha)
                : ColorProvider.rgba(230, 230, 235, alpha);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), searchDisplay, searchX + 7f, topY + 8f,
                searchTextColor, 6f);
        DrawUtil.drawText(Fonts.LUPA.get(), "a", searchX + searchW - 12f, topY + 7.5f,
                ColorProvider.setAlpha(ColorProvider.getColorIcons(), alpha), 6.5f);

        // Горизонтальный разделитель
        DrawUtil.drawRound(contentX, gy + TOP_H + 1f, fullW, 0.5f, 0f,
                ColorProvider.rgba(255, 255, 255, (int) (12 * a)));
    }

    private void renderModules(DrawContext ctx, int mouseX, int mouseY, int alpha) {
        NewModulePanel panel = getPanel(activeTab.category);
        if (panel == null) return;
        // panel.render уже возвращает maxScroll (max(0, used - h))
        contentMaxScroll = panel.render(ctx, contentX, contentY, contentW, contentH, mouseX, mouseY, alpha / 255f, contentScroll);
        clampScroll();
    }

    private NewModulePanel getPanel(ModuleCategory cat) {
        for (NewModulePanel p : categoryPanels) {
            if (p.getCategory() == cat) return p;
        }
        return null;
    }

    private void renderFriends(DrawContext ctx, int mouseX, int mouseY, int alpha) {
        float a = alpha / 255f;
        List<Friend> friends = FriendRepository.getFriends();
        float fy = contentY + contentScroll;
        float rowH = 30f;
        for (Friend f : friends) {
            if (hasSearch() && !matchesSearch(f.name())) continue;
            if (fy + rowH >= contentY && fy <= contentY + contentH) {
                DrawUtil.drawRound(contentX, fy, contentW, rowH, INNER_RADIUS,
                        ColorProvider.rgba(255, 255, 255, (int) (8 * a)));
                UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + f.name()).getBytes());
                Identifier skin = DefaultSkinHelper.getSkinTextures(uuid).texture();
                drawSkinHead(ctx, skin, contentX + 7f, fy + rowH / 2f - 9f, 18f, a);
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), f.name(), contentX + 32f, fy + 8f,
                        ColorProvider.setAlpha(ColorProvider.getColorText(), alpha), 7f);
                DrawUtil.drawText(Fonts.SFREGULAR.get(), "Local", contentX + 32f, fy + 18f,
                        ColorProvider.rgba(120, 120, 135, alpha), 5.5f);
            }
            fy += rowH + 4f;
        }
        if (friends.isEmpty()) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Нет друзей. Добавьте через .friend add <ник>",
                    contentX + 2f, contentY + 4f, ColorProvider.rgba(140, 140, 150, alpha), 6.5f);
        }
        contentMaxScroll = Math.max(0, (fy - contentY - contentScroll) - contentH);
        clampScroll();
    }

    private void drawSkinHead(DrawContext ctx, Identifier skin, float x, float y, float sz, float a) {
        AbstractTexture tex = mc.getTextureManager().getTexture(skin);
        if (tex == null) {
            DrawUtil.drawRound(x, y, sz, sz, 4f, ColorProvider.rgba(80, 80, 120, (int)(200 * a)));
            return;
        }
        Builder.texture()
                .size(new SizeState(sz, sz))
                .radius(new QuadRadiusState(4f))
                .color(new QuadColorState(ColorProvider.rgba(255, 255, 255, (int)(255 * a))))
                .smoothness(1f)
                .texture(8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, tex)
                .build()
                .render(ctx.getMatrices().peek().getPositionMatrix(), x, y, 0);
    }

    private void renderConfigs(DrawContext ctx, int mouseX, int mouseY, int alpha) {
        float a = alpha / 255f;
        List<String> configs = ConfigManager.getConfigs();
        float cy = contentY + contentScroll;
        float rowH = 30f;
        for (String cfg : configs) {
            if (hasSearch() && !matchesSearch(cfg)) continue;
            if (cy + rowH >= contentY && cy <= contentY + contentH) {
                DrawUtil.drawRound(contentX, cy, contentW, rowH, INNER_RADIUS,
                        ColorProvider.rgba(255, 255, 255, (int) (8 * a)));
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), cfg, contentX + 10f, cy + 7f,
                        ColorProvider.setAlpha(ColorProvider.getColorText(), alpha), 7f);
                DrawUtil.drawText(Fonts.SFREGULAR.get(), "Локально", contentX + 10f, cy + 17f,
                        ColorProvider.rgba(120, 120, 135, alpha), 5.5f);

                float loadW = 54f;
                float loadX = contentX + contentW - loadW - 8f;
                boolean loadHov = HoverUtil.isHovered(mouseX, mouseY, loadX, cy + rowH / 2f - 7f, loadW, 14f);
                if (loadHov) CursorManager.requestHand();
                DrawUtil.drawRound(loadX, cy + rowH / 2f - 7f, loadW, 14f, 4f,
                        ColorProvider.setAlpha(ColorProvider.getColorVisualModules(), (int)((loadHov ? 75 : 45) * a)));
                float lblW = Fonts.SFREGULAR.get().getWidth("Загрузить", 6f);
                DrawUtil.drawText(Fonts.SFREGULAR.get(), "Загрузить",
                        loadX + loadW / 2f - lblW / 2f, cy + rowH / 2f - 3f,
                        ColorProvider.setAlpha(ColorProvider.getColorText(), alpha), 6f);
            }
            cy += rowH + 4f;
        }
        if (configs.isEmpty()) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Нет конфигов", contentX + 2f, contentY + 4f,
                    ColorProvider.rgba(140, 140, 150, alpha), 6.5f);
        }
        contentMaxScroll = Math.max(0, (cy - contentY - contentScroll) - contentH);
        clampScroll();
    }

    // ── Theme section constants (1в1 с HW) ──────────────────────────────────
    private static final int THEME_GRID_COLUMNS = 3;
    private static final float THEME_CARD_GAP = 5f;
    private static final float THEME_CARD_H = 38f;
    private static final float THEME_CREATE_H = 28f;
    private static final float THEME_CREATE_BUTTON_W = 58f;
    private static final float THEME_DELETE_W = 32f;
    private static final float THEME_DELETE_H = 10f;
    private static final float THEME_EDITOR_W = 110f;
    private static final float THEME_EDITOR_GAP = 6f;
    private static final float THEME_EDITOR_HIT_PAD = 18f;

    private record ThemeCard(String name, int c1, int c2, boolean custom) {}

    private static final record ThemePreset(String name, int c1, int c2) {}
    private static final ThemePreset[] THEMES = {
        new ThemePreset("SpaceVisuals",    0xFFAA44FF, 0xFF0D0D10),
        new ThemePreset("Recode",          0xFFFF4444, 0xFF1A0808),
        new ThemePreset("Nebula",          0xFF44FFAA, 0xFF0A1810),
        new ThemePreset("Mist",            0xFFB0C4DE, 0xFF12161C),
        new ThemePreset("Obsidian Night",  0xFF8844FF, 0xFF0A0A12),
        new ThemePreset("Arctic Frost",    0xFF44FFEE, 0xFF081A18),
        new ThemePreset("Ultraviolet",     0xFFFFDD44, 0xFF1A1808),
        new ThemePreset("Neon Blood Moon", 0xFFFF44AA, 0xFF1A0810),
        new ThemePreset("Toxic Anarchy",   0xFF44FF44, 0xFF081A08),
        new ThemePreset("Deep Ocean",      0xFF4488FF, 0xFF080A1A),
        new ThemePreset("Sunset Glow",     0xFFFF9944, 0xFF1A1008),
        new ThemePreset("Cyberpunk 2077",  0xFFFF44FF, 0xFF1A0818),
        new ThemePreset("Чёрная",          0xFFCCCCCC, 0xFF050505),
        new ThemePreset("Чёрная (тёмная)", 0xFF888888, 0xFF000000),
    };

    private final tech.onetap.ui.ThemeManagerWindow themeManager = new tech.onetap.ui.ThemeManagerWindow();
    private String newThemeName = "";
    private boolean themeNameFocused = false;

    private void renderInterface(DrawContext ctx, int mouseX, int mouseY, int alpha) {
        float a = alpha / 255f;
        float createY = contentY + contentScroll;
        renderThemeCreator(mouseX, mouseY, a, createY);

        List<ThemeCard> cards = getVisibleThemeCards();
        float gridTop = createY + THEME_CREATE_H + THEME_CARD_GAP;
        float cardW = (contentW - THEME_CARD_GAP * (THEME_GRID_COLUMNS - 1)) / THEME_GRID_COLUMNS;
        int curThemeColor = ColorProvider.getColorClient();

        for (int i = 0; i < cards.size(); i++) {
            ThemeCard theme = cards.get(i);
            int col = i % THEME_GRID_COLUMNS;
            int row = i / THEME_GRID_COLUMNS;
            float cx = contentX + col * (cardW + THEME_CARD_GAP);
            float cyy = gridTop + row * (THEME_CARD_H + THEME_CARD_GAP);
            if (cyy + THEME_CARD_H < contentY || cyy > contentY + contentH) continue;
            boolean hovered = HoverUtil.isHovered(mouseX, mouseY, cx, cyy, cardW, THEME_CARD_H);
            boolean active = isThemeActive(curThemeColor, theme);
            if (hovered || (theme.custom() && isThemeDeleteHovered(mouseX, mouseY, cx, cyy, cardW))) CursorManager.requestHand();
            renderThemeCard(ctx, theme, cx, cyy, cardW, hovered, active, a, alpha, mouseX, mouseY);
        }

        float rows = (float) Math.ceil(cards.size() / (double) THEME_GRID_COLUMNS);
        float gridH = rows <= 0 ? 0 : rows * (THEME_CARD_H + THEME_CARD_GAP) - THEME_CARD_GAP;
        contentMaxScroll = Math.max(0, THEME_CREATE_H + THEME_CARD_GAP + gridH - contentH);
        clampScroll();
    }

    private void renderThemeCreator(int mouseX, int mouseY, float a, float y) {
        int alpha = (int)(255 * a);
        float fieldX = contentX + 6f;
        float fieldY = y + 7f;
        float buttonW = THEME_CREATE_BUTTON_W;
        float fieldW = contentW - 12f - buttonW - 5f;
        float buttonX = fieldX + fieldW + 5f;
        boolean canCreate = !newThemeName.trim().isEmpty();
        boolean fieldHov = HoverUtil.isHovered(mouseX, mouseY, fieldX, fieldY, fieldW, 16f);
        boolean btnHov = HoverUtil.isHovered(mouseX, mouseY, buttonX, fieldY, buttonW, 16f) && canCreate;
        if (fieldHov || btnHov) CursorManager.requestHand();

        DrawUtil.drawRound(contentX, y, contentW, THEME_CREATE_H, INNER_RADIUS,
                ColorProvider.rgba(255, 255, 255, (int)(9 * a)));
        DrawUtil.drawText(Fonts.SFREGULAR.get(), "Save current colors", fieldX, y + 2.5f,
                ColorProvider.rgba(135, 135, 150, alpha), 5.2f);

        int fieldColor = themeNameFocused
                ? ColorProvider.setAlpha(ColorProvider.getColorClient(), (int)(38 * a))
                : ColorProvider.rgba(0, 0, 0, (int)(24 * a));
        DrawUtil.drawRound(fieldX, fieldY, fieldW, 16f, 4f, fieldColor);
        String fieldText = newThemeName.isEmpty() ? "Theme name..." : newThemeName;
        if (themeNameFocused && System.currentTimeMillis() / 500 % 2 == 0) fieldText += "|";
        int fieldTextColor = newThemeName.isEmpty()
                ? ColorProvider.rgba(145, 145, 160, alpha)
                : ColorProvider.setAlpha(ColorProvider.getColorText(), alpha);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), fieldText, fieldX + 5f, fieldY + 5f, fieldTextColor, 6f);

        int btnColor = canCreate
                ? ColorProvider.setAlpha(ColorProvider.getColorClient(), (int)((btnHov ? 120 : 85) * a))
                : ColorProvider.rgba(130, 130, 145, (int)(35 * a));
        DrawUtil.drawRound(buttonX, fieldY, buttonW, 16f, 4f, btnColor);
        float btnTW = Fonts.SFREGULAR.get().getWidth("Create", 6f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), "Create",
                buttonX + buttonW / 2f - btnTW / 2f, fieldY + 5f,
                ColorProvider.setAlpha(ColorProvider.getColorText(), canCreate ? alpha : (int)(120 * a)), 6f);
    }

    private void renderThemeCard(DrawContext ctx, ThemeCard theme, float x, float y, float width,
                                 boolean hovered, boolean active, float a, int alpha, int mouseX, int mouseY) {
        DrawUtil.drawRound(x, y, width, THEME_CARD_H, INNER_RADIUS,
                ColorProvider.rgba(255, 255, 255, (int)((hovered ? 14 : 8) * a)));
        if (active) {
            DrawUtil.drawRound(x, y, 2f, THEME_CARD_H, 1f, ColorProvider.setAlpha(theme.c1(), alpha));
        }
        float dotY = y + 8f;
        DrawUtil.drawRound(x + 7f, dotY, 7f, 7f, 3.5f, ColorProvider.setAlpha(theme.c1(), alpha));
        int c2bright = brightenForDot(theme.c2());
        DrawUtil.drawRound(x + 17f, dotY, 7f, 7f, 3.5f, ColorProvider.setAlpha(c2bright, alpha));
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), trimToWidth(theme.name(), width - 62f, 6.2f),
                x + 29f, y + 7.5f, ColorProvider.setAlpha(ColorProvider.getColorText(), alpha), 6.2f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), theme.custom() ? "Custom" : "Preset",
                x + 29f, y + 20f, ColorProvider.rgba(130, 130, 145, alpha), 5.2f);

        if (active) {
            String badge = "Active";
            float badgeW = Fonts.SFREGULAR.get().getWidth(badge, 5f) + 10f;
            float badgeX = x + width - badgeW - 6f;
            float badgeY = y + 6f;
            DrawUtil.drawRound(badgeX, badgeY, badgeW, THEME_DELETE_H, 3f,
                    ColorProvider.setAlpha(theme.c1(), (int)(120 * a)));
            float tw = Fonts.SFREGULAR.get().getWidth(badge, 5f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), badge, badgeX + badgeW / 2f - tw / 2f,
                    badgeY + 1.5f, ColorProvider.setAlpha(ColorProvider.getColorText(), alpha), 5f);
        }
        if (theme.custom()) {
            boolean delHov = isThemeDeleteHovered(mouseX, mouseY, x, y, width);
            float delX = x + width - THEME_DELETE_W - 6f;
            float delY = y + THEME_CARD_H - THEME_DELETE_H - 5f;
            DrawUtil.drawRound(delX, delY, THEME_DELETE_W, THEME_DELETE_H, 3f,
                    delHov ? ColorProvider.rgba(255, 82, 94, (int)(95 * a))
                           : ColorProvider.rgba(255, 82, 94, (int)(55 * a)));
            float dw = Fonts.SFREGULAR.get().getWidth("Delete", 5f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Delete", delX + THEME_DELETE_W / 2f - dw / 2f,
                    delY + 1.5f, ColorProvider.rgba(255, 235, 238, alpha), 5f);
        }
    }

    private boolean isThemeActive(int currentColor, ThemeCard theme) {
        return ColorProvider.red(currentColor) == ColorProvider.red(theme.c1())
                && ColorProvider.green(currentColor) == ColorProvider.green(theme.c1())
                && ColorProvider.blue(currentColor) == ColorProvider.blue(theme.c1());
    }

    private boolean isThemeDeleteHovered(int mouseX, int mouseY, float x, float y, float width) {
        float delX = x + width - THEME_DELETE_W - 6f;
        float delY = y + THEME_CARD_H - THEME_DELETE_H - 5f;
        return HoverUtil.isHovered(mouseX, mouseY, delX, delY, THEME_DELETE_W, THEME_DELETE_H);
    }

    private List<ThemeCard> getVisibleThemeCards() {
        List<ThemeCard> cards = new ArrayList<>();
        for (ThemePreset t : THEMES) {
            if (!hasSearch() || matchesSearch(t.name())) cards.add(new ThemeCard(t.name(), t.c1(), t.c2(), false));
        }
        for (tech.onetap.ui.ThemeManagerWindow.SavedThemeView t : themeManager.getSavedThemeViews()) {
            if (!hasSearch() || matchesSearch(t.name())) cards.add(new ThemeCard(t.name(), t.c1(), t.c2(), true));
        }
        return cards;
    }

    private int brightenForDot(int c2) {
        int r = Math.min(255, ColorProvider.red(c2) + 60);
        int g = Math.min(255, ColorProvider.green(c2) + 60);
        int b = Math.min(255, ColorProvider.blue(c2) + 80);
        return ColorProvider.rgba(r, g, b, 255);
    }

    private String trimToWidth(String text, float maxWidth, float size) {
        if (Fonts.SFREGULAR.get().getWidth(text, size) <= maxWidth) return text;
        String result = text;
        while (!result.isEmpty() && Fonts.SFREGULAR.get().getWidth(result + "...", size) > maxWidth)
            result = result.substring(0, result.length() - 1);
        return result + "...";
    }

    private void handleInterfaceClick(double mouseX, double mouseY) {
        float createY = contentY + contentScroll;
        float fieldX = contentX + 6f;
        float fieldY = createY + 7f;
        float buttonW = THEME_CREATE_BUTTON_W;
        float fieldW = contentW - 12f - buttonW - 5f;
        float buttonX = fieldX + fieldW + 5f;

        // Клик на поле ввода
        if (HoverUtil.isHovered(mouseX, mouseY, fieldX, fieldY, fieldW, 16f)) {
            themeNameFocused = true;
            searchFocused = false;
            return;
        }
        // Клик на кнопку Create
        if (HoverUtil.isHovered(mouseX, mouseY, buttonX, fieldY, buttonW, 16f)) {
            if (!newThemeName.trim().isEmpty()) {
                themeManager.saveCurrentAsNamedTheme(newThemeName.trim());
                newThemeName = "";
                themeNameFocused = false;
            } else {
                themeNameFocused = true;
            }
            searchFocused = false;
            return;
        }
        themeNameFocused = false;

        // Клик на карточку темы
        float gridTop = createY + THEME_CREATE_H + THEME_CARD_GAP;
        float cardW = (contentW - THEME_CARD_GAP * (THEME_GRID_COLUMNS - 1)) / THEME_GRID_COLUMNS;
        List<ThemeCard> cards = getVisibleThemeCards();
        for (int i = 0; i < cards.size(); i++) {
            ThemeCard theme = cards.get(i);
            int col = i % THEME_GRID_COLUMNS;
            int row = i / THEME_GRID_COLUMNS;
            float cx = contentX + col * (cardW + THEME_CARD_GAP);
            float cyy = gridTop + row * (THEME_CARD_H + THEME_CARD_GAP);

            // Кнопка Delete
            if (theme.custom() && isThemeDeleteHovered((int)mouseX, (int)mouseY, cx, cyy, cardW)) {
                themeManager.deleteSavedTheme(theme.name());
                return;
            }
            // Клик на карточку
            if (HoverUtil.isHovered(mouseX, mouseY, cx, cyy, cardW, THEME_CARD_H)) {
                themeManager.applyPresetColors(theme.c1(), theme.c2());
                return;
            }
        }
    }

    private void renderEvents(DrawContext ctx, int mouseX, int mouseY, int alpha) {
        float totalH = eventsTab.render(ctx, contentX, contentY, contentW, contentH, mouseX, mouseY, alpha / 255f, contentScroll);
        contentMaxScroll = Math.max(0, totalH - contentH);
        clampScroll();
    }

    private void renderGlobalSearch(DrawContext ctx, int mouseX, int mouseY, int alpha) {
        float a = alpha / 255f;
        float startY = contentY + contentScroll;
        float curY = startY;

        // ── Модули — два столбца через categoryPanels (как в HW) ──
        float[] colY = {curY, curY};
        boolean hasModules = false;
        for (NewModulePanel panel : categoryPanels) {
            boolean panelHasMatches = false;
            for (NewModulePanel.Entry entry : panel.getEntries()) {
                if (matchesSearch(entry.module.getName())) { panelHasMatches = true; break; }
            }
            if (!panelHasMatches) continue;
            if (!hasModules) {
                float titleY = Math.min(colY[0], colY[1]);
                drawSearchSectionTitle("Modules", titleY, alpha);
                colY[0] = colY[1] = titleY + 13f;
                hasModules = true;
            }
            panel.renderFlowColumns(ctx, contentX, contentY, contentW, contentH,
                    mouseX, mouseY, a, Math.min(colY[0], colY[1]), colY);
        }
        if (hasModules) curY = Math.max(colY[0], colY[1]) + 4f;

        // ── Конфиги ──
        List<String> configs = ConfigManager.getConfigs();
        boolean hasConfigs = false;
        for (String cfg : configs) {
            if (!matchesSearch(cfg)) continue;
            if (!hasConfigs) { curY = drawSearchSectionTitle("Configs", curY, alpha); hasConfigs = true; }
            if (curY + 30f >= contentY && curY <= contentY + contentH) {
                DrawUtil.drawRound(contentX, curY, contentW, 30f, INNER_RADIUS,
                        ColorProvider.rgba(255, 255, 255, (int)(8 * a)));
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), cfg, contentX + 10f, curY + 7f,
                        ColorProvider.setAlpha(ColorProvider.getColorText(), alpha), 7f);
                DrawUtil.drawText(Fonts.SFREGULAR.get(), "Локально", contentX + 10f, curY + 17f,
                        ColorProvider.rgba(120, 120, 135, alpha), 5.5f);
            }
            curY += 34f;
        }

        // ── Друзья ──
        List<Friend> friends = FriendRepository.getFriends();
        boolean hasFriends = false;
        for (Friend f : friends) {
            if (!matchesSearch(f.name())) continue;
            if (!hasFriends) { curY = drawSearchSectionTitle("Friends", curY, alpha); hasFriends = true; }
            if (curY + 30f >= contentY && curY <= contentY + contentH) {
                DrawUtil.drawRound(contentX, curY, contentW, 30f, INNER_RADIUS,
                        ColorProvider.rgba(255, 255, 255, (int)(8 * a)));
                UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + f.name()).getBytes());
                Identifier skin = DefaultSkinHelper.getSkinTextures(uuid).texture();
                drawSkinHead(ctx, skin, contentX + 7f, curY + 5f, 18f, a);
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), f.name(), contentX + 32f, curY + 7f,
                        ColorProvider.setAlpha(ColorProvider.getColorText(), alpha), 7f);
                DrawUtil.drawText(Fonts.SFREGULAR.get(), "Local", contentX + 32f, curY + 17f,
                        ColorProvider.rgba(120, 120, 135, alpha), 5.5f);
            }
            curY += 34f;
        }

        if (!hasModules && !hasConfigs && !hasFriends) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Ничего не найдено", contentX + 2f, contentY + 4f,
                    ColorProvider.rgba(140, 140, 150, alpha), 6.5f);
        }

        contentMaxScroll = Math.max(0, curY - startY - contentH);
        clampScroll();
    }

    private float drawSearchSectionTitle(String title, float y, int alpha) {
        if (y + 12f >= contentY && y <= contentY + contentH) {
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), title, contentX + 2f, y + 2f,
                    ColorProvider.rgba(150, 150, 168, alpha), 6f);
        }
        return y + 13f;
    }

    private void clampScroll() {
        if (targetScroll < -contentMaxScroll) targetScroll = -contentMaxScroll;
        if (targetScroll > 0) targetScroll = 0;
        if (contentScroll < -contentMaxScroll) contentScroll = -contentMaxScroll;
        if (contentScroll > 0) contentScroll = 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();
        layoutThemeEditor();

        // ПКМ на карточке темы — открывает редактор
        if (button == 1 && activeTab == Tab.INTERFACE) {
            themeManager.toggleOpen();
            searchFocused = false;
            themeNameFocused = false;
            return true;
        }

        // Обработка кликов ThemeManagerWindow
        themeManager.mouseClicked(mouseX, mouseY, button);
        if (isThemeEditorArea(mouseX, mouseY)) {
            searchFocused = false;
            themeNameFocused = false;
            return true;
        }

        float x = gx + PAD;
        float y = gy + PAD + TOP_H + 4f;
        String section = null;
        for (Tab tab : Tab.values()) {
            if (!tab.section.equals(section)) {
                section = tab.section;
                y += 12f;
            }
            if (HoverUtil.isHovered(mouseX, mouseY, x, y - 2.5f, SIDEBAR_W - PAD * 2f, 16f) && button == 0) {
                activeTab = tab;
                targetScroll = contentScroll = 0f;
                searchText = "";        // ← сброс поиска при смене вкладки
                searchFocused = false;
                themeNameFocused = false;
                return true;
            }
            y += 17f;
        }

        float topX = contentX;
        float topY = gy + PAD;
        float fullW = gx + gw - PAD - topX;
        float searchW = 116f;
        float titleW = fullW - searchW - 4f;
        float searchX = topX + titleW + 4f;
        if (HoverUtil.isHovered(mouseX, mouseY, searchX, topY, searchW, TOP_H - 2f) && button == 0) {
            searchFocused = true;
            themeNameFocused = false;
            return true;
        }
        if (!HoverUtil.isHovered(mouseX, mouseY, searchX, topY, searchW, TOP_H - 2f)) {
            if (button == 0) searchFocused = false;
        }

        if (HoverUtil.isHovered(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            if (hasSearch()) {
                // Клики по модулям в поиске — через те же categoryPanels
                for (NewModulePanel panel : categoryPanels) {
                    panel.mouseClicked(mouseX, mouseY, button);
                }
            } else {
                switch (activeTab) {
                    case EVENTS -> eventsTab.mouseClicked(mouseX, mouseY, button, contentX, contentY, contentW, contentH, contentScroll);
                    case FRIENDS -> {}
                    case CONFIGS -> {
                        float cy = contentY + contentScroll;
                        float rowH = 30f;
                        for (String cfg : ConfigManager.getConfigs()) {
                            float loadW = 54f;
                            float loadX = contentX + contentW - loadW - 8f;
                            if (HoverUtil.isHovered(mouseX, mouseY, loadX, cy + rowH / 2f - 7f, loadW, 14f) && button == 0) {
                                ConfigManager.load(cfg);
                                return true;
                            }
                            cy += rowH + 4f;
                        }
                    }
                    case INTERFACE -> {
                        if (button == 0) handleInterfaceClick(mouseX, mouseY);
                    }
                    default -> {
                        NewModulePanel panel = getPanel(activeTab.category);
                        if (panel != null) panel.mouseClicked(mouseX, mouseY, button);
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        themeManager.mouseReleased(mouseX, mouseY, button);
        for (NewModulePanel panel : categoryPanels) panel.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (closing) return true;
        layout();
        layoutThemeEditor();
        if (isThemeEditorArea(mouseX, mouseY)) {
            themeManager.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            return true;
        }
        targetScroll += (float) (verticalAmount * 22f);
        if (targetScroll < -contentMaxScroll) targetScroll = -contentMaxScroll;
        if (targetScroll > 0) targetScroll = 0;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (themeManager.isOpen()) {
                themeManager.setOpen(false);
                return true;
            }
            if (!closing) close();
            return true;
        }
        themeManager.keyPressed(keyCode);
        if (themeNameFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !newThemeName.isEmpty())
                newThemeName = newThemeName.substring(0, newThemeName.length() - 1);
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (!newThemeName.trim().isEmpty()) {
                    themeManager.saveCurrentAsNamedTheme(newThemeName.trim());
                    newThemeName = "";
                }
                themeNameFocused = false;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) themeNameFocused = false;
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty())
                searchText = searchText.substring(0, searchText.length() - 1);
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE)
                searchFocused = false;
            return true;
        }
        for (NewModulePanel panel : categoryPanels) {
            for (NewModulePanel.Entry entry : panel.getEntries()) {
                if (entry.binding) {
                    entry.module.setKey(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode);
                    entry.binding = false;
                    return true;
                }
            }
        }
        for (NewModulePanel panel : categoryPanels) panel.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (themeNameFocused && !Character.isISOControl(chr) && newThemeName.length() < 24) {
            newThemeName += chr;
            return true;
        }
        if (searchFocused && !Character.isISOControl(chr) && searchText.length() < 32) {
            searchText += chr;
            return true;
        }
        for (NewModulePanel panel : categoryPanels) panel.charTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
