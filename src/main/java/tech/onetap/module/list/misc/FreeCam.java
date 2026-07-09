package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.CameraPositionEvent;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;

import java.util.UUID;

@ModuleInformation(moduleName = "FreeCam", moduleCategory = ModuleCategory.MISC)
public class FreeCam extends Module {
    private static final double TICK_SPEED_MULTIPLIER = 0.25;

    private final SliderSetting speed = new SliderSetting("Скорость", 2, 0.5f, 5, 0.1f);
    private final BooleanSetting freeze = new BooleanSetting("Заморозка", true);
    private final BooleanSetting removePackets = new BooleanSetting("Отмена пакетов", true);
    private final BooleanSetting dummy = new BooleanSetting("Думми", true);

    private Vec3d pos;
    private Vec3d prevPos;
    private OtherClientPlayerEntity dummyPlayer;

    @Override
    public void onEnable() {
        if (mc.player != null) {
            pos = mc.gameRenderer.getCamera().getPos();
            prevPos = pos;
            spawnDummy();
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        removeDummy();
        pos = null;
        prevPos = null;
        super.onDisable();
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        if (e.getPacket() instanceof GameJoinS2CPacket || e.getPacket() instanceof PlayerRespawnS2CPacket) {
            setEnabled(false);
            return;
        }

        if (freeze.getValue() && removePackets.getValue() && e.getPacket() instanceof PlayerMoveC2SPacket) {
            e.cancelEvent();
        }
    }

    @Subscribe
    public void onTick(EventTick e) {
        if (mc.player == null || pos == null) return;

        prevPos = pos;
        double spd = speed.getFloatValue() * TICK_SPEED_MULTIPLIER;

        double vertical = 0.0;
        if (mc.options.jumpKey.isPressed()) vertical += spd;
        if (mc.options.sneakKey.isPressed()) vertical -= spd;

        double forward = 0.0;
        double sideways = 0.0;
        if (mc.options.forwardKey.isPressed()) forward += spd;
        if (mc.options.backKey.isPressed()) forward -= spd;
        if (mc.options.leftKey.isPressed()) sideways += spd;
        if (mc.options.rightKey.isPressed()) sideways -= spd;

        if (forward != 0.0 || sideways != 0.0 || vertical != 0.0) {
            double yawRad = Math.toRadians(mc.player.getYaw());
            double sin = Math.sin(yawRad);
            double cos = Math.cos(yawRad);
            pos = pos.add(sideways * cos - forward * sin, vertical, forward * cos + sideways * sin);
        }

        if (freeze.getValue()) {
            mc.player.setVelocity(Vec3d.ZERO);
        }

        syncDummy();
    }

    @Subscribe
    public void onCameraPosition(CameraPositionEvent e) {
        if (pos == null || prevPos == null) return;

        double tickDelta = e.getTickDelta();
        Vec3d interpolated = prevPos.lerp(pos, tickDelta);
        e.setPos(interpolated);
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent e) {
        e.forward = 0.0f;
        e.strafe = 0.0f;
        e.jump = false;
        e.sneak = false;
        e.cancel();
    }

    private void spawnDummy() {
        if (!dummy.getValue() || mc.player == null || mc.world == null || dummyPlayer != null) return;

        GameProfile profile = new GameProfile(UUID.randomUUID(), mc.player.getName().getString());
        dummyPlayer = new OtherClientPlayerEntity(mc.world, profile);
        dummyPlayer.copyFrom(mc.player);
        dummyPlayer.setPosition(mc.player.getPos());
        dummyPlayer.setYaw(mc.player.getYaw());
        dummyPlayer.setPitch(mc.player.getPitch());
        dummyPlayer.bodyYaw = mc.player.bodyYaw;
        dummyPlayer.headYaw = mc.player.headYaw;
        mc.world.addEntity(dummyPlayer);
    }

    private void syncDummy() {
        if (dummyPlayer == null || mc.player == null) return;

        dummyPlayer.setPosition(mc.player.getPos());
        dummyPlayer.setYaw(mc.player.getYaw());
        dummyPlayer.setPitch(mc.player.getPitch());
        dummyPlayer.bodyYaw = mc.player.bodyYaw;
        dummyPlayer.headYaw = mc.player.headYaw;
        dummyPlayer.setSneaking(mc.player.isSneaking());
        dummyPlayer.setSprinting(mc.player.isSprinting());
        dummyPlayer.setHealth(mc.player.getHealth());
        dummyPlayer.age = mc.player.age;
    }

    private void removeDummy() {
        if (mc.world != null && dummyPlayer != null) {
            mc.world.removeEntity(dummyPlayer.getId(), Entity.RemovalReason.DISCARDED);
            dummyPlayer = null;
        }
    }
}
