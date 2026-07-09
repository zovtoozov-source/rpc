package tech.onetap.util.neuro.rotation;

import java.util.Random;

public class PerlinNoise {
    private final int[] perm = new int[512];

    public PerlinNoise() {
        this(new Random());
    }

    public PerlinNoise(long seed) {
        this(new Random(seed));
    }

    public PerlinNoise(Random rand) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        System.arraycopy(p, 0, perm, 0, 256);
        System.arraycopy(p, 0, perm, 256, 256);
    }

    public double noise(double x) {
        return noise(x, 0, 0);
    }

    public double noise(double x, double y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = fade(xf);
        double v = fade(yf);
        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];
        double x1 = lerp(grad(aa, xf, yf, 0), grad(ba, xf - 1, yf, 0), u);
        double x2 = lerp(grad(ab, xf, yf - 1, 0), grad(bb, xf - 1, yf - 1, 0), u);
        return lerp(x1, x2, v);
    }

    public double noise(double x, double y, double z) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        int zi = (int) Math.floor(z) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double zf = z - Math.floor(z);
        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);
        int aaa = perm[perm[perm[xi] + yi] + zi];
        int aba = perm[perm[perm[xi] + yi + 1] + zi];
        int aab = perm[perm[perm[xi] + yi] + zi + 1];
        int abb = perm[perm[perm[xi] + yi + 1] + zi + 1];
        int baa = perm[perm[perm[xi + 1] + yi] + zi];
        int bba = perm[perm[perm[xi + 1] + yi + 1] + zi];
        int bab = perm[perm[perm[xi + 1] + yi] + zi + 1];
        int bbb = perm[perm[perm[xi + 1] + yi + 1] + zi + 1];
        double x1 = lerp(grad(aaa, xf, yf, zf), grad(baa, xf - 1, yf, zf), u);
        double x2 = lerp(grad(aba, xf, yf - 1, zf), grad(bba, xf - 1, yf - 1, zf), u);
        double y1 = lerp(x1, x2, v);
        double x3 = lerp(grad(aab, xf, yf, zf - 1), grad(bab, xf - 1, yf, zf - 1), u);
        double x4 = lerp(grad(abb, xf, yf - 1, zf - 1), grad(bbb, xf - 1, yf - 1, zf - 1), u);
        double y2 = lerp(x3, x4, v);
        return lerp(y1, y2, w);
    }

    private double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private double lerp(double a, double b, double x) { return a + x * (b - a); }
    private double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
