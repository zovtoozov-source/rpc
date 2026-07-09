package tech.onetap.util.rotation;

import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.MathHelper;
import tech.onetap.event.list.LookEvent;
import tech.onetap.event.list.RotationEvent;

public class FreeLookComponent extends Component {

    @Getter
    @Setter
    private static boolean active;
    @Getter
    @Setter
    private static float freeYaw, freePitch;

    public static void activate(float yaw, float pitch) {
        freeYaw = yaw;
        freePitch = pitch;
        active = true;
    }

    @Subscribe
    public void onEvent(LookEvent event) {
        if (active) {
            rotateTowards(event.getYaw(), event.getPitch());
            event.setCancelled(true);
        }
    }

    @Subscribe
    public void onEvent(RotationEvent event) {
        if (active) {
            event.setYaw(freeYaw);
            event.setPitch(freePitch);
        } else {
            freeYaw = event.getYaw();
            freePitch = event.getPitch();
        }
    }

    private void rotateTowards(double targetYaw, double targetPitch) {
        freePitch = MathHelper.clamp((float) (freePitch + targetPitch * 0.15), -90.0F, 90.0F);
        freeYaw = (float) (freeYaw + targetYaw * 0.15);
    }
}
