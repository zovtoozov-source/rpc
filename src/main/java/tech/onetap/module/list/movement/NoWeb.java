package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventCobweb;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.player.move.MoveUtil;

@ModuleInformation(moduleName = "No Web", moduleCategory = ModuleCategory.MOVEMENT)
public class NoWeb extends Module {

    @Subscribe
    private void onCobweb(EventCobweb e) {
        Vec3d velocity = mc.player.getVelocity();

        float yaw = mc.player.getYaw();
        double forward = 0;
        double strafe = 0;

        if (mc.player.input.playerInput.forward()) forward += 1;
        if (mc.player.input.playerInput.backward()) forward -= 1;

        if (mc.player.input.playerInput.left()) strafe += 1;
        if (mc.player.input.playerInput.right()) strafe -= 1;

        if (!(forward == 0 && strafe == 0)) {

            if (forward != 0) {
                if (strafe > 0) yaw += (forward > 0 ? -45 : 45);
                else if (strafe < 0) yaw += (forward > 0 ? 45 : -45);

                strafe = 0;

                if (forward > 0) forward = 1;
                else forward = -1;
            }

            double movementYaw = Math.toDegrees(
                    Math.atan2(strafe, forward)
            ) + yaw;

            yaw = (float) ((movementYaw % 360 + 360) % 360);
        }

        float result = 0.2f;

        if ((yaw >= 313 && yaw <= 317) || (yaw >= 223 && yaw <= 227) || (yaw >= 133 && yaw <= 137) || (yaw >= 43 && yaw <= 47)) {
            result = 0.295f;
        } else if ((yaw >= 311 && yaw <= 319) || (yaw >= 221 && yaw <= 229) || (yaw >= 131 && yaw <= 139) || (yaw >= 41 && yaw <= 49)) {
            result = 0.292f;
        } else if ((yaw >= 310.8f && yaw <= 320.8f) || (yaw >= 220.8f && yaw <= 230.8f) || (yaw >= 130.8f && yaw <= 140.8f) || (yaw >= 40.8f && yaw <= 50.8f)) {
            result = 0.284f;
        } else if ((yaw >= 308.7f && yaw <= 322.7f) || (yaw >= 218.7f && yaw <= 232.7f) || (yaw >= 128.7f && yaw <= 142.7f) || (yaw >= 38.7f && yaw <= 52.7f)) {
            result = 0.275f;
        } else if ((yaw >= 306.5f && yaw <= 324.5f) || (yaw >= 216.5f && yaw <= 234.5f) || (yaw >= 126.5f && yaw <= 144.5f) || (yaw >= 36.5f && yaw <= 54.5f)) {
            result = 0.268f;
        } else if ((yaw >= 304f && yaw <= 327f) || (yaw >= 214f && yaw <= 237f) || (yaw >= 124f && yaw <= 147f) || (yaw >= 34f && yaw <= 57f)) {
            result = 0.255f;
        }

        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(velocity.x, 1.219f, velocity.z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(velocity.x, mc.player.isGliding() ? -1.5f : -3.5f, velocity.z);
        } else {
            mc.player.setVelocity(velocity.x, 0, velocity.z);
        }

        MoveUtil.setMotion(result);
    }
}