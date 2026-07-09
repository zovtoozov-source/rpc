package tech.onetap.util.server;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import tech.onetap.event.EventGameUpdate;

public class ServerHandler {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private long pvpStartTime = -1;
    private long lastPacketTime = System.currentTimeMillis();

    private String serverType = "Unknown";
    private boolean inPvP;
    private int tps = 20;

    public void tick(EventGameUpdate eventUpdate) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();

        if (now - lastPacketTime > 5000) {
            tps = 20;
        }

        if (inPvP && pvpStartTime != -1 && now - pvpStartTime > 10000) {
            inPvP = false;
            pvpStartTime = -1;
        }
    }

    public void onPacketReceived() {
        lastPacketTime = System.currentTimeMillis();
    }

    public void onPlayerListPacket(PlayerListS2CPacket packet) {
        String ip = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "";
        detectServer(ip);
    }

    private void detectServer(String ip) {
        if (ip.contains("funtime") || ip.contains("fun-time")) {
            serverType = "FunTime";
        } else if (ip.contains("holyworld") || ip.contains("holy-world")) {
            serverType = "HolyWorld";
        } else if (ip.contains("reallyworld") || ip.contains("really-world")) {
            serverType = "ReallyWorld";
        } else if (ip.contains("lonygrief") || ip.contains("lony-grief")) {
            serverType = "LonyGrief";
        } else if (ip.contains("raidmine")) {
            serverType = "Raidmine";
        } else {
            serverType = "Unknown";
        }
    }

    public String getServerType() {
        return serverType;
    }

    public boolean isInPvP() {
        return inPvP;
    }

    public void setInPvP(boolean inPvP) {
        this.inPvP = inPvP;
        if (inPvP) pvpStartTime = System.currentTimeMillis();
    }

    public int getTps() {
        return tps;
    }
}
