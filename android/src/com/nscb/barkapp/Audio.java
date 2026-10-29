package com.nscb.barkapp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** WAV decoding, resampling and silence trimming. */
public final class Audio {
    private Audio() {}

    public static float[] readWav(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) bo.write(buf, 0, n);
        byte[] b = bo.toByteArray();
        if (b.length < 12 || !new String(b, 0, 4, "US-ASCII").equals("RIFF")
                || !new String(b, 8, 4, "US-ASCII").equals("WAVE"))
            throw new IOException("Not a WAV file");
        int pos = 12, channels = 0, rate = 0, bits = 0, dataOff = -1, dataLen = 0, fmt = 0;
        while (pos + 8 <= b.length) {
            String id = new String(b, pos, 4, "US-ASCII");
            int len = le32(b, pos + 4);
            if (id.equals("fmt ")) {
                fmt = le16(b, pos + 8); channels = le16(b, pos + 10);
                rate = le32(b, pos + 12); bits = le16(b, pos + 22);
            } else if (id.equals("data")) {
                dataOff = pos + 8; dataLen = Math.min(len, b.length - dataOff);
                break;
            }
            pos += 8 + len + (len & 1);
        }
        if (dataOff < 0 || fmt != 1 || bits != 16 || channels < 1)
            throw new IOException("Only 16-bit PCM WAV is supported");
        int frames = dataLen / (2 * channels);
        float[] x = new float[frames];
        for (int i = 0; i < frames; i++) {
            double s = 0;
            for (int c = 0; c < channels; c++) s += (short) le16(b, dataOff + 2 * (i * channels + c));
            x[i] = (float) (s / channels / 32768.0);
        }
        return resample(x, rate);
    }

    /** Linear resample to 16 kHz, same as np.interp in bark.load_wav. */
    public static float[] resample(float[] x, int rate) {
        if (rate == BarkFeatures.SR || x.length < 2) return x;
        int n = (int) ((long) x.length * BarkFeatures.SR / rate);
        float[] y = new float[n];
        for (int i = 0; i < n; i++) {
            double p = n == 1 ? 0 : (double) i * (x.length - 1) / (n - 1);
            int j = (int) p; double fr = p - j;
            y[i] = (float) (j + 1 < x.length ? x[j] * (1 - fr) + x[j + 1] * fr : x[j]);
        }
        return y;
    }

    /** Cut leading/trailing quiet parts. Returns null if nothing loud was heard. */
    public static float[] trim(float[] x, double minPeak) {
        int win = 320; // 20 ms
        int n = x.length / win;
        if (n == 0) return null;
        double[] e = new double[n];
        double mx = 0;
        for (int i = 0; i < n; i++) {
            double s = 0;
            for (int j = 0; j < win; j++) { double v = x[i * win + j]; s += v * v; }
            e[i] = Math.sqrt(s / win);
            mx = Math.max(mx, e[i]);
        }
        if (mx < minPeak) return null;
        int a = 0, b = n - 1;
        while (a < n && e[a] < 0.1 * mx) a++;
        while (b > a && e[b] < 0.1 * mx) b--;
        a = Math.max(0, a - 5); b = Math.min(n - 1, b + 5); // keep 100 ms around it
        float[] y = new float[(b - a + 1) * win];
        System.arraycopy(x, a * win, y, 0, y.length);
        return y;
    }

    private static int le16(byte[] b, int i) { return (b[i] & 0xff) | ((b[i + 1] & 0xff) << 8); }
    private static int le32(byte[] b, int i) { return le16(b, i) | (le16(b, i + 2) << 16); }
}
