package tech.onetap.util.render.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import tech.onetap.util.render.shader.GlProgram;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public final class GuiMetanoiseRenderer {
    private static final GlProgram PROGRAM = new GlProgram(Identifier.of("mre", "metanoise"), VertexFormats.POSITION_COLOR);
    private static final int SETUP_RETRY_MS = 1_000;

    private static long lastSetupAttempt;

    private GuiMetanoiseRenderer() {
    }

    public static void draw(MatrixStack matrices, float x, float y, float width, float height,
                            float progress, float radius, int backgroundColor, int smokeColor) {
        ShaderProgram shader = getShader();
        if (shader == null) {
            DrawUtil.drawRound(x, y, width, height, radius, backgroundColor);
            return;
        }

        float clampedProgress = Math.max(0.0f, Math.min(1.0f, progress));
        setUniform(shader, "Size", width, height);
        setUniform(shader, "Time", clampedProgress);
        setUniform(shader, "BgColor", backgroundColor);
        setUniform(shader, "OutlineColor", smokeColor);
        setUniform(shader, "Radius", radius, radius, radius, radius);
        setUniform(shader, "Smoothness", 0.8f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).color(255, 255, 255, 255);
        buffer.vertex(matrix, x, y + height, 0.0f).color(255, 255, 255, 255);
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(255, 255, 255, 255);
        buffer.vertex(matrix, x + width, y, 0.0f).color(255, 255, 255, 255);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static ShaderProgram getShader() {
        if (!PROGRAM.isReady()) {
            long now = System.currentTimeMillis();
            if (now - lastSetupAttempt >= SETUP_RETRY_MS) {
                lastSetupAttempt = now;
                GlProgram.loadAndSetupPrograms();
            }
        }
        if (!PROGRAM.isReady()) return null;
        return PROGRAM.use();
    }

    private static void setUniform(ShaderProgram shader, String name, float value) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(value);
    }

    private static void setUniform(ShaderProgram shader, String name, float first, float second) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(first, second);
    }

    private static void setUniform(ShaderProgram shader, String name, float first, float second, float third, float fourth) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(first, second, third, fourth);
    }

    private static void setUniform(ShaderProgram shader, String name, int rgba) {
        int alpha = (rgba >>> 24) & 0xFF;
        int red = (rgba >>> 16) & 0xFF;
        int green = (rgba >>> 8) & 0xFF;
        int blue = rgba & 0xFF;
        setUniform(shader, name, red / 255.0f, green / 255.0f, blue / 255.0f, alpha / 255.0f);
    }
}
