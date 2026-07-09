package tech.onetap.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.event.list.MoveInputEvent;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        KeyboardInput self = (KeyboardInput) (Object) this;
        PlayerInput input = self.playerInput;

        MoveInputEvent event = new MoveInputEvent(
                movementMultiplier(input.forward(), input.backward()),
                movementMultiplier(input.left(), input.right()),
                input.jump(),
                input.sneak(),
                0.3);
        event.post();

        if (event.isCancelled()) {
            this.movementForward = 0.0f;
            this.movementSideways = 0.0f;
            self.playerInput = new PlayerInput(false, false, false, false, false, false, false);
            return;
        }

        this.movementForward = event.getForward();
        this.movementSideways = event.getStrafe();
        self.playerInput = new PlayerInput(
                event.getForward() > 0.0f,
                event.getForward() < 0.0f,
                event.getStrafe() > 0.0f,
                event.getStrafe() < 0.0f,
                event.isJump(),
                event.isSneaking(),
                input.sprint()
        );
    }

    private float movementMultiplier(boolean positive, boolean negative) {
        return positive == negative ? 0.0f : positive ? 1.0f : -1.0f;
    }
}
