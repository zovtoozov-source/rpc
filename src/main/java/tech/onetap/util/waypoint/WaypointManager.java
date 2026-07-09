package tech.onetap.util.waypoint;

import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.EventGameUpdate;
import tech.onetap.event.list.EventHUD;
import tech.onetap.util.render.math.ProjectionUtil;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WaypointManager {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final File WAYPOINTS_FILE = new File(MinecraftClient.getInstance().runDirectory, "onetap/waypoints.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Waypoint> waypoints = new ArrayList<>();

    public WaypointManager() {
        load();
    }

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public void add(Waypoint waypoint) {
        waypoints.add(waypoint);
    }

    public boolean remove(String name) {
        return waypoints.removeIf(w -> w.getName().equalsIgnoreCase(name));
    }

    public void clear() {
        waypoints.clear();
    }

    public void load() {
        if (!WAYPOINTS_FILE.exists()) return;
        try (FileReader reader = new FileReader(WAYPOINTS_FILE)) {
            List<Waypoint> loaded = GSON.fromJson(reader, new TypeToken<List<Waypoint>>(){}.getType());
            if (loaded != null) {
                waypoints.clear();
                waypoints.addAll(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        if (!WAYPOINTS_FILE.getParentFile().exists()) {
            WAYPOINTS_FILE.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(WAYPOINTS_FILE)) {
            GSON.toJson(waypoints, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    private void onWorldRender(EventHUD event) {
        if (mc.player == null || mc.world == null) return;

        for (Waypoint w : waypoints) {
            double dx = w.getX() - mc.player.getX();
            double dy = w.getY() - mc.player.getY();
            double dz = w.getZ() - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist > 0.5) {
                Vec3d pos = new Vec3d(w.getX(), w.getY() + 1.0, w.getZ());
                Vector2f screen = ProjectionUtil.project(pos);

                if (screen.getX() == Float.MAX_VALUE && screen.getY() == Float.MAX_VALUE) continue;

                float scale = (float) Math.max(0.5, Math.min(2.0, 30.0 / dist));
                float nameFontSize = 8.0f * scale;
                float distFontSize = 6.0f * scale;

                String distText = String.format("%.0f м", dist);
                float nameWidth = Fonts.SFMEDIUM.get().getWidth(w.getName(), nameFontSize);
                float distWidth = Fonts.SFMEDIUM.get().getWidth(distText, distFontSize);
                float totalWidth = Math.max(nameWidth, distWidth) + 16.0f * scale;
                float totalHeight = nameFontSize + distFontSize + 12.0f * scale;

                float bgX = screen.getX() - totalWidth / 2.0f;
                float bgY = screen.getY() - totalHeight - 8.0f * scale;

                DrawUtil.drawRound(bgX, bgY, totalWidth, totalHeight, 4f * scale, ColorProvider.rgba(0, 0, 0, 180));

                float nameX = screen.getX() - nameWidth / 2.0f;
                float nameY = bgY + 4.0f * scale;
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), w.getName(), nameX, nameY, 0xFFFFFFFF, nameFontSize);

                float distX = screen.getX() - distWidth / 2.0f;
                float distY = nameY + nameFontSize + 2.0f * scale;
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), distText, distX, distY, 0xFF8C8CFF, distFontSize);
            }
        }
    }

    @Subscribe
    private void onTick(EventGameUpdate ignored) {
        if (mc.player == null) return;
        save();
    }
}
