package com.nscb.barkapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Two softmax heads (arousal, valence) loaded from models/bark_model.txt. */
public final class BarkModel {
    public static final String[] AROUSAL = {"Low", "Medium", "High"};
    public static final String[] VALENCE = {"Negative", "Neutral", "Positive"};

    private final double[] mu, sd;
    private final double[][] wA, wV;
    private final double[] bA, bV;

    public static final class Result {
        public final double[] arousal, valence;
        public final int a, v;
        Result(double[] arousal, double[] valence) {
            this.arousal = arousal; this.valence = valence;
            a = argmax(arousal); v = argmax(valence);
        }
        public double confidence() { return Math.min(arousal[a], valence[v]); }
        public String meaning() { return phrase(AROUSAL[a], VALENCE[v]); }
    }

    public BarkModel(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        int d = Integer.parseInt(r.readLine().trim());
        mu = row(r); sd = row(r);
        bA = row(r); wA = new double[d][]; for (int i = 0; i < d; i++) wA[i] = row(r);
        bV = row(r); wV = new double[d][]; for (int i = 0; i < d; i++) wV[i] = row(r);
    }

    private static double[] row(BufferedReader r) throws IOException {
        String[] p = r.readLine().trim().split(" ");
        double[] out = new double[p.length];
        for (int i = 0; i < p.length; i++) out[i] = Double.parseDouble(p[i]);
        return out;
    }

    private static double[] head(double[] z, double[][] w, double[] b) {
        int k = b.length;
        double[] o = b.clone();
        for (int i = 0; i < z.length; i++) for (int j = 0; j < k; j++) o[j] += z[i] * w[i][j];
        double mx = Double.NEGATIVE_INFINITY;
        for (double v : o) mx = Math.max(mx, v);
        double s = 0;
        for (int j = 0; j < k; j++) { o[j] = Math.exp(o[j] - mx); s += o[j]; }
        for (int j = 0; j < k; j++) o[j] /= s;
        return o;
    }

    public Result predict(double[] feat) {
        double[] z = new double[feat.length];
        for (int i = 0; i < z.length; i++) z[i] = (feat[i] - mu[i]) / sd[i];
        return new Result(head(z, wA, bA), head(z, wV, bV));
    }

    static int argmax(double[] a) {
        int b = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[b]) b = i;
        return b;
    }

    public static String phrase(String a, String v) {
        switch (a + "/" + v) {
            case "High/Positive": return "Very excited and happy (play, greeting)";
            case "High/Neutral": return "Highly worked up, mood unclear (alert, wants attention)";
            case "High/Negative": return "Agitated or distressed (warning, fear, frustration)";
            case "Medium/Positive": return "Happy and engaged";
            case "Medium/Neutral": return "Attentive, calling out";
            case "Medium/Negative": return "Uneasy or annoyed";
            case "Low/Positive": return "Relaxed and content";
            case "Low/Neutral": return "Calm, low-key sound";
            default: return "Quietly unhappy (whining, sulking)";
        }
    }
}
