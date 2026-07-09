package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ColorSetting;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.render.providers.ColorProvider;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "BoxESP", moduleCategory = ModuleCategory.RENDER)
public final class BoxESP extends Module {
    private final BooleanSetting boxes = new BooleanSetting("Боксы", true);
    private final BooleanSetting fill = new BooleanSetting("Заливка", true);
    private final ColorSetting color = new ColorSetting("Цвет", ColorProvider.rgba(160, 80, 255, 255));
    private final BooleanSetting friendColors = new BooleanSetting("Цвет друзей", true);
    private final ColorSetting friendColor = new ColorSetting("Цвет друга", ColorProvider.rgba(0, 255, 0, 255));
    private final List<PlayerEntity> players = new ArrayList<>();

    @Subscribe
    private void onTick(EventTick e) {
        players.clear();
        if (mc.world != null && mc.player != null) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
                if (p.isInvisible()) continue;
                if (p.distanceTo(mc.player) > 64.0F) continue;
                players.add(p);
            }
        }
    }

    @Subscribe
    private void onWorldRender(EventWorldRender e) {
        if (!boxes.getValue() || mc.player == null) return;
        float td = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();

        for (PlayerEntity player : players) {
            double px = MathHelper.lerp(td, player.lastRenderX, player.getX());
            double py = MathHelper.lerp(td, player.lastRenderY, player.getY());
            double pz = MathHelper.lerp(td, player.lastRenderZ, player.getZ());
            Box box = player.getDimensions(player.getPose()).getBoxAt(px, py, pz);

            boolean friend = friendColors.getValue() && FriendRepository.isFriend(player.getNameForScoreboard());
            int baseColor = friend ? friendColor.getValue() : color.getValue();

            double minX = box.minX - camPos.x;
            double minY = box.minY - camPos.y;
            double minZ = box.minZ - camPos.z;
            double maxX = box.maxX - camPos.x;
            double maxY = box.maxY - camPos.y;
            double maxZ = box.maxZ - camPos.z;

            drawBox(e.getMatrixStack(), minX, minY, minZ, maxX, maxY, maxZ, baseColor);
        }
    }

    private void drawBox(MatrixStack matrices, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        if (fill.getValue()) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            int fillColor = multAlpha(color, 0.1f);
            float fr = ((fillColor >> 16) & 0xFF) / 255f;
            float fg = ((fillColor >> 8) & 0xFF) / 255f;
            float fb = (fillColor & 0xFF) / 255f;
            float fa = ((fillColor >> 24) & 0xFF) / 255f;

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(fr, fg, fb, fa);

            buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(fr, fg, fb, fa);

            buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(fr, fg, fb, fa);

            buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(fr, fg, fb, fa);

            buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(fr, fg, fb, fa);

            buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(fr, fg, fb, fa);
            buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(fr, fg, fb, fa);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(1.5F);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a);

        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r, g, b, a);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static int multAlpha(int color, float factor) {
        int a = (int) ((color >> 24 & 0xFF) * factor);
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
