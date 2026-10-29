package com.nscb.barkapp;

/** Java port of nscb/bark.py `features()`. Must stay numerically identical. */
public final class BarkFeatures {
    public static final int SR = 16000, N_FFT = 512, HOP = 160, N_MELS = 40, N_MFCC = 13;
    public static final int DIM = 55;
    private static final int NB = N_FFT / 2 + 1;
    private static final double[][] FB = melFilters();
    private static final double[][] DCT = dct();
    private static final double[] WIN = hanning();

    private BarkFeatures() {}

    private static double hz2mel(double f) { return 2595 * Math.log10(1 + f / 700); }
    private static double mel2hz(double m) { return 700 * (Math.pow(10, m / 2595) - 1); }

    private static double[][] melFilters() {
        double lo = hz2mel(50), hi = hz2mel(SR / 2.0);
        int[] bins = new int[N_MELS + 2];
        for (int i = 0; i < N_MELS + 2; i++) {
            double m = lo + (hi - lo) * i / (N_MELS + 1);
            bins[i] = (int) Math.floor((N_FFT + 1) * mel2hz(m) / SR);
        }
        double[][] fb = new double[N_MELS][NB];
        for (int m = 1; m <= N_MELS; m++) {
            int l = bins[m - 1], c = bins[m], h = bins[m + 1];
            for (int k = l; k < c; k++) fb[m - 1][k] = (double) (k - l) / Math.max(c - l, 1);
            for (int k = c; k < h; k++) fb[m - 1][k] = (double) (h - k) / Math.max(h - c, 1);
        }
        return fb;
    }

    private static double[][] dct() {
        double[][] d = new double[N_MFCC][N_MELS];
        for (int k = 0; k < N_MFCC; k++)
            for (int m = 0; m < N_MELS; m++)
                d[k][m] = Math.cos(Math.PI / N_MELS * (m + 0.5) * k);
        return d;
    }

    private static double[] hanning() {
        double[] w = new double[N_FFT];
        for (int i = 0; i < N_FFT; i++) w[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N_FFT - 1));
        return w;
    }

    /** In-place radix-2 FFT. */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr; cr = nr;
                }
            }
        }
    }

    private static void meanStd(double[][] a, int[] cols, double[] out, int off) {
        int n = a.length, k = cols.length;
        for (int j = 0; j < k; j++) {
            double s = 0;
            for (double[] row : a) s += row[cols[j]];
            double mu = s / n, v = 0;
            for (double[] row : a) { double d = row[cols[j]] - mu; v += d * d; }
            out[off + j] = mu;
            out[off + k + j] = Math.sqrt(v / n);
        }
    }

    private static double mean(double[] a) { double s = 0; for (double v : a) s += v; return s / a.length; }
    private static double std(double[] a) {
        double mu = mean(a), v = 0;
        for (double x : a) v += (x - mu) * (x - mu);
        return Math.sqrt(v / a.length);
    }

    public static double[] compute(float[] input) {
        float[] x = input;
        if (x.length < N_FFT) { x = new float[N_FFT]; System.arraycopy(input, 0, x, 0, input.length); }
        int n = 1 + (x.length - N_FFT) / HOP;
        double[][] logmel = new double[n][N_MELS];
        double[][] mfcc = new double[n][N_MFCC];
        double[] centroid = new double[n], rms = new double[n], zcr = new double[n];
        double[] re = new double[N_FFT], im = new double[N_FFT];
        for (int t = 0; t < n; t++) {
            double sq = 0; int zc = 0; double prevSign = 0;
            for (int i = 0; i < N_FFT; i++) {
                double v = x[t * HOP + i] * WIN[i];
                re[i] = v; im[i] = 0; sq += v * v;
                double sg = Math.signum(v);
                if (i > 0 && sg != prevSign) zc++;
                prevSign = sg;
            }
            rms[t] = Math.sqrt(sq / N_FFT);
            zcr[t] = (double) zc / (N_FFT - 1);
            fft(re, im);
            double[] spec = new double[NB];
            double num = 0, den = 0;
            for (int k = 0; k < NB; k++) {
                spec[k] = re[k] * re[k] + im[k] * im[k];
                num += spec[k] * k * (double) SR / N_FFT;
                den += spec[k];
            }
            centroid[t] = num / (den + 1e-10);
            for (int m = 0; m < N_MELS; m++) {
                double s = 0;
                for (int k = 0; k < NB; k++) s += spec[k] * FB[m][k];
                logmel[t][m] = Math.log(s + 1e-10);
            }
            for (int k = 0; k < N_MFCC; k++) {
                double s = 0;
                for (int m = 0; m < N_MELS; m++) s += logmel[t][m] * DCT[k][m];
                mfcc[t][k] = s;
            }
        }
        double[] f = new double[DIM];
        int[] all = new int[N_MFCC];
        for (int i = 0; i < N_MFCC; i++) all[i] = i;
        meanStd(mfcc, all, f, 0);
        int[] every4 = new int[10];
        for (int i = 0; i < 10; i++) every4[i] = i * 4;
        meanStd(logmel, every4, f, 26);
        double rmsMax = 0;
        for (double r : rms) rmsMax = Math.max(rmsMax, r);
        f[46] = mean(centroid); f[47] = std(centroid);
        f[48] = mean(zcr); f[49] = std(zcr);
        f[50] = std(rms) / (mean(rms) + 1e-9);
        double secs = (double) x.length / SR;
        int loudCount = 0, onsets = 0; boolean prev = false;
        for (int t = 0; t < n; t++) {
            boolean loud = rms[t] > 0.1 * rmsMax;
            if (loud) loudCount++;
            if (t > 0 && loud != prev) onsets++;
            prev = loud;
        }
        f[51] = Math.log(secs + 1e-3);
        f[52] = (double) loudCount / n;
        f[53] = onsets / secs;
        f[54] = Math.log(rmsMax + 1e-6);
        return f;
    }
}
