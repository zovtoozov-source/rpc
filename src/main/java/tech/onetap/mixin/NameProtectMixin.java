package tech.onetap.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import tech.onetap.Onetap;
import tech.onetap.module.list.misc.NameProtect;
import tech.onetap.util.friend.Friend;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.replace.ReplaceUtil;

@Mixin(EntityRenderer.class)
public class NameProtectMixin {

    @ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true)
    private Text modifyLabelText(Text original) {
        try {
            if (original == null) return original;
            String text = original.getString();
            if (text == null || text.isEmpty()) return original;

            NameProtect np = Onetap.getInstance().getModuleStorage().get(NameProtect.class);
            if (np == null || !np.isEnabled()) return original;

            String me = MinecraftClient.getInstance().player.getNameForScoreboard();
            String nick = np.customName.getValue();
            String replacement = nick.isEmpty() ? "Protected" : nick;

            if (text.contains(me)) {
                return ReplaceUtil.replaceLiteral(original, me, replacement);
            }

            if (np.hideFriends.getValue()) {
                var friends = FriendRepository.getFriends();
                for (Friend friend : friends) {
                    if (text.contains(friend.name())) {
                        return ReplaceUtil.replaceLiteral(original, friend.name(), replacement);
                    }
                }
            }

            return original;
        } catch (Exception e) {
            return original;
        }
    }
}
