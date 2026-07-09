package tech.onetap.event.list;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.Event;

@Getter
@Setter
public class CameraPositionEvent extends Event {
    private Vec3d pos;
    private final float tickDelta;

    public CameraPositionEvent(Vec3d pos, float tickDelta) {
        this.pos = pos;
        this.tickDelta = tickDelta;
    }
}
