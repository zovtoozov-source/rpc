package tech.onetap.util.neuro.rotation;

import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class HumanAimModel {

    public record AimStep(float deltaYaw, float deltaPitch, float aimX, float aimY, float aimZ) {
    }

    private static final long SEGMENT_GAP_MS = 120L;
    private static final int MIN_SEGMENT_LEN = 3;

    private static final float MIN_GAIN = 0.03f;
    private static final float MAX_GAIN = 0.65f;

    private static final float SEGMENT_BLEND = 0.5f;
    private static final float OUTPUT_SMOOTHING = 0.30f;
    private static final float AIM_SMOOTHING = 0.25f;

    private static final float YAW_ACCEL_LIMIT = 5.0f;
    private static final float PITCH_ACCEL_LIMIT = 2.5f;
    private static final float MAX_YAW_STEP = 15.0f;
    private static final float MAX_PITCH_STEP = 8.0f;

    private static final float MICRO_JITTER = 0.06f;
    private static final float AIM_JITTER = 0.02f;

    /** Контекстный сдвиг aimY (макс). */
    private static final float AIM_CONTEXT_OFFSET = 0.5f;

    private static final float RETARGET_FACTOR = 2.5f;
    private static final float RETARGET_ABS = 10.0f;

    private final List<Segment> segments = new ArrayList<>();
    private boolean built = false;

    private Segment active = null;
    private int index = 0;
    private float activeStartAbsErr = 0.0f;
    /// Выбранная точка прицела для текущего сегмента (учитывает mean сегмента + контекст)
    private float targetAimX = 0.0f, targetAimY = 0.0f, targetAimZ = 0.0f;
    /// Плавное движение: НЕ сбрасывается при смене сегмента
    private float smoothYaw = 0.0f, smoothPitch = 0.0f;
    private float smoothAimY = 0.0f; // только Y плавно переходит

    public boolean isBuilt() { return built; }
    public int getSampleCount() { return segments.size(); }

    public void reset() {
        active = null; index = 0; activeStartAbsErr = 0.0f;
        targetAimX = targetAimY = targetAimZ = 0.0f;
        smoothYaw = smoothPitch = 0.0f;
        smoothAimY = 0.0f;
    }

    // ──── build ────

    public void build(List<NeuroPattern> samples) {
        segments.clear();
        active = null; index = 0; activeStartAbsErr = 0.0f;
        built = false;
        if (samples == null || samples.size() < MIN_SEGMENT_LEN) return;

        List<NeuroPattern> cur = new ArrayList<>();
        long pt = samples.get(0).getTimestamp();
        for (NeuroPattern s : samples) {
            if (s.getTimestamp() - pt > SEGMENT_GAP_MS && !cur.isEmpty()) { flush(cur); cur = new ArrayList<>(); }
            cur.add(s); pt = s.getTimestamp();
        }
        flush(cur);
        built = !segments.isEmpty();
    }

    private void flush(List<NeuroPattern> run) {
        if (run.size() < MIN_SEGMENT_LEN) return;
        int n = run.size();
        Segment seg = new Segment(n);
        double sumAY = 0;
        for (int i = 0; i < n; i++) {
            NeuroPattern p = run.get(i);
            float ye = p.getYawError(), pe = p.getPitchError();
            seg.gainY[i] = clampGain(Math.abs(ye) < 0.15f ? 0.0f : -p.getDeltaYaw() / ye);
            seg.gainP[i] = clampGain(Math.abs(pe) < 0.15f ? 0.0f : -p.getDeltaPitch() / pe);
            seg.aimX[i] = clampOffset(p.getAimX());
            seg.aimY[i] = clampOffset(p.getAimY());
            seg.aimZ[i] = clampOffset(p.getAimZ());
            sumAY += p.getAimY();
        }
        NeuroPattern s = run.get(0);
        seg.startYawErr = s.getYawError();
        seg.startPitchErr = s.getPitchError();
        seg.distance = s.getDistance();
        seg.type = s.getTargetType();
        seg.meanAimY = (float) (sumAY / n);
        segments.add(seg);
    }

    // ──── inference ────

    /** Возвращает точку прицела для текущего сегмента (с контекстными поправками). */
    public float[] getTargetAim(double distance, float relY) {
        return new float[]{targetAimX, targetAimY, targetAimZ};
    }

    public AimStep nextMove(float yawError, float pitchError, double distance, String type, float relY) {
        float absErr = Math.abs(yawError) + Math.abs(pitchError);
        boolean needNew = active == null || index >= active.length
                || absErr > activeStartAbsErr * RETARGET_FACTOR + RETARGET_ABS;
        if (needNew) {
            selectSegment(yawError, pitchError, distance, type, relY);
            activeStartAbsErr = absErr;
        }
        if (active == null) return fallback(yawError, pitchError, relY);

        int i = Math.min(index, active.length - 1);

        targetAimX = active.aimX[i];
        targetAimZ = active.aimZ[i];
        float ctxOff = contextOffset(distance, relY, active.meanAimY);
        targetAimY = clampOffset(active.meanAimY + ctxOff);
        float gsY = nonLinearGain(Math.abs(yawError));
        float gsP = nonLinearGain(Math.abs(pitchError));
        float tY = MathHelper.clamp(-yawError * active.gainY[i] * gsY, -MAX_YAW_STEP, MAX_YAW_STEP);
        float tP = MathHelper.clamp(-pitchError * active.gainP[i] * gsP, -MAX_PITCH_STEP, MAX_PITCH_STEP);
        if (index == 0) { tY *= SEGMENT_BLEND; tP *= SEGMENT_BLEND; }

        float pY = smoothYaw, pP = smoothPitch;
        smoothYaw += (tY - smoothYaw) * OUTPUT_SMOOTHING;
        smoothPitch += (tP - smoothPitch) * OUTPUT_SMOOTHING;
        smoothYaw = clampDiff(smoothYaw, pY, YAW_ACCEL_LIMIT);
        smoothPitch = clampDiff(smoothPitch, pP, PITCH_ACCEL_LIMIT);

        smoothYaw += (float) (Math.random() - 0.5) * 2.0f * MICRO_JITTER;
        smoothPitch += (float) (Math.random() - 0.5) * 2.0f * MICRO_JITTER;

        // aimY плавно к targetAimY
        smoothAimY += (targetAimY - smoothAimY) * AIM_SMOOTHING;
        smoothAimY += (float) (Math.random() - 0.5) * 2.0f * AIM_JITTER;

        index++;
        return new AimStep(smoothYaw, smoothPitch, targetAimX, smoothAimY, targetAimZ);
    }

    private AimStep fallback(float yawError, float pitchError, float relY) {
        float fy = clampDiff(-yawError * 0.12f, 0.0f, YAW_ACCEL_LIMIT);
        float fp = clampDiff(-pitchError * 0.12f, 0.0f, PITCH_ACCEL_LIMIT);
        return new AimStep(fy, fp, 0.0f, aimYfromRelY(relY), 0.0f);
    }

    private void selectSegment(float yawError, float pitchError, double distance, String type, float relY) {
        Segment best = null;
        double bestScore = Double.MAX_VALUE;
        for (Segment seg : segments) {
            double s = sq((seg.startYawErr - yawError) / 12.0)
                     + sq((seg.startPitchErr - pitchError) / 8.0)
                     + sq((seg.distance - distance) / 2.5);
            if (!seg.type.equals(type)) s += 2.0;
            if (s < bestScore) { bestScore = s; best = seg; }
        }

        active = best;
        index = 0;

        if (best == null) {
            targetAimX = 0; targetAimY = aimYfromRelY(relY); targetAimZ = 0;
        }
    }

    /** Контекст: дистанция + перепад высот + штраф за голову на ближней. */
    private float contextOffset(double distance, float relY, float meanAimY) {
        float distF = (float) MathHelper.clamp(1.0 - distance / 12.0, 0.0, 1.0);
        float relOff = relY * 0.3f;
        float rnd = (float) (Math.random() - 0.5) * 0.5f * distF;
        float headPenalty = 0;
        if (meanAimY > 0.3f && distance < 12.0) {
            headPenalty = -(meanAimY - 0.3f) * distF * 3.5f;
        }
        return clampOffset((relOff + rnd + headPenalty) * AIM_CONTEXT_OFFSET);
    }

    private float aimYfromRelY(float relY) {
        if (relY < -0.3f) return 0.5f;
        if (relY > 0.3f) return -0.2f;
        return 0.0f;
    }

    // ──── utils ────

    private float nonLinearGain(float absErr) {
        if (absErr < 0.5f) return 0.3f;
        if (absErr < 2.0f) return 0.6f;
        if (absErr < 6.0f) return 0.85f;
        if (absErr < 12.0f) return 1.0f;
        return 1.15f;
    }

    private float clampDiff(float cur, float prev, float maxD) {
        if (cur > prev + maxD) return prev + maxD;
        if (cur < prev - maxD) return prev - maxD;
        return cur;
    }

    private float sq(double x) { return (float) (x * x); }

    private float clampGain(float g) {
        if (Float.isNaN(g) || Float.isInfinite(g)) return 0.0f;
        return MathHelper.clamp(g, MIN_GAIN, MAX_GAIN);
    }

    private float clampOffset(float o) {
        if (Float.isNaN(o) || Float.isInfinite(o)) return 0.0f;
        return MathHelper.clamp(o, -1.0f, 1.0f);
    }

    private static final class Segment {
        final int length;
        final float[] gainY, gainP, aimX, aimY, aimZ;
        float startYawErr, startPitchErr;
        double distance;
        String type = "player";
        float meanAimY;
        Segment(int l) {
            this.length = l;
            this.gainY = new float[l]; this.gainP = new float[l];
            this.aimX = new float[l]; this.aimY = new float[l]; this.aimZ = new float[l];
        }
    }
}
