package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.ActivityType;
import com.jagrosh.discordipc.entities.RichPresence;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Discord RPC", moduleDesc = "Discord Rich Presence", moduleCategory = ModuleCategory.MISC)
public class DiscordRPC extends Module {

    private static final long APPLICATION_ID = 1524606397155770508L;
    private static final long UPDATE_INTERVAL_MS = 15_000L;

    private IPCClient client;
    private final long startTime = System.currentTimeMillis() / 1000L;
    private boolean buttonsAdded = false;
    private long lastUpdate = 0;

    @Override
    public void onEnable() {
        super.onEnable();
        buttonsAdded = false;
        connect();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (client != null) {
            try {
                client.sendRichPresence(null);
            } catch (Exception ignored) {}
            client.close();
            client = null;
        }
    }

    private void connect() {
        if (client != null) return;

        Thread t = new Thread(() -> {
            try {
                client = new IPCClient(APPLICATION_ID);
                client.setListener(new IPCListener() {
                    @Override public void onReady(IPCClient c) { sendPresence(); }
                    @Override public void onClose(IPCClient c, JsonObject json) { client = null; }
                    @Override public void onDisconnect(IPCClient c, Throwable t) { client = null; }
                    @Override public void onPacketSent(IPCClient c, com.jagrosh.discordipc.entities.Packet p) {}
                    @Override public void onPacketReceived(IPCClient c, com.jagrosh.discordipc.entities.Packet p) {}
                    @Override public void onActivityJoin(IPCClient c, String s) {}
                    @Override public void onActivitySpectate(IPCClient c, String s) {}
                    @Override public void onActivityJoinRequest(IPCClient c, String s, com.jagrosh.discordipc.entities.User u) {}
                });
                client.connect();
            } catch (Exception ignored) {}
        }, "Discord-IPC");
        t.setDaemon(true);
        t.start();
    }

    private void sendPresence() {
        if (client == null) return;

        String details;
        if (mc.player != null) {
            if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) {
                details = "На сервере: " + mc.getCurrentServerEntry().address;
            } else if (mc.isInSingleplayer()) {
                details = "В одиночной игре";
            } else {
                details = "В игре";
            }
        } else if (mc.currentScreen != null) {
            String screenName = mc.currentScreen.getClass().getSimpleName();
            if (mc.currentScreen instanceof TitleScreen) {
                details = "В главном меню";
            } else if (mc.currentScreen instanceof MultiplayerScreen) {
                details = "В списке серверов";
            } else if (mc.currentScreen instanceof OptionsScreen) {
                details = "В настройках";
            } else if (screenName.contains("Alt") || screenName.contains("Account")) {
                details = "Выбирает аккаунт";
            } else {
                details = "В главном меню";
            }
        } else {
            details = "В главном меню";
        }

        String username = "я хуесос";
        int uid = 1488;

        JsonArray buttons = new JsonArray();
        if (!buttonsAdded) {
            JsonObject btn = new JsonObject();
            btn.addProperty("label", "Дискорд чита");
            btn.addProperty("url", "https://discord.gg/VxArvpNs28");
            buttons.add(btn);
            buttonsAdded = true;
        }

        String largeImageUrl = "https://raw.githubusercontent.com/zovtoozov-source/rpc/master/src/main/resources/assets/mre/videos/IMG_8920.gif";

        RichPresence presence = new RichPresence.Builder()
                .setActivityType(ActivityType.Playing)
                .setDetails(details)
                .setState("User: " + username + " | UID: " + uid)
                .setLargeImage(largeImageUrl, "MoonWard 1.21.4")
                .setStartTimestamp(startTime)
                .setButtons(buttons)
                .build();

        try {
            client.sendRichPresence(presence);
        } catch (Exception ignored) {}
    }

    @Subscribe
    private void onTick(EventTick e) {
        long now = System.currentTimeMillis();
        if (now - lastUpdate >= UPDATE_INTERVAL_MS) {
            lastUpdate = now;
            sendPresence();
        }
    }
}
