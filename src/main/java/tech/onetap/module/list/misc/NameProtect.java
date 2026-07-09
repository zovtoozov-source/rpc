package tech.onetap.module.list.misc;

import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.StringSetting;
import tech.onetap.util.friend.Friend;
import tech.onetap.util.friend.FriendRepository;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInformation(moduleName = "Streamer Mode", moduleCategory = ModuleCategory.MISC)
public class NameProtect extends Module {
    private static final String DEFAULT_NAME = "Protected";
    private static final String STAFF_RANK = " §cstaff";
    private static final Pattern RANK_PATTERN = Pattern.compile("(?iu)(.*?р\\s*а\\s*н\\s*г\\s*[:：]\\s*)(?:§[0-9a-fk-or])*.*");
    private static final Pattern RANK_PREFIX_PATTERN = Pattern.compile("(?iu).*р\\s*а\\s*н\\s*г\\s*[:：]\\s*$");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);

    public final BooleanSetting hideFriends = new BooleanSetting("Скрыть друзей", false);
    public final BooleanSetting hideRank = new BooleanSetting("Скрыть ранг", true);
    public final StringSetting customName = new StringSetting("Свой ник", "");

    private boolean skipNextRankPart;

    public String getCustomName() {
        if (!isEnabled() || mc.player == null) return mc.player.getNameForScoreboard();
        return getReplacementName();
    }

    public String getCustomName(String originalName) {
        if (!isEnabled() || mc.player == null || originalName == null) {
            skipNextRankPart = false;
            return originalName;
        }

        String result = replaceRank(originalName);
        if (result.isEmpty()) return result;

        String replacement = getReplacementName();
        String me = mc.player.getNameForScoreboard();

        if (result.contains(me)) {
            return result.replace(me, replacement);
        }

        if (hideFriends.getValue()) {
            var friends = FriendRepository.getFriends();
            for (Friend friend : friends) {
                if (result.contains(friend.name())) {
                    return result.replace(friend.name(), replacement);
                }
            }
        }

        return result;
    }

    private String getReplacementName() {
        String nick = customName.getValue();
        return nick == null || nick.isBlank() ? DEFAULT_NAME : nick;
    }

    private String replaceRank(String text) {
        if (!hideRank.getValue()) {
            skipNextRankPart = false;
            return text;
        }

        if (skipNextRankPart) {
            String plain = stripColorCodes(text).trim();
            if (plain.isEmpty()) {
                return "";
            }
            if (!looksLikeRankPrefix(plain)) {
                skipNextRankPart = false;
                return "";
            }
            skipNextRankPart = false;
        }

        Matcher matcher = RANK_PATTERN.matcher(text);
        if (!matcher.find()) return text;

        String prefix = matcher.group(1);
        skipNextRankPart = true;
        return text.substring(0, matcher.start()) + prefix + STAFF_RANK;
    }

    private boolean looksLikeRankPrefix(String text) {
        return RANK_PREFIX_PATTERN.matcher(text).matches();
    }

    private String stripColorCodes(String text) {
        return COLOR_CODE_PATTERN.matcher(text).replaceAll("");
    }
}
