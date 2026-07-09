package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import tech.onetap.Onetap;
import tech.onetap.event.EventGameUpdate;
import tech.onetap.event.list.EventChangeSprint;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.player.FreeCamera;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.friend.FriendRepository;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.math.StopWatch;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.player.simulate.SimulatedPlayer;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;
import tech.onetap.util.text.ValueUnit;
import tech.onetap.util.neuro.rotation.AIRotationRecorder;
import tech.onetap.util.neuro.rotation.AIRotationManager;
import tech.onetap.event.list.MoveInputEvent;
import tech.onetap.util.rotation.FreeLookComponent;
import tech.onetap.util.rotation.modes.HolyWorldMode;

@ModuleInformation(moduleName = "KillAura", moduleCategory = ModuleCategory.COMBAT)
public class KillAura extends Module {

    public final ModeSetting rotation = new ModeSetting("Ротация", "ReallyWorld", "ReallyWorld", "RaidMine", "Wellmine old", "NoRot", "LonyGrief", "Sloth (TESTTT)", "HolyWorld", "Neuro");
    public final ModeSetting rotationBehavior = new ModeSetting("Поведение ротации", "Плавная", "Плавная", "Снапы");
    private final ModeListSetting targets = new ModeListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Голые", true),
            new BooleanSetting("Монстры", true),
            new BooleanSetting("Животные", true)
    );

    public final SliderSetting distance = new SliderSetting("Дистанция", ValueUnit.countable("блок", "блока", "блоков"), 3, 2, 6, 0.1f);
    private final SliderSetting preRotation = new SliderSetting("Пре дистанция", ValueUnit.countable("блок", "блока", "блоков"), 1.5f, 0, 3, 0.1f);
    public final BooleanSetting raycastCheck = new BooleanSetting("Проверка на наведение", true);
    public final BooleanSetting smartAim = new BooleanSetting("Умное наведение", true);
    public final BooleanSetting predictate = new BooleanSetting("Предикт на элитрах", true);
    public final SliderSetting predictValue = new SliderSetting("Предикт значение", 3, 1, 5, 0.1f).setVisible(() -> false);

    public final BooleanSetting hitThroughWalls = new BooleanSetting("Бить через стены", true);

    public final BooleanSetting hitAfterOvertake = new BooleanSetting("Бить токо после перегона", true).setVisible(() -> false);


    public final BooleanSetting onlySpace = new BooleanSetting("Только с пробелом", true);
    public final BooleanSetting clientLook = new BooleanSetting("Клиент лук", true);
    public final BooleanSetting showPredictPoint = new BooleanSetting("Показать предикт точку", true).setVisible(() -> false);
    public final BooleanSetting elytraTurnaround = new BooleanSetting("Разворот на элитрах", true).setVisible(() -> false);
    public final ModeSetting moveCorrection = new ModeSetting("Коррекция движения", "Нет", "Нет", "Таргет", "Свободная");

    public final BooleanSetting useResolver = new BooleanSetting("Резольвер (Elytra)", true).setVisible(() -> false);
    public boolean isResolving = false;
    public Vec3d resolverPoint = null;
    private final StopWatch resolverTimer = new StopWatch();


    public boolean isTurnaroundActive = false;
    private float RANDOM_STRENGTH = 0.75f;
    public static boolean isSlowdownActive = false;
    private static StopWatch stopWatch = new StopWatch();
    @Getter
    private LivingEntity target;
    public static LivingEntity lastTarget;
    public int ticksToAttack;

    private int razvorotikTicks;

    private boolean back;
    public float speedAcceleration;
    public float obhod;
    public static long lastPhysicalMoveTime;

    private final StopWatch turnaroundTimer = new StopWatch();

    public float preddict;
    public float lastYaw;
    public float lastPitch;

    private float previousDeltaYaw;
    private float previousDeltaPitch;

    private final HolyWorldMode holyWorldMode = new HolyWorldMode();

    private boolean renderListenerRegistered = false;
    private final WorldRenderEvents.Last renderListener = context -> {
        if (isEnabled() && showPredictPoint.getValue()) {
            renderPredictPoint(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true));
        }
    };

    private void findResolverPoint() {
        if (mc.player == null || mc.world == null) return;
        Vec3d eye = mc.player.getEyePos();

        float oppositeYaw = mc.player.getYaw() + 180f;
        float searchPitch = -50f;

        int[] yawOffsets = {0, 30, -30, 45, -45, 60, -60, 90, -90};

        for (int offset : yawOffsets) {
            float testYaw = oppositeYaw + offset;

            float radYaw = (float) Math.toRadians(testYaw);
            float radPitch = (float) Math.toRadians(searchPitch);

            double x = -Math.sin(radYaw) * Math.cos(radPitch);
            double y = -Math.sin(radPitch);
            double z = Math.cos(radYaw) * Math.cos(radPitch);

            Vec3d checkVec = new Vec3d(x, y, z).normalize().multiply(8.0);
            Vec3d endPoint = eye.add(checkVec);

            if (mc.world.raycast(new RaycastContext(eye, endPoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS) {
                resolverPoint = endPoint;
                return;
            }
        }
        resolverPoint = null;
    }

    @Subscribe
    private void onGameUpdate(EventGameUpdate e) {
        if (mc.player == null || target == null) return;

        Onetap.getInstance().getModuleStorage().setRandomness(1);

        if (AIRotationRecorder.isRecording()) {
            return;
        }

        if (isResolving) {
            if (resolverTimer.isReached(300)) {
                isResolving = false;
            } else if (resolverPoint != null) {
                var rot = new Rotation(RotationUtil.calculate(resolverPoint));
                RotationComponent.update(rot, 360, 360, 360, 360, 0, 1, clientLook.getValue());
                lastYaw = rot.getYaw();
                lastPitch = rot.getPitch();
                return;
            }
        }

        if (rotationBehavior.is("Снапы")) {
            boolean isReadyToAttack = mc.player.getAttackCooldownProgress(1.0f) >= 0.95f && ticksToAttack <= 1;
            if (!isReadyToAttack) {
                return;
            }
        }

        switch (rotation.getValue()) {
            case "ReallyWorld" -> updateVanillaRotation(target);
            case "RaidMine" -> updateSlothRotation(target);
            case "Wellmine old" -> updateWellmineRotation(target);
            case "NoRot" -> updateLonyJirRotation(target);
            case "LonyGrief" -> updateLonyGriefRotation(target);
            case "Sloth (TESTTT)" -> slothTest(target);
            case "HolyWorld" -> updateHolyWorldRotation(target);
            case "Neuro" -> updateNeuroRotation(target);
        }
    }

    @Subscribe
    private void onChangeSprint(EventChangeSprint e) {
        if (canStopSprinting()) e.setSprinting(false);
    }

    @Subscribe
    private void onMoveInput(MoveInputEvent e) {
        if (moveCorrection.is("Нет") || target == null) return;

        if (moveCorrection.is("Таргет")) {
            Vec3d targetCenter = target.getBoundingBox().getCenter();
            e.setYaw(new Rotation(RotationUtil.calculate(targetCenter)).getYaw(), mc.player.getYaw());
        } else if (moveCorrection.is("Свободная")) {
            e.setYaw(mc.player.getYaw(), FreeLookComponent.getFreeYaw());
        }
    }

    @Subscribe
    private void onUpdate(final EventTick ignored) {
        if (mc.player == null || mc.world == null) return;

        if (ticksToAttack > 0) ticksToAttack--;
        if (razvorotikTicks > 0) razvorotikTicks--;

        updateTarget();

        if (target != null) {
            lastTarget = target;
                isSlowdownActive = false;
            Vec3d predict = PredictUtils.getPredicted(target, predictValue.getValue());
            double distToPredict = mc.player.getEyePos().distanceTo(predict);

                isSlowdownActive = false;

            if (canStopSprinting()) mc.player.setSprinting(false);

            if (canAttack()) {
                if (useResolver.getValue() && mc.player.isGliding()) {
                    mc.player.setVelocity(0, 0, 0);

                    findResolverPoint();
                    if (resolverPoint != null) {
                        isResolving = true;
                        resolverTimer.reset();
                    }
                }
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));

                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);



                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));

                ticksToAttack = 10;
            }
        } else {
            speedAcceleration = 0;
            razvorotikTicks = 0;
        }
    }

    private boolean isValidEntity(Entity entity) {
        if (!entity.isAlive()) return false;
        PlayerEntity player = Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer != null ? Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer : mc.player;
        if (entity == Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer) return false;
        if (entity instanceof ClientPlayerEntity) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (entity instanceof PlayerEntity p && p.getArmor() != 0 && !targets.isEnabled("Игроки")) return false;
        if (entity instanceof PlayerEntity p && p.getArmor() == 0 && !targets.isEnabled("Голые")) return false;
        if ((entity instanceof HostileEntity || entity instanceof AmbientEntity) && !targets.isEnabled("Монстры"))
            return false;
        if ((entity instanceof PassiveEntity || entity instanceof FishEntity) && !targets.isEnabled("Животные"))
            return false;
        if (entity instanceof PlayerEntity p) {
            if (Onetap.getInstance().getModuleStorage().get(AntiBot.class).isBot(p)) return false;
            if (!FriendRepository.shouldAttack(p)) return false;
        }
        if (player.getEyePos().distanceTo(BestPoint.getNearestPoint(entity)) > (player.isGliding() ? 50 : distance.getValue() + preRotation.getValue()))
            return false;
        return true;
    }

    public boolean canAttack() {
        if (target == null) return false;

        isTurnaroundActive = false;

        PlayerEntity player = Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer != null ?
                Onetap.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer : mc.player;

        if (target.isGliding()) {
            Vec3d predict = PredictUtils.getPredicted(target, predictValue.getValue());
            double distToPredict = player.getEyePos().distanceTo(predict);

            preddict = hitAfterOvertake.getValue() ? 2.7f : 4f;

            if (distToPredict <= preddict && elytraTurnaround.getValue()) {
                isTurnaroundActive = true;
            }
        }

        if (!Onetap.getInstance().getIdealHitUtils().cooldownIsReached(false)) return false;
        if (ticksToAttack > 0) return false;

        if (target.isGliding()) {
            double distToPredict = player.getEyePos().distanceTo(PredictUtils.getPredicted(target, predictValue.getValue()));
            if (distToPredict > preddict) return false;

            if (isTurnaroundActive) {
                float targetYaw = new Rotation(RotationUtil.calculate(target.getBoundingBox().getCenter())).getYaw();
                float yawDiff = Math.abs(MathHelper.wrapDegrees(lastYaw - targetYaw));

                if (yawDiff > 5f) {
                    return false;
                }
            }
        } else {
            if (!RaytraceUtil.rayTrace(player.getRotationVector(), distance.getValue(), target.getBoundingBox()) && raycastCheck.getValue())
                return false;

            if (player.getEyePos().distanceTo(BestPoint.getNearestPoint(target)) > (distance.getValue() - 0.2f))
                return false;
        }

        if (!hitThroughWalls.getValue()) {
            Vec3d eyes = mc.player.getEyePos();
            Vec3d targetCenter = target.getBoundingBox().getCenter();
            var raycast = mc.world.raycast(new RaycastContext(eyes, targetCenter, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (raycast.getType() != HitResult.Type.MISS) {
                return false;
            }
        }

        return Onetap.getInstance().getIdealHitUtils().canCritical();
    }

    public boolean canStopSprinting() {
        if (target == null) return false;
        if (!Onetap.getInstance().getIdealHitUtils().cooldownIsReached(true)) return false;
        if (ticksToAttack > 1) return false;
        if (SimulatedPlayer.simulateLocalPlayer(1).fallDistance == 0) return false;
        return true;
    }

    private void updateTarget() {
        LivingEntity best = null;
        double bestFovDot = -1;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F);

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity) {
                if (!isValidEntity(entity)) continue;

                Vec3d targetVec = BestPoint.getNearestPoint(entity).subtract(eyePos).normalize();
                double dot = lookVec.dotProduct(targetVec);

                if (dot > bestFovDot) {
                    bestFovDot = dot;
                    best = (LivingEntity) entity;
                }
            }
        }

        if (target == null || !isValidEntity(target)) {
            this.target = best;
        }
    }

    private void updateVanillaRotation(LivingEntity target) {
        if (target == null) return;

        Vec3d targetPoint = resolveMultipoint(target, BestPoint.getNearestPoint(target), distance.getValue());
        var rotation = new Rotation(RotationUtil.calculate(targetPoint));

        RotationComponent.update(rotation, 360, 360, 360, 360, 0, 1, clientLook.getValue());
    }

    private void updateSlothRotation(LivingEntity target) {
        if (target == null) return;

        Vec3d targetPoint = resolveMultipoint(target, BestPoint.getPoint2(target), 6);
        if (target.isGliding() && predictate.getValue() && !isTurnaroundActive) {
            targetPoint = PredictUtils.getPredicted(target, predictValue.getValue());
        }

        var angle = new Rotation(RotationUtil.calculate(targetPoint));
        float targetYaw = angle.getYaw();
        float targetPitch = angle.getPitch();

        var box = target.getBoundingBox();
        boolean isLooking = mc.player.isGliding() ?
                RaytraceUtil.rayTrace(mc.player.getRotationVector(), 6, box.expand(-0.10, -0.5, -0.10)) :
                RaytraceUtil.rayTrace(mc.player.getRotationVector(), 6, box.expand(-0.10, -1, -0.10));

        float randomFactor = (float) Math.random();
        float deltaYawAbs = Math.abs(MathHelper.wrapDegrees(targetYaw - lastYaw));

        float accelBase = 0.004f;
        float decelBase = 0.02f;
        float jitterMult = 0.7f;
        float customFov = 25.0f;

        if (mc.player.isGliding()) accelBase += 0.0005f;

        if (!isLooking && speedAcceleration <= 0.16f) {
            if (deltaYawAbs > 70) {
                speedAcceleration += accelBase - (randomFactor * 0.0025f);
            } else if (deltaYawAbs > 40) {
                speedAcceleration += accelBase - (randomFactor * 0.0015f);
            } else {
                speedAcceleration += accelBase + (randomFactor * 0.0015f);
            }
        } else {
            if (speedAcceleration >= -0.01f) {
                if (mc.player.isGliding()) {
                    speedAcceleration -= 0.01f;
                } else {
                    speedAcceleration -= (deltaYawAbs > 50) ? decelBase * 1.5f : decelBase;
                    if(mc.player.distanceTo(target)<=1.5f){
                        speedAcceleration -= 0.08f;
                    }
                }
            }
        }

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float deltaPitch = targetPitch - lastPitch;
        float smooth = Math.max(speedAcceleration, 0);

        float newYaw = lastYaw + deltaYaw * Math.min(Math.max(smooth, 0), 1);
        float newPitch = lastPitch + deltaPitch * Math.min(Math.max(smooth / 2, 0), 1);

        if (deltaYawAbs > customFov) {
            float time = (mc.player.age + mc.getRenderTickCounter().getTickDelta(true)) * 0.15f;
            float ampBase = (1f - MathHelper.clamp(smooth, 0f, 1f)) * jitterMult;

            float jitterYaw = (float) (Math.sin(time * 1.7f) + (randomFactor * 0.2f)) * (0.35f * ampBase);
            float jitterPitch = (float) (Math.cos(time * 1.9f) + (randomFactor * 0.2f)) * (0.50f * ampBase);

            newYaw += mc.player.isGliding() ? jitterYaw / 4f : jitterYaw / 1f;
            newPitch += mc.player.isGliding() ? jitterPitch / 4f : jitterPitch / 1f;
        }

        float gcdValue = GCDFixer.getGCDValue();
        newYaw -= (newYaw - lastYaw) % gcdValue;
        newPitch -= (newPitch - lastPitch) % gcdValue;

        var smoothRot = new Rotation(newYaw, newPitch);

        var deltaYaw2 = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw() - lastYaw);
        var deltaPitch2 = mc.gameRenderer.getCamera().getPitch() - lastPitch;

        if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            deltaYaw2 = MathHelper.wrapDegrees((mc.gameRenderer.getCamera().getYaw() - 180) - lastYaw);
            deltaPitch2 = -mc.gameRenderer.getCamera().getPitch() - lastPitch;
        }

        RotationComponent.update(smoothRot, 360, 360, Math.abs(deltaYaw2) > 3 || Math.abs(deltaPitch2) > 3 ? 0 : 360, Math.abs(deltaYaw2) > 3 || Math.abs(deltaPitch2) > 3 ? 0 : 360, 0, 1, clientLook.getValue());

        lastYaw = smoothRot.getYaw();
        lastPitch = smoothRot.getPitch();
    }

    private void updateLonyJirRotation(LivingEntity target) {
        double time = System.nanoTime() * 1e-9;
        var angle = new Rotation(RotationUtil.calculate(target.getBoundingBox().getCenter().add(0, (float) Math.abs(Math.sin(time * 19)) / 2, 0)));
        var predict = PredictUtils.getPredicted(target, predictValue.getValue() + 2.5f);

        if (target.isGliding() && predictate.getValue() && !isTurnaroundActive) angle = new Rotation(predict);

        if (!RaytraceUtil.rayTrace(mc.player.getRotationVector(), 999, target.getBoundingBox().expand(-0.2f))) {
            speedAcceleration += (float) Math.abs(Math.sin(time * 19)) / 666;
        } else {
            if (speedAcceleration >= 0.02f)
                speedAcceleration -= 0.02f;
        }

        var deltaYaw = MathHelper.wrapDegrees(angle.getYaw() - lastYaw);
        var deltaPitch = angle.getPitch() - lastPitch;

        var smooth = Math.min(Math.max(speedAcceleration, 0), 0.2f);

        var newYaw = lastYaw + deltaYaw * smooth;
        var newPitch = lastPitch + deltaPitch * (smooth / 3);

        newYaw -= (newYaw - lastYaw) % GCDFixer.getGCDValue();
        newPitch -= (newPitch - lastPitch) % GCDFixer.getGCDValue();

        var smoothRot = new Rotation(newYaw, newPitch);

        var deltaYaw2 = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw() - lastYaw);
        var deltaPitch2 = mc.gameRenderer.getCamera().getPitch() - lastPitch;

        if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            deltaYaw2 = MathHelper.wrapDegrees((mc.gameRenderer.getCamera().getYaw() - 180) - lastYaw);
            deltaPitch2 = -mc.gameRenderer.getCamera().getPitch() - lastPitch;
        }

        if (mc.player.isGliding() && target.isGliding())
            RotationComponent.update(smoothRot, 360, 360, 360, 360, 0, 1, clientLook.getValue());
        lastYaw = smoothRot.getYaw();
        lastPitch = smoothRot.getPitch();
    }

    private void slothTest(LivingEntity target) {
        if (target == null) return;

        Vec3d point = resolveMultipoint(target, BestPoint.getPoint2(target), 6);
        if (target.isGliding() && predictate.getValue() && !isTurnaroundActive) {
            point = PredictUtils.getPredicted(target, predictValue.getValue());
        }
        boolean isLooking = RaytraceUtil.rayTrace(mc.player.getRotationVector(), 6, target.getBoundingBox().expand(-0,-1,-0));
        var idealRotation = new Rotation(RotationUtil.calculate(point));
        float targetYaw = idealRotation.getYaw();
        float targetPitch = idealRotation.getPitch();
        float randomFactor = (float) Math.random();

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float deltaPitch = targetPitch - lastPitch;


        float distance = mc.player.distanceTo(target) / 30 ;
        if(!isLooking && mc.player.getAttackCooldownProgress(1) >= 0.7f){
        distance += 0.03f / 1.5f;

            stopWatch.reset();
        }
        if(!isLooking ){
            distance += 0.0075f / 1.5f;

            stopWatch.reset();
        }
        else{
        distance *= 0.15f + (randomFactor * 0.2f);

        }
        var smooth = Math.min(Math.max(distance, 0), 0.12f);

        float newYaw = lastYaw + (deltaYaw) * smooth;
        float newPitch = lastPitch + (deltaPitch * 0.5f) * smooth;



        float gcd = GCDFixer.getGCDValue();
        newYaw -= (newYaw - lastYaw) % gcd;
        newPitch -= (newPitch - lastPitch) % gcd;

        newPitch = MathHelper.clamp(newPitch, -90f, 90f);

        var legitRot = new Rotation(newYaw, newPitch);

        RotationComponent.update(legitRot, 360, 360, 360, 360, 0, 1, clientLook.getValue());

        lastYaw = legitRot.getYaw();
        lastPitch = legitRot.getPitch();
    }

    private void updateLonyGriefRotation(LivingEntity target) {
        Vec3d point = target.isGliding() && predictate.getValue() && !isTurnaroundActive ? PredictUtils.getPredicted(target, predictValue.getValue()) : resolveMultipoint(target, BestPoint.getPoint(target), 6);

        var angle = new Rotation(RotationUtil.calculate(point));
        float targetYaw = angle.getYaw();
        float targetPitch = angle.getPitch();

        if (!back) {
            float pon = mc.player.isGliding() ? 1.35f : 1f;
            speedAcceleration += (Math.abs(MathHelper.wrapDegrees(targetYaw - lastYaw)) > 40 ? 0.005f / pon : 0.0038f / pon);

            boolean isLooking = RaytraceUtil.rayTrace(mc.player.getRotationVector(), 6, target.getBoundingBox().expand(-0.2, -0.3, -0.2));
            if (speedAcceleration >= 0.16f / pon || isLooking) {
                back = true;
            }
        } else {
            if (speedAcceleration >= -0.01f) {
                speedAcceleration -= (Math.abs(MathHelper.wrapDegrees(targetYaw - lastYaw)) > 60 ? 0.06f : 0.01f);
            }
            if (speedAcceleration <= -0.01f) {
                back = false;
            }
        }

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float deltaPitch = targetPitch - lastPitch;
        float smooth = Math.max(speedAcceleration, 0);

        float newYaw = lastYaw + deltaYaw * Math.min(Math.max(smooth, 0), 1);
        float newPitch = lastPitch + deltaPitch * Math.min(Math.max(smooth / 2, 0), 1);

        float gcdValue = GCDFixer.getGCDValue();
        newYaw -= (newYaw - lastYaw) % gcdValue;
        newPitch -= (newPitch - lastPitch) % gcdValue;

        var smoothRot = new Rotation(newYaw, newPitch);
        RotationComponent.update(smoothRot, 360, 360, 360, 360, 0, 1, clientLook.getValue());

        lastYaw = smoothRot.getYaw();
        lastPitch = smoothRot.getPitch();
    }

    private void updateWellmineRotation(LivingEntity target) {
        var box = target.getBoundingBox();
        Vec3d vector = resolveMultipoint(target, BestPoint.getMultipoint(target, 6), 6);

        if (target.isGliding() && predictate.getValue() && !isTurnaroundActive) {
            vector = PredictUtils.getPredicted(target, predictValue.getValue());
        }

        var angle = RotationUtil.calculate(vector);

        float targetYaw = angle.x;
        float targetPitch = angle.y;

        if (!back) {
            if (speedAcceleration >= 1f) {
                speedAcceleration = 0 ;
            } else {
                if(mc.player.isGliding()){
                    float diff = Math.abs(MathHelper.wrapDegrees(angle.x - mc.player.getYaw()));
                    speedAcceleration += (diff > 40 ? 0.0025f : 0.005f);
                }
                else{
                    speedAcceleration += 0.005f ;

                }
            }

            Vec3d offset = Vec3d.ZERO;
            if (mc.player.isGliding() && target instanceof PlayerEntity && target.isGliding()) {
                offset = PredictUtils.getPredicted(target, predictValue.getValue());
            }

            if (speedAcceleration >= 0.18 || RaytraceUtil.rayTrace(mc.player.getRotationVector(), 6, box.offset(offset).expand(-0.5, -1, -0.5))) {
                back = true;
            }
        } else {
            if (speedAcceleration >= -0.01f) {
                float diff = Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()));
                speedAcceleration -= (diff > 40 ? 0.04f : 0.01f);
            }
            if (speedAcceleration <= -0.01f) back = false;
        }

        float randomYaw = (float) java.util.concurrent.ThreadLocalRandom.current().nextDouble(-RANDOM_STRENGTH, RANDOM_STRENGTH);
        float randomPitch = (float) java.util.concurrent.ThreadLocalRandom.current().nextDouble(-RANDOM_STRENGTH, RANDOM_STRENGTH);

        targetYaw += randomYaw;
        targetPitch += randomPitch;

        float smoothVal = Math.min(Math.max(speedAcceleration, -1), 1);

        float changeYaw = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()) * smoothVal;
        float changePitch = (targetPitch - mc.player.getPitch()) * (smoothVal / 2f);

        var smoothRot = new Rotation(
                mc.player.getYaw() + changeYaw,
                MathHelper.clamp(mc.player.getPitch() + changePitch, -90, 90)
        );

        RotationComponent.update(smoothRot, 360, 360, 360, 360, 0, 1, clientLook.getValue());

        lastYaw = smoothRot.getYaw();
        lastPitch = smoothRot.getPitch();
    }

    private void updateHolyWorldRotation(LivingEntity target) {
        holyWorldMode.setTarget(target);
        holyWorldMode.tick();
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
    }

    private void updateNeuroRotation(LivingEntity target) {
        double distance = this.distance.getValue();
        Vec3d targetPoint = BestPoint.getMultipoint(target, distance);
        Rotation targetRotation = new Rotation(RotationUtil.calculate(targetPoint));

        float currentYaw = MathHelper.wrapDegrees(mc.player.getYaw());
        float currentPitch = mc.player.getPitch();

        float targetDeltaYaw = MathHelper.wrapDegrees(targetRotation.getYaw() - currentYaw);
        float targetDeltaPitch = targetRotation.getPitch() - currentPitch;

        float[] input = new float[]{previousDeltaYaw, previousDeltaPitch, targetDeltaYaw, targetDeltaPitch};
        float[] output = AIRotationManager.predict(input);

        float appliedYaw = MathHelper.wrapDegrees(currentYaw + output[0]);
        float appliedPitch = MathHelper.clamp(currentPitch + output[1], -90, 90);

        Rotation rotation = new Rotation(appliedYaw, appliedPitch);
        RotationComponent.update(rotation, 180, 180, 5, 1000);

        previousDeltaYaw = output[0];
        previousDeltaPitch = output[1];
    }

    private Vec3d resolveMultipoint(LivingEntity target, Vec3d point, double range) {
        if (!smartAim.getValue() || target == null) {
            return point;
        }

        return BestPoint.getNearestVisiblePoint(target, point, range);
    }

    private float applyGCD(float deltaRotation) {
        float sensitivity = (float) (mc.options.getMouseSensitivity().getValue() * 0.6f + 0.2f);
        float multiplier = sensitivity * sensitivity * sensitivity * 8.0f * 0.15f;
        return (Math.round(deltaRotation / multiplier) * multiplier);
    }


    private void renderPredictPoint(MatrixStack matrices, Camera camera, float tickDelta) {
        if (target == null || !target.isGliding()) return;

        Vec3d predictPos = PredictUtils.getPredictedRender(target, predictValue.getValue(), tickDelta);
        Vec3d camPos = camera.getPos();

        double renderX = predictPos.x - camPos.x;
        double renderY = predictPos.y - camPos.y;
        double renderZ = predictPos.z - camPos.z;

        float size = 0.35f;
        int color = ColorProvider.getThemeColor();

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 1;

        buffer.vertex(matrix, -size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, -size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, -size, size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, -size, size).color(r, g, b, a);

        buffer.vertex(matrix, -size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, -size, -size).color(r, g, b, a);

        buffer.vertex(matrix, -size, size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, size).color(r, g, b, a);

        buffer.vertex(matrix, size, size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, size).color(r, g, b, a);

        buffer.vertex(matrix, -size, size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, -size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, size).color(r, g, b, a);

        buffer.vertex(matrix, -size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, size).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    @Override
    public void onEnable() {
        target = null;
        razvorotikTicks = 0;
        Onetap.getInstance().getModuleStorage().setSpeedAcceleration(0);

        if (!renderListenerRegistered) {
            WorldRenderEvents.LAST.register(renderListener);
            renderListenerRegistered = true;
        }

        super.onEnable();
    }

    @Override
    public void onDisable() {
        target = null;
        ticksToAttack = 0;
        speedAcceleration = 0;
        razvorotikTicks = 0;
        isResolving = false;
        resolverPoint = null;
        holyWorldMode.reset();
        Onetap.getInstance().getModuleStorage().setSpeedAcceleration(0);
        Onetap.getInstance().getModuleStorage().setRandomness(1);
        super.onDisable();
    }
}
