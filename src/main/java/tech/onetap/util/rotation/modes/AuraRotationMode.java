package tech.onetap.util.rotation.modes;

import net.minecraft.entity.LivingEntity;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.rotation.Rotation;

public interface AuraRotationMode extends IMinecraft {

    Rotation calculate(LivingEntity target);

    float yawSpeed();

    float pitchSpeed();

    default void reset() {}
}
