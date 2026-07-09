package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInformation(moduleName = "Well Helper", moduleDesc = "Помощник для Well", moduleCategory = ModuleCategory.MISC)
public class WellHelper extends Module {

    private final BooleanSetting invseeElytraCheck = new BooleanSetting("Invsee элитры", true);

    private final List<String> pendingPlayers = new ArrayList<>();
    private String currentInvseePlayer = null;
    private boolean waitingForInvsee = false;
    private boolean collectingPlayers = false;
    private int tickDelay = 0;
    private int collectTimeout = 0;

    private static final Pattern PLAYER_PATTERN = Pattern.compile("([a-zA-Z0-9_]{3,16})\\s+\\d+");

    @Subscribe
    private void onPacket(EventPacket e) {
        if (mc.player == null || mc.world == null) return;
        if (e.getType() != EventPacket.Type.RECEIVE) return;

        if (e.getPacket() instanceof GameMessageS2CPacket p && invseeElytraCheck.getValue()) {
            String text = p.content().getString();

            if (text.toLowerCase().contains("игроки рядом")) {
                pendingPlayers.clear();
                currentInvseePlayer = null;
                waitingForInvsee = false;
                collectingPlayers = true;
                collectTimeout = 6;
                return;
            }

            if (collectingPlayers) {
                Matcher matcher = PLAYER_PATTERN.matcher(text);
                if (matcher.find()) {
                    String playerName = matcher.group(1);
                    if (!playerName.equalsIgnoreCase(mc.player.getName().getString())) {
                        if (!pendingPlayers.contains(playerName)) {
                            pendingPlayers.add(playerName);
                        }
                    }
                    collectTimeout = 6;
                }
            }
        }
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null || mc.world == null || !invseeElytraCheck.getValue()) return;

        if (collectingPlayers) {
            collectTimeout--;
            if (collectTimeout <= 0) {
                collectingPlayers = false;
                if (!pendingPlayers.isEmpty()) {
                    tickDelay = 10;
                }
            }
            return;
        }

        if (tickDelay > 0) {
            tickDelay--;
            if (tickDelay == 0 && !pendingPlayers.isEmpty() && !waitingForInvsee) {
                checkNextPlayer();
            }
            return;
        }

        if (waitingForInvsee && currentInvseePlayer != null) {
            if (mc.currentScreen instanceof GenericContainerScreen screen) {
                boolean hasElytra = false;

                int containerSlots = screen.getScreenHandler().getRows() * 9;
                for (int i = 0; i < containerSlots; i++) {
                    Slot slot = screen.getScreenHandler().slots.get(i);
                    ItemStack stack = slot.getStack();
                    if (stack.getItem() == Items.ELYTRA) {
                        hasElytra = true;
                        break;
                    }
                }

                if (hasElytra) {
                    logDirect(Text.literal(currentInvseePlayer).formatted(Formatting.GREEN)
                            .append(Text.literal(" имеет ЭЛИТРЫ!").formatted(Formatting.LIGHT_PURPLE)));
                }

                mc.player.closeHandledScreen();
                currentInvseePlayer = null;
                waitingForInvsee = false;

                if (!pendingPlayers.isEmpty()) {
                    tickDelay = 6;
                }
            }
        }
    }

    private void checkNextPlayer() {
        if (pendingPlayers.isEmpty() || mc.getNetworkHandler() == null) return;

        currentInvseePlayer = pendingPlayers.remove(0);
        waitingForInvsee = true;
        mc.getNetworkHandler().sendChatCommand("invsee " + currentInvseePlayer);
    }
}
