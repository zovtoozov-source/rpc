package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "DragonFly", moduleDesc = "Ускоряет полёт в креативе", moduleCategory = ModuleCategory.MOVEMENT)
public class DragonFly extends Module {

    private final SliderSetting speed = new SliderSetting("Скорость", 2.0, 0.5, 5.0, 0.1);
    private final BooleanSetting instantMotion = new BooleanSetting("Резкие движения", true);

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null) {
            mc.player.getAbilities().setFlySpeed(0.05f);
            mc.player.sendAbilitiesUpdate();
        }
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null) return;

        if (mc.player.getAbilities().flying) {
            float flySpeed = (float) speed.getValue() * 0.05f;
            mc.player.getAbilities().setFlySpeed(flySpeed);
            mc.player.sendAbilitiesUpdate();
        } else {
            mc.player.getAbilities().setFlySpeed(0.05f);
            mc.player.sendAbilitiesUpdate();
        }

        if (!mc.player.getAbilities().flying || !instantMotion.getValue()) return;

        Vec3d velocity = mc.player.getVelocity();
        float forward = mc.player.input.movementForward;
        float sideways = mc.player.input.movementSideways;

        if (forward == 0 && sideways == 0) {
            mc.player.setVelocity(0, velocity.y, 0);
        } else {
            double speedVal = speed.getValue();
            Vec3d forwardVec = Vec3d.fromPolar(0, mc.player.getYaw()).normalize();
            Vec3d rightVec = Vec3d.fromPolar(0, mc.player.getYaw() - 90).normalize();

            double velX = forwardVec.x * forward * speedVal + rightVec.x * sideways * speedVal;
            double velZ = forwardVec.z * forward * speedVal + rightVec.z * sideways * speedVal;

            mc.player.setVelocity(velX, velocity.y, velZ);
        }

        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, speed.getValue() * 0.2, mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -speed.getValue() * 0.2, mc.player.getVelocity().z);
        }
    }
}
