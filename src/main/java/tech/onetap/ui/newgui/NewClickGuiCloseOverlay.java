package tech.onetap.ui.newgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import tech.onetap.event.list.EventHUD;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;
import tech.onetap.util.render.renderers.GuiMetanoiseRenderer;
import tech.onetap.util.render.stencil.StencilUtil;

public final class NewClickGuiCloseOverlay {
    private static final NewClickGuiCloseOverlay INSTANCE = new NewClickGuiCloseOverlay();

    private static final float CLOSE_THRESHOLD = 0.02f;
    private static final float RADIUS = 8.0f;
    private static final int METANOISE_PANEL_COLOR = 0xD30A0B16;
    private static final long METANOISE_CLOSE_DURATION_MS = 2750L;

    private final Animation animation = new Animation(Easing.CIRC_OUT, METANOISE_CLOSE_DURATION_MS);
    private boolean active;
    private float x;
    private float y;
    private float width;
    private float height;

    public static void startStatic(float x, float y, float width, float height) {
        INSTANCE.start(x, y, width, height);
    }

    public static void renderStatic(DrawContext context) {
        INSTANCE.render(context);
    }

    public void start(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.active = true;
        this.animation.setDuration(METANOISE_CLOSE_DURATION_MS);
        this.animation.reset(1.0f);
        this.animation.run(0.0f);
    }

    public void render(EventHUD event) {
        render(event.getDrawContext());
    }

    private void render(DrawContext context) {
        if (!active) return;

        float progress = animation.run(0.0f);
        if (progress < CLOSE_THRESHOLD) {
            active = false;
            return;
        }

        StencilUtil.push();
        GuiMetanoiseRenderer.draw(context.getMatrices(), x, y, width, height, progress, RADIUS,
                ColorProvider.setAlpha(METANOISE_PANEL_COLOR, 211),
                ColorProvider.setAlpha(ColorProvider.getColorClient(), 255));

        StencilUtil.read(1);
        DrawUtil.drawRoundBlur(x, y, width, height, RADIUS, ColorProvider.rgba(70, 70, 70, 255), 22f);
        GuiMetanoiseRenderer.draw(context.getMatrices(), x, y, width, height, progress, RADIUS,
                ColorProvider.setAlpha(METANOISE_PANEL_COLOR, 211),
                ColorProvider.setAlpha(ColorProvider.getColorClient(), 100));

        StencilUtil.pop();
    }
}

