package tech.onetap.util.render.icon;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class SvgIcon {

    private static int textureId = -1;
    private static boolean loaded = false;

    public static void load(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) { return; }

            BufferedImage img = ImageIO.read(file);
            if (img == null) { return; }

            int iw = img.getWidth(), ih = img.getHeight();
            int size = Math.max(iw, ih);

            NativeImage ni = new NativeImage(NativeImage.Format.RGBA, size, size, true);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int argb = img.getRGB(Math.min(x, iw - 1), Math.min(y, ih - 1));
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    ni.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }

            NativeImageBackedTexture tex = new NativeImageBackedTexture(ni);
            Identifier id = Identifier.of("mre", "watermark_icon");
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            textureId = tex.getGlId();
            tex.setFilter(true, false);
            loaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final Matrix4f IDENTITY = new Matrix4f();

    public static void draw(float x, float y, float size, int color) {
        if (!loaded || textureId == -1) return;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, textureId);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(IDENTITY, x, y, 0).texture(0, 0).color(r, g, b, a);
        builder.vertex(IDENTITY, x, y + size, 0).texture(0, 1).color(r, g, b, a);
        builder.vertex(IDENTITY, x + size, y + size, 0).texture(1, 1).color(r, g, b, a);
        builder.vertex(IDENTITY, x + size, y, 0).texture(1, 0).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
    }

    public static boolean isLoaded() { return loaded && textureId != -1; }
}
