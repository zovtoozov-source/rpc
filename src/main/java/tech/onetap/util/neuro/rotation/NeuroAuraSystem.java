package tech.onetap.util.neuro.rotation;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.rotation.Rotation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static tech.onetap.util.IMinecraft.mc;

/**
 * Human-aim аура: детерминированный replay записанной траектории и записанных точек
 * прицеливания внутри хитбокса цели.
 */
public class NeuroAuraSystem {
    private static final String PATTERNS_DIRECTORY = "neuro_patterns";
    private static final String PATTERN_EXTENSION = ".neuro";

    private static final long MIN_RECORD_INTERVAL_MS = 20L;
    private static final int MAX_SAMPLES = 20000;
    private static final int MIN_SAMPLES_FOR_MODEL = 40;

    private static final float MAX_YAW_STEP = 24.0f;
    private static final float MAX_PITCH_STEP = 16.0f;

    private static final float FALLBACK_GAIN = 0.16f;
    private static final float FALLBACK_MAX_YAW = 9.0f;
    private static final float FALLBACK_MAX_PITCH = 5.0f;

    @Getter
    private final List<NeuroPattern> recordedPatterns = new CopyOnWriteArrayList<>();
    private final HumanAimModel model = new HumanAimModel();

    @Getter
    @Setter
    private boolean isRecording = false;
    @Getter
    @Setter
    private boolean isUsingNeuro = false;
    @Getter
    @Setter
    private String currentPatternName = null;
    @Getter
    private String lastDebugMessage = "§7Готов";
    @Getter
    private int recordedThisSession = 0;

    private long lastRecordTime = 0L;

    // Состояние записи для вычисления реальных дельт движения.
    private boolean hasPrevSample = false;
    private float prevYaw;
    private float prevPitch;
    private float prevYawError;
    private float prevPitchError;
    private float prevAimX;
    private float prevAimY;
    private float prevAimZ;
    private double prevDistance;
    private double prevTargetSpeed;
    private String prevTargetType = "player";

    public NeuroAuraSystem() {
        createPatternsDirectory();
    }

    private void createPatternsDirectory() {
        try {
            Path path = Paths.get(PATTERNS_DIRECTORY);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ────────────────────────────── запись ──────────────────────────────

    public void recordTick(LivingEntity target, float currentYaw, float currentPitch) {
        if (!isRecording || target == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastRecordTime < MIN_RECORD_INTERVAL_MS) return;
        lastRecordTime = now;

        AimOffset aimOffset = computeAimOffset(target, currentYaw, currentPitch);
        Vec3d recordedAimPoint = pointFromOffset(target, aimOffset.x(), aimOffset.y(), aimOffset.z());
        Rotation perfect = getRotationTo(recordedAimPoint);
        float yawError = MathHelper.wrapDegrees(currentYaw - perfect.getYaw());
        float pitchError = currentPitch - perfect.getPitch();
        double distance = mc.player.getEyePos().distanceTo(target.getEyePos());
        double targetSpeed = target.getVelocity().horizontalLength();
        String targetType = target instanceof PlayerEntity ? "player" : "mob";

        if (hasPrevSample) {
            float deltaYaw = MathHelper.wrapDegrees(currentYaw - prevYaw);
            float deltaPitch = currentPitch - prevPitch;

            recordedPatterns.add(new NeuroPattern(
                    prevYawError, prevPitchError,
                    deltaYaw, deltaPitch,
                    prevAimX, prevAimY, prevAimZ,
                    prevDistance, prevTargetSpeed, prevTargetType));
            recordedThisSession++;

            while (recordedPatterns.size() > MAX_SAMPLES) {
                recordedPatterns.remove(0);
            }
            if (recordedThisSession % 25 == 0) {
                lastDebugMessage = "§aЗаписано: §f" + recordedPatterns.size() + " сэмплов";
            }
        }

        prevYaw = currentYaw;
        prevPitch = currentPitch;
        prevYawError = yawError;
        prevPitchError = pitchError;
        prevAimX = aimOffset.x();
        prevAimY = aimOffset.y();
        prevAimZ = aimOffset.z();
        prevDistance = distance;
        prevTargetSpeed = targetSpeed;
        prevTargetType = targetType;
        hasPrevSample = true;
    }

    public void recordAttack(LivingEntity target, float currentYaw, float currentPitch) {
        recordTick(target, currentYaw, currentPitch);
    }

    // ──────────────────────────── воспроизведение ────────────────────────────

    public Rotation getNeuroRotation(LivingEntity target) {
        if (!isUsingNeuro) {
            lastDebugMessage = "§cNeuro выкл";
            return null;
        }
        if (target == null || mc.player == null) return null;

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();
        double distance = mc.player.getEyePos().distanceTo(target.getEyePos());
        String targetType = target instanceof PlayerEntity ? "player" : "mob";
        float relY = (float) MathHelper.clamp((target.getY() - mc.player.getY()) / 2.0, -1.0, 1.0);

        HumanAimModel.AimStep step;
        String source;
        if (model.isBuilt()) {
            // 1. Получаем целевую точку прицела от модели (mean сегмента + контекст)
            float[] targetAim = model.getTargetAim(distance, relY);
            // 2. Считаем ошибку ОТНОСИТЕЛЬНО этой точки
            Vec3d aimPoint = pointFromOffset(target, targetAim[0], targetAim[1], targetAim[2]);
            Rotation perfect = getRotationTo(aimPoint);
            float yawError = MathHelper.wrapDegrees(curYaw - perfect.getYaw());
            float pitchError = curPitch - perfect.getPitch();
            // 3. Получаем движение на основе корректной ошибки
            step = model.nextMove(yawError, pitchError, distance, targetType, relY);
            source = "model";
        } else {
            Rotation perfect = getRotationTo(target.getEyePos());
            float yawError = MathHelper.wrapDegrees(curYaw - perfect.getYaw());
            float pitchError = curPitch - perfect.getPitch();
            step = new HumanAimModel.AimStep(
                    MathHelper.clamp(-yawError * FALLBACK_GAIN, -FALLBACK_MAX_YAW, FALLBACK_MAX_YAW),
                    MathHelper.clamp(-pitchError * FALLBACK_GAIN, -FALLBACK_MAX_PITCH, FALLBACK_MAX_PITCH),
                    0.0f, 0.0f, 0.0f);
            source = "fallback";
        }

        float newYaw = MathHelper.wrapDegrees(curYaw + MathHelper.clamp(step.deltaYaw(), -MAX_YAW_STEP, MAX_YAW_STEP));
        float newPitch = MathHelper.clamp(curPitch + MathHelper.clamp(step.deltaPitch(), -MAX_PITCH_STEP, MAX_PITCH_STEP), -90.0f, 90.0f);

        lastDebugMessage = String.format("§a[Human] err → move %.2f/%.2f aim %.2f/%.2f/%.2f (%s)",
                step.deltaYaw(), step.deltaPitch(), step.aimX(), step.aimY(), step.aimZ(), source);
        return new Rotation(newYaw, newPitch);
    }

    private AimOffset computeAimOffset(LivingEntity target, float yaw, float pitch) {
        Box box = target.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = directionFromRotation(yaw, pitch);
        Vec3d hit = rayBoxIntersection(eye, dir, box);
        if (hit == null) {
            Vec3d center = box.getCenter();
            double t = center.subtract(eye).dotProduct(dir);
            if (Double.isNaN(t) || Double.isInfinite(t)) {
                t = eye.distanceTo(center);
            }
            t = Math.max(0.0, t);
            Vec3d pointOnRay = eye.add(dir.multiply(t));
            hit = new Vec3d(
                    MathHelper.clamp(pointOnRay.x, box.minX, box.maxX),
                    MathHelper.clamp(pointOnRay.y, box.minY, box.maxY),
                    MathHelper.clamp(pointOnRay.z, box.minZ, box.maxZ));
        }
        return offsetFromPoint(box, hit);
    }

    private Vec3d rayBoxIntersection(Vec3d origin, Vec3d dir, Box box) {
        double tMin = 0.0;
        double tMax = Double.MAX_VALUE;

        double[] origins = {origin.x, origin.y, origin.z};
        double[] dirs = {dir.x, dir.y, dir.z};
        double[] mins = {box.minX, box.minY, box.minZ};
        double[] maxs = {box.maxX, box.maxY, box.maxZ};

        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dirs[axis]) < 1.0E-8) {
                if (origins[axis] < mins[axis] || origins[axis] > maxs[axis]) {
                    return null;
                }
                continue;
            }

            double inv = 1.0 / dirs[axis];
            double t1 = (mins[axis] - origins[axis]) * inv;
            double t2 = (maxs[axis] - origins[axis]) * inv;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return null;
            }
        }

        return origin.add(dir.multiply(tMin));
    }

    private Vec3d directionFromRotation(float yaw, float pitch) {
        float yawRad = -yaw * ((float) Math.PI / 180.0f) - (float) Math.PI;
        float pitchRad = -pitch * ((float) Math.PI / 180.0f);
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        return new Vec3d(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch).normalize();
    }

    private AimOffset offsetFromPoint(Box box, Vec3d point) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double hx = Math.max((box.maxX - box.minX) * 0.5, 1.0E-6);
        double hy = Math.max((box.maxY - box.minY) * 0.5, 1.0E-6);
        double hz = Math.max((box.maxZ - box.minZ) * 0.5, 1.0E-6);
        return new AimOffset(
                (float) MathHelper.clamp((point.x - cx) / hx, -1.0, 1.0),
                (float) MathHelper.clamp((point.y - cy) / hy, -1.0, 1.0),
                (float) MathHelper.clamp((point.z - cz) / hz, -1.0, 1.0));
    }

    private Vec3d pointFromOffset(LivingEntity target, float offX, float offY, float offZ) {
        Box box = target.getBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double hx = (box.maxX - box.minX) * 0.5;
        double hy = (box.maxY - box.minY) * 0.5;
        double hz = (box.maxZ - box.minZ) * 0.5;
        return new Vec3d(
                cx + MathHelper.clamp(offX, -1.0f, 1.0f) * hx,
                cy + MathHelper.clamp(offY, -1.0f, 1.0f) * hy,
                cz + MathHelper.clamp(offZ, -1.0f, 1.0f) * hz);
    }

    private Rotation getRotationTo(Vec3d point) {
        return new Rotation(RotationUtil.calculate(point));
    }

    private void rebuildModel() {
        if (recordedPatterns.size() >= MIN_SAMPLES_FOR_MODEL) {
            model.build(new ArrayList<>(recordedPatterns));
        }
    }

    // ──────────────────────────── сохранение / загрузка ────────────────────────────

    public void savePatterns(String profileName) {
        if (recordedPatterns.isEmpty()) {
            lastDebugMessage = "§cНет сэмплов";
            return;
        }
        try {
            createPatternsDirectory();
            String filename = PATTERNS_DIRECTORY + "/" + profileName + PATTERN_EXTENSION;
            try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(filename))) {
                output.writeObject(new ArrayList<>(recordedPatterns));
            }
            currentPatternName = profileName;
            lastDebugMessage = "§aСохранено " + recordedPatterns.size() + " сэмплов";
        } catch (IOException e) {
            lastDebugMessage = "§cОшибка сохранения";
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public void loadPatterns(String profileName) {
        String filename = PATTERNS_DIRECTORY + "/" + profileName + PATTERN_EXTENSION;
        File file = new File(filename);
        if (!file.exists()) {
            lastDebugMessage = "§eНет паттерна: " + profileName;
            return;
        }
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(filename))) {
            List<NeuroPattern> loaded = (List<NeuroPattern>) input.readObject();
            recordedPatterns.clear();
            recordedPatterns.addAll(loaded);
            currentPatternName = profileName;
            rebuildModel();
            model.reset();
            lastDebugMessage = "§aЗагружено " + recordedPatterns.size() + " сэмплов"
                    + (model.isBuilt() ? " §7(модель готова)" : " §c(мало данных)");
        } catch (IOException | ClassNotFoundException e) {
            lastDebugMessage = "§cОшибка загрузки (перезапиши профиль)";
            e.printStackTrace();
        }
    }

    // ──────────────────────────── управление ────────────────────────────

    public int getPatternCount() {
        return recordedPatterns.size();
    }

    public void startRecording() {
        isRecording = true;
        isUsingNeuro = false;
        recordedThisSession = 0;
        lastRecordTime = 0L;
        hasPrevSample = false;
        lastDebugMessage = "§aЗапись начата — целься сам как обычно";
    }

    public void stopRecording() {
        isRecording = false;
        hasPrevSample = false;
        rebuildModel();
        lastDebugMessage = "§eЗапись остановлена. Сэмплов: " + recordedPatterns.size()
                + (model.isBuilt() ? " §7(модель готова)" : " §c(мало данных)");
    }

    public void clearPatterns() {
        recordedPatterns.clear();
        recordedThisSession = 0;
        currentPatternName = null;
        hasPrevSample = false;
        model.build(new ArrayList<>());
        lastDebugMessage = "§eСэмплы очищены";
    }

    public String getStatusString() {
        String status = "§8[§bNeuro§8] §fСэмплов: §e" + recordedPatterns.size();
        if (isRecording) {
            status += " §a[ЗАПИСЬ";
            if (recordedThisSession > 0) status += " +" + recordedThisSession;
            status += "]";
        }
        if (isUsingNeuro) {
            status += " §b[АКТИВЕН";
            if (currentPatternName != null) status += " §7(" + currentPatternName + ")";
            status += "]";
        }
        if (model.isBuilt()) {
            status += " §d[модель " + model.getSampleCount() + "]";
        }
        return status;
    }

    public List<String> getPatternNames() {
        List<String> patterns = new ArrayList<>();
        File dir = new File(PATTERNS_DIRECTORY);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(PATTERN_EXTENSION));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    patterns.add(name.substring(0, name.length() - PATTERN_EXTENSION.length()));
                }
            }
        }
        return patterns;
    }

    // ──────────────────────── совместимость со старым API ────────────────────────

    public List<String> getModelNames() {
        return getPatternNames();
    }

    public void loadProfile(String name) {
        loadPatterns(name);
    }

    public void trainModel(String name) {
        rebuildModel();
        savePatterns(name);
    }

    public boolean hasModel() {
        return !recordedPatterns.isEmpty();
    }

    public boolean isActive() {
        return isUsingNeuro;
    }

    public void setActive(boolean active) {
        setUsingNeuro(active);
        if (active) {
            rebuildModel();
            model.reset();
        }
    }

    public String getCurrentProfile() {
        return currentPatternName;
    }

    public void setCurrentProfile(String currentProfile) {
        this.currentPatternName = currentProfile;
    }

    public String getLastTickDebug() {
        return lastDebugMessage;
    }

    private record AimOffset(float x, float y, float z) {
    }
}
