package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.player.other.SlownessManager;
import tech.onetap.util.player.other.WorldUtils;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.List;

@ModuleInformation(moduleName = "High Jump", moduleCategory = ModuleCategory.MOVEMENT)
public class HighJump extends Module {

    private boolean hasPlaced;
    private boolean hasOpened;
    private int pickedSlot;
    private BlockPos shulkerPos;

    @Subscribe
    public void onUpdate(final EventTick ignored) {
        if (mc.player == null) return;

        var slot = InventoryUtil.searchItem(List.of(Items.SHULKER_BOX, Items.BLACK_SHULKER_BOX, Items.BROWN_SHULKER_BOX, Items.CYAN_SHULKER_BOX, Items.BLUE_SHULKER_BOX));

        if (hasOpened) {
            pushPlayer();
            return;
        }

        if (slot == -1) return;

        final var nearestBlock = WorldUtils.findNearestPlaceableBlock();

        if (nearestBlock != null) {
            RotationComponent.update(new Rotation(RotationUtil.calculate(nearestBlock.toCenterPos())), 360, 360, 360, 360, 0, 10, false);
            SlownessManager.addTask(new SlownessManager.SlowTask(0, () -> {
                if (!hasPlaced) {
                    if (mc.crosshairTarget instanceof BlockHitResult hitResult) {
                        shulkerPos = nearestBlock;
                        if (slot >= 0 && slot <= 8) {
                            pickedSlot = slot;
                            mc.player.getInventory().selectedSlot = slot;
                        } else {
                            InventoryUtil.swapWithBypassGrim(() -> {
                                pickedSlot = slot;
                                mc.interactionManager.clickSlot(0, slot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
                            });
                        }
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                        if (pickedSlot >= 0 && pickedSlot <= 8) mc.player.getInventory().selectedSlot = pickedSlot;
                        else InventoryUtil.swapWithBypassGrim(() -> mc.interactionManager.clickSlot(0, pickedSlot   , mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player));
                        hasPlaced = true;
                    }
                    if (hasOpened) return;
                    RotationComponent.update(new Rotation(RotationUtil.calculate(shulkerPos.toCenterPos())), 360, 360, 360, 360, 0, 1424821, false);
                    SlownessManager.addTask(new SlownessManager.SlowTask(50, () -> {
                        if (shulkerPos == null) return;
                        if (mc.crosshairTarget instanceof BlockHitResult hitResult) {
                            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                        }
                        hasOpened = true;
                    }));
                } else {
                }
            }));
        }
    }

    private void pushPlayer() {
        for (BlockPos pos : BlockPos.iterate(
                mc.player.getBlockPos().add(-6, -6, -6),
                mc.player.getBlockPos().add(6, 6, 6))) {

            BlockEntity be = mc.world.getBlockEntity(pos);
            if (!(be instanceof ShulkerBoxBlockEntity shulker)) {
                continue;
            }

            double dx = mc.player.getX() - (pos.getX() + 0.5);
            double dz = mc.player.getZ() - (pos.getZ() + 0.5);
            double dy = mc.player.getY() - (pos.getY() + 0.5);

            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDistance > 1.5f) {
                setEnabled(false);
                continue;
            }
            if (Math.abs(dy) > (mc.player.getVelocity().y > 1.0 ? 30.0 : 2.0)) continue;
            if (!mc.player.isOnGround()) continue;

            float progress = shulker.getAnimationProgress(1.0f);

            if (progress > 0.0f && progress <= 1.0f) {
                Vec3d vel = mc.player.getVelocity();
                mc.player.setVelocity(vel.x, 2.33f, vel.z);
                mc.player.velocityDirty = true;
                setEnabled(false);
                break;
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        hasOpened = false;
        hasPlaced = false;
    }
}