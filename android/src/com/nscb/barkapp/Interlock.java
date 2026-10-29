package com.nscb.barkapp;

import java.util.Random;

/**
 * Simulated neural bridge with the spec's safety rules. Nothing is
 * transmitted anywhere; "sending" only appends to a log.
 */
public final class Interlock {
    public static final double MIN_INTERVAL_S = 0.5, MAX_HR_RISE = 0.15,
            MAX_INTENSITY = 0.05, MAX_TEMP_RISE = 0.5, MAX_STRESS = 0.4;
    public static final String[] STATES = {"calm", "rest", "play", "food", "alert"};
    public static final String[] PRESETS = {
            "0x4F9A sit + calm", "0x1A01 food soon", "0x2B02 go home",
            "0x3C03 play", "0x5D04 rest", "alert (fear-related)"};

    public double baselineHr = 80, hr = 80, temp = 38.5, stress = 0.1;
    public boolean severed;
    public String reason = "";
    private long lastTxMs = -100000;
    private final Random rng = new Random();

    /** Advance the simulated animal one tick toward the chosen state. */
    public void tick(String state) {
        boolean aroused = state.equals("alert") || state.equals("play");
        double target = baselineHr * (aroused ? 1.25 : 1.0);
        hr += (target - hr) * 0.3 + rng.nextGaussian();
        stress = state.equals("alert") ? 0.6 : 0.1 + rng.nextDouble() * 0.05;
    }

    /** Returns a log line. Throws IllegalStateException when blocked. */
    public String send(int preset, double intensity, String species) {
        if (severed) throw new IllegalStateException("Link is cut (" + reason + "). A person must reset it.");
        if (hr > baselineHr * (1 + MAX_HR_RISE))
            sever(String.format("heart rate %.0f bpm is over +15%% of baseline %.0f", hr, baselineHr));
        if (temp - 38.5 > MAX_TEMP_RISE) sever("tissue temperature rose too much");
        if (stress > MAX_STRESS) sever(String.format("stress %.2f is over %.2f", stress, MAX_STRESS));
        if (preset == PRESETS.length - 1)
            throw new IllegalStateException("Fear/survival concepts can never be sent.");
        if (intensity <= 0 || intensity > MAX_INTENSITY)
            throw new IllegalStateException(String.format("Intensity %.3f is over the %.2f cap.", intensity, MAX_INTENSITY));
        long now = System.currentTimeMillis();
        if (now - lastTxMs < MIN_INTERVAL_S * 1000)
            throw new IllegalStateException("Too fast: wait 500 ms between pulses.");
        lastTxMs = now;
        return String.format("[SIMULATED] %s → %s pattern @ %.3f (HR %.0f, stress %.2f)",
                PRESETS[preset], species, intensity, hr, stress);
    }

    private void sever(String why) {
        severed = true; reason = why;
        throw new IllegalStateException("LINK CUT: " + why);
    }

    public void reset(String operator) {
        if (operator == null || operator.trim().isEmpty())
            throw new IllegalStateException("Type your name to reset.");
        severed = false; reason = "";
        hr = baselineHr;
    }
}
