package tech.onetap.ui.newgui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.DrawContext;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Вкладка Events — парсер ивентов FunTime + MixerGrief.
 * 4 столбика: 2 FunTime | 2 MixerGrief
 */
public class EventsTab implements IMinecraft {

    private static final String API_URL = "https://api.funtime.su/method/events-info";
    private static final String TOKEN = "1fc4db5.a4d76db158899e4d410e5c81b1a48cf5";
    private static final int MAX_PER_REQ = 30;
    private static final long REFRESH_MS = 10_000;

    private final List<EventCard> ftCards = new CopyOnWriteArrayList<>();
    private final List<EventCard> mgCards = new CopyOnWriteArrayList<>();
    private final List<EventCard> mgMines = new CopyOnWriteArrayList<>();
    private long lastFetch = 0;
    private long fetchTimestamp = 0;
    private boolean fetching = false;
    private String ftStatus = "Загрузка...";
    private String mgStatus = "Загрузка...";

    { // Запускаем первый fetch сразу
        fetching = true;
        Thread t = new Thread(this::fetchAll, "EventsFetchInit");
        t.setDaemon(true);
        t.start();
    }

    public record EventCard(String server, int serverNum, String version, String eventId,
                            String phase, int secondsLeft, String loot, String eventType) {

        String displayName() {
            return switch (eventId) {
                case "vulkan" -> "Вулкан";
                case "beacon" -> "Маяк";
                case "hellm" -> "Адский маяк";
                case "airdrop" -> "Аирдроп";
                case "myst_beacon" -> "Мист. маяк";
                case "meteor_rain" -> "Метеоритный дождь";
                case "ожидание" -> "Ожидание ивента";
                case "supply_drop" -> "Сброс припасов";
                case "boss" -> "Босс";
                case "treasure" -> "Сокровище";
                case "raid" -> "Рейд";
                default -> eventId;
            };
        }

        String statusStr(long elapsedSec) {
            int remaining;
            if ("LOOTING".equals(phase)) {
                remaining = Math.max(0, 60 - (int) elapsedSec);
            } else {
                remaining = Math.max(0, secondsLeft - (int) elapsedSec);
            }
            String time = formatTime(remaining);
            if (phase == null) return "До спавна: " + time;
            return switch (phase) {
                case "ACTIVATING" -> "Появится через: " + time;
                case "RUNNING"    -> "До активации: " + time;
                case "OPENED"     -> "До открытия: " + time;
                case "LOOTING"    -> "До закрытия: " + time;
                case "WAITING"    -> "До спавна: " + time;
                default           -> "Статус: " + time;
            };
        }

        private static String formatTime(int sec) {
            int m = sec / 60, s = sec % 60;
            return m > 0 ? m + " мин " + s + " сек" : s + " сек";
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastFetch > REFRESH_MS && !fetching) {
            fetching = true;
            Thread t = new Thread(this::fetchAll, "EventsFetch");
            t.setDaemon(true);
            t.start();
        }
    }

    private void fetchAll() {
        try {
            List<EventCard> result = new ArrayList<>();
            fetchServers(new int[]{100,101,102,103,104,105,200,201,202,203,204,205,206,207,208,209,210,300,301,302,303,400,401,500,501,600,700,800,900,904}, "1.21", result);
            fetchServers(new int[]{1001,1002,1003,1004,1005,2001,2002,2003,2004,2005,3001,3002,3003,4001,4002,5001,5002,6001,6002,6003,6004}, "1.16.5", result);
            result.removeIf(c -> c.eventId() == null);
            result.removeIf(c -> "OPENED".equals(c.phase()) || "LOOTING".equals(c.phase()));
            result.sort(Comparator.comparingInt(EventCard::secondsLeft));
            ftCards.clear();
            ftCards.addAll(result);
            ftStatus = ftCards.isEmpty() ? "Нет активных ивентов" : "";
        } catch (Exception e) {
            ftStatus = "FT: " + e.getClass().getSimpleName();
        }

        try {
            List<EventCard> mgEvResult = new ArrayList<>();
            List<EventCard> mgMineResult = new ArrayList<>();
            fetchMixerGrief(mgEvResult, mgMineResult);
            mgCards.clear();
            mgCards.addAll(mgEvResult);
            mgMines.clear();
            mgMines.addAll(mgMineResult);
            mgStatus = "";
        } catch (Exception e) {
            mgStatus = "Недоступно (порт 8000)";
        }

        fetchTimestamp = System.currentTimeMillis();
        lastFetch = System.currentTimeMillis();
        fetching = false;
    }

    private void fetchServers(int[] nums, String ver, List<EventCard> out) {
        for (int i = 0; i < nums.length; i += MAX_PER_REQ) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < Math.min(i + MAX_PER_REQ, nums.length); j++) {
                if (j > i) sb.append(",");
                sb.append("anarchy").append(nums[j]);
            }
            try { fetchBatch(sb.toString(), ver, out); } catch (Exception ignored) {}
        }
    }

    private void fetchMixerGrief(List<EventCard> evOut, List<EventCard> mineOut) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; (Invoke-RestMethod -Uri 'http://api-v1.mixergrief.pw:8000/api/events' -Method Get) | ConvertTo-Json -Depth 5 -Compress");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String jsonStr;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new InputStreamReader(proc.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            jsonStr = sb.toString();
        }
        proc.waitFor();
        if (jsonStr.isEmpty()) return;

        JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();

        for (String key : json.keySet()) {
            JsonObject serverObj = json.getAsJsonObject(key);
            String serverId = serverObj.has("serverId") ? serverObj.get("serverId").getAsString() : key;
            int serverNum = 0;
            try { serverNum = Integer.parseInt(serverId.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            final int sNum = serverNum;

            JsonObject events = serverObj.has("events") ? serverObj.getAsJsonObject("events") : null;
            if (events != null) {
                int secsUntilNext = events.has("secondsUntilNextEvent") ? events.get("secondsUntilNextEvent").getAsInt() : -1;
                boolean hasActive = false;

                if (events.has("activeEvents") && events.getAsJsonArray("activeEvents").size() > 0) {
                    for (JsonElement el : events.getAsJsonArray("activeEvents")) {
                        JsonObject ev = el.getAsJsonObject();
                        String type = ev.has("type") ? ev.get("type").getAsString() : "unknown";
                        String status = ev.has("virtualStatus") ? ev.get("virtualStatus").getAsString() : (ev.has("status") ? ev.get("status").getAsString() : "");
                        evOut.add(new EventCard(serverId, sNum, "MG", mgEventName(type), status, 0, null, "active"));
                        hasActive = true;
                    }
                }
                if (!hasActive && secsUntilNext > 0) {
                    evOut.add(new EventCard(serverId, sNum, "MG", "Ожидание ивента", null, secsUntilNext, null, "system"));
                }
            }

            JsonObject mines = serverObj.has("mines") ? serverObj.getAsJsonObject("mines") : null;
            if (mines != null) {
                for (String mineKey : mines.keySet()) {
                    JsonObject mine = mines.getAsJsonObject(mineKey);
                    String level = mine.has("currentLevel") ? mine.get("currentLevel").getAsString() : "?";
                    level = level.replaceAll("\u00A7.", "");
                    int timeToNext = mine.has("timeToNextMine") ? mine.get("timeToNextMine").getAsInt() : 0;
                    mineOut.add(new EventCard("Анархия-" + sNum, sNum, "MG", "Шахта", level, timeToNext, null, "mine"));
                }
            }
        }
        evOut.sort(Comparator.comparingInt(EventCard::secondsLeft));
        mineOut.sort(Comparator.comparingInt(EventCard::secondsLeft));
    }

    private String mgEventName(String type) {
        return switch (type.toUpperCase()) {
            case "METEORITE" -> "Метеорит";
            case "AIRDROP"   -> "Аирдроп";
            case "BOSS"      -> "Босс";
            case "TREASURE"  -> "Сокровище";
            case "RAID"      -> "Рейд";
            case "SUPPLY"    -> "Припасы";
            case "MYSTIC"    -> "Мистический";
            default          -> type;
        };
    }

    private void fetchBatch(String servers, String ver, List<EventCard> out) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL + "?event-type=all&server-type=" + servers).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization-Token", TOKEN);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (conn.getResponseCode() != 200) return;
        JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
        JsonArray resp = json.getAsJsonArray("response");
        if (resp == null) return;
        for (JsonElement el : resp) {
            JsonObject sObj = el.getAsJsonObject();
            String sName = sObj.get("server").getAsString();
            int num = Integer.parseInt(sName.replace("anarchy", ""));
            JsonArray evs = sObj.getAsJsonArray("events");
            if (evs == null) continue;
            for (JsonElement evEl : evs) {
                JsonObject ev = evEl.getAsJsonObject();
                int timeLeft = ev.get("time-seconds-left").getAsInt();
                String id = ev.has("id") && !ev.get("id").isJsonNull() ? ev.get("id").getAsString() : null;
                String phase = ev.has("phase") && !ev.get("phase").isJsonNull() ? ev.get("phase").getAsString() : null;
                String loot = ev.has("loot") && !ev.get("loot").isJsonNull() ? ev.get("loot").getAsString() : null;
                if ("null".equals(loot)) loot = null;
                String evType = ev.has("event-type") ? ev.get("event-type").getAsString() : "system";
                if (id != null || timeLeft > 0) {
                    out.add(new EventCard(sName, num, ver, id != null ? id : "ожидание", phase, timeLeft, loot, evType));
                }
            }
        }
    }

    /** Рендер. Возвращает общую высоту контента (для скролла). */
    public float render(DrawContext ctx, float x, float y, float w, float h,
                        int mouseX, int mouseY, float a, float scroll) {
        tick();
        int alpha = (int)(255 * a);
        float gap = 3f, cardH = 42f, cardGap = 3f;
        float headerH = 14f;

        float sectionW = w / 2f;
        float colW = (sectionW - gap * 2f) / 2f;

        float startY = y + scroll;

        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "FunTime",
                x + sectionW / 2f - Fonts.SFMEDIUM.get().getWidth("FunTime", 7.5f) / 2f, startY,
                ColorProvider.setAlpha(ColorProvider.getColorClient(), alpha), 7.5f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), "MixerGrief",
                x + sectionW + sectionW / 2f - Fonts.SFMEDIUM.get().getWidth("MixerGrief", 7.5f) / 2f, startY,
                ColorProvider.setAlpha(ColorProvider.getColorClient(), alpha), 7.5f);

        float contentStartY = startY + headerH;

        if (!ftStatus.isEmpty() && ftCards.isEmpty()) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), ftStatus, x + 4f, contentStartY + 4f,
                    ColorProvider.rgba(140, 140, 150, alpha), 6f);
        }

        float[] ftColY = {contentStartY, contentStartY};
        long elapsedSec = (System.currentTimeMillis() - fetchTimestamp) / 1000;
        for (EventCard card : ftCards) {
            int col = ftColY[0] <= ftColY[1] ? 0 : 1;
            float cx = x + gap + col * (colW + gap);
            float cy = ftColY[col];
            if (cy + cardH >= y && cy <= y + h) {
                renderCard(ctx, card, cx, cy, colW, cardH, mouseX, mouseY, alpha, elapsedSec);
            }
            ftColY[col] += cardH + cardGap;
        }

        float mgX = x + sectionW + 2f;
        DrawUtil.drawRound(x + sectionW - 0.5f, startY, 0.5f, h - (startY - y),
                0.2f, ColorProvider.rgba(255, 255, 255, (int)(30 * a)));

        if (!mgStatus.isEmpty() && mgCards.isEmpty() && mgMines.isEmpty()) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), mgStatus, mgX + 4f, contentStartY + 4f,
                    ColorProvider.rgba(140, 140, 150, alpha), 6f);
        }

        float mgEvY = contentStartY;
        for (EventCard card : mgCards) {
            if (mgEvY + cardH >= y && mgEvY <= y + h) {
                renderCard(ctx, card, mgX + gap, mgEvY, colW, cardH, mouseX, mouseY, alpha, elapsedSec);
            }
            mgEvY += cardH + cardGap;
        }

        float mgMineY = contentStartY;
        for (EventCard card : mgMines) {
            if (mgMineY + cardH >= y && mgMineY <= y + h) {
                renderCard(ctx, card, mgX + gap + colW + gap, mgMineY, colW, cardH, mouseX, mouseY, alpha, elapsedSec);
            }
            mgMineY += cardH + cardGap;
        }

        float maxY = Math.max(Math.max(ftColY[0], ftColY[1]), Math.max(mgEvY, mgMineY));
        return maxY - startY;
    }

    private void renderCard(DrawContext ctx, EventCard card, float x, float y, float w, float h,
                            int mouseX, int mouseY, int alpha, long elapsedSec) {
        float a = alpha / 255f;
        int bgTop = ColorProvider.rgba(22, 22, 32, (int)(220 * a));
        int bgBot = ColorProvider.rgba(16, 16, 24, (int)(220 * a));
        DrawUtil.drawRoundBlur(x, y, w, h, 4f, ColorProvider.rgba(18, 18, 26, (int)(180 * a)), 8f);
        DrawUtil.drawRound(x, y, w, h, 4f, bgTop, bgBot);
        DrawUtil.drawRound(x - 0.3f, y - 0.3f, w + 0.6f, h + 0.6f, 4.3f,
                ColorProvider.rgba(255, 255, 255, (int)(14 * a)));

        float pad = 5f, fontSize = 5.5f;
        float curY = y + pad;

        DrawUtil.drawText(Fonts.SFMEDIUM.get(), card.displayName(), x + pad, curY,
                ColorProvider.setAlpha(ColorProvider.getColorClient(), alpha), 6.5f);
        curY += 9f;

        String info = card.version().equals("MG")
                ? "Анархия-" + card.serverNum()
                : "Анархия " + card.serverNum() + " • " + card.version();
        DrawUtil.drawText(Fonts.SFREGULAR.get(), info, x + pad, curY,
                ColorProvider.rgba(170, 170, 180, alpha), fontSize);
        curY += 7.5f;

        String statusStr = card.statusStr(elapsedSec);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), statusStr, x + pad, curY,
                ColorProvider.rgba(130, 130, 145, alpha), fontSize);
        curY += 7.5f;

        if (card.loot() != null) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Лут: " + card.loot(), x + pad, curY,
                    ColorProvider.rgba(130, 130, 145, alpha), fontSize);
        } else if (card.phase() != null && "mine".equals(card.eventType())) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Редкость: " + card.phase(), x + pad, curY,
                    ColorProvider.rgba(130, 130, 145, alpha), fontSize);
        }

        float btnH = 9f;
        float btnY = y + h - btnH - 3f;
        String btnText = "/an" + card.serverNum();
        float btnTW = Fonts.SFMEDIUM.get().getWidth(btnText, 5f);
        float btnW = btnTW + 8f;
        float btnX = x + w - btnW - pad;
        boolean hov = HoverUtil.isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
        if (hov) CursorManager.requestHand();
        int btnBg = hov
                ? ColorProvider.setAlpha(ColorProvider.getColorClient(), (int)(220 * a))
                : ColorProvider.setAlpha(ColorProvider.getColorVisualModules(), (int)(100 * a));
        DrawUtil.drawRound(btnX, btnY, btnW, btnH, btnH / 2f, btnBg);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), btnText, btnX + (btnW - btnTW) / 2f, btnY + (btnH - 5f) / 2f - 0.5f,
                ColorProvider.rgba(255, 255, 255, alpha), 5f);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                float x, float y, float w, float h, float scroll) {
        if (button != 0) return false;
        float gap = 3f, cardH = 42f, cardGap = 3f, headerH = 14f;
        float sectionW = w / 2f;
        float colW = (sectionW - gap * 2f) / 2f;
        float contentStartY = y + scroll + headerH;
        float pad = 5f;

        float[] ftColY = {contentStartY, contentStartY};
        for (EventCard card : ftCards) {
            int col = ftColY[0] <= ftColY[1] ? 0 : 1;
            float cx = x + gap + col * (colW + gap);
            float cy = ftColY[col];
            if (checkCardClick(mouseX, mouseY, card, cx, cy, colW, cardH, pad)) return true;
            ftColY[col] += cardH + cardGap;
        }

        float mgX = x + sectionW + 2f;
        float mgEvY = contentStartY;
        for (EventCard card : mgCards) {
            if (checkCardClick(mouseX, mouseY, card, mgX + gap, mgEvY, colW, cardH, pad)) return true;
            mgEvY += cardH + cardGap;
        }

        return false;
    }

    private boolean checkCardClick(double mouseX, double mouseY, EventCard card,
                                   float cx, float cy, float colW, float cardH, float pad) {
        String btnText = "/an" + card.serverNum();
        float btnTW = Fonts.SFMEDIUM.get().getWidth(btnText, 5f);
        float btnW = btnTW + 8f;
        float btnH = 9f;
        float btnX = cx + colW - btnW - pad;
        float btnY = cy + cardH - btnH - 3f;
        if (HoverUtil.isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
            if (mc.player != null) {
                mc.player.networkHandler.sendChatCommand("an" + card.serverNum());
            }
            return true;
        }
        return false;
    }
}
