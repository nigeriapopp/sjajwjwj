package com.nscb.barkapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 1, REQ_FILE = 2, MAX_SECONDS = 5;
    private static final int BG = 0xFF101418, CARD = 0xFF1C232B, TEXT = 0xFFECEFF3,
            MUTED = 0xFF9AA5B1, ACCENT = 0xFF4FC3F7, WARN = 0xFFFFB74D, BAD = 0xFFEF5350, GOOD = 0xFF66BB6A;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private BarkModel model;
    private volatile boolean recording;

    private View listenTab, labTab;
    private Button tabListen, tabLab, listenBtn;
    private TextView status, meaning, conf;
    private final ProgressBar[] aBars = new ProgressBar[3], vBars = new ProgressBar[3];
    private final TextView[] aPct = new TextView[3], vPct = new TextView[3];
    private LinearLayout resultCard;

    private final Interlock lock = new Interlock();
    private Spinner speciesSp, stateSp, conceptSp;
    private TextView vitals, intensityLabel, log;
    private EditText operator;
    private double intensity = 0.02;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try (InputStream in = getAssets().open("bark_model.txt")) {
            model = new BarkModel(in);
        } catch (Exception e) {
            throw new RuntimeException("model missing", e);
        }
        LinearLayout root = col();
        root.setBackgroundColor(BG);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(12), dp(12), dp(12), dp(4));
        tabListen = tabButton("Listen");
        tabLab = tabButton("Experimental");
        tabs.addView(tabListen, new LinearLayout.LayoutParams(0, dp(44), 1));
        tabs.addView(tabLab, new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(tabs);
        listenTab = buildListen();
        labTab = buildLab();
        root.addView(listenTab, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(labTab, new LinearLayout.LayoutParams(-1, 0, 1));
        tabListen.setOnClickListener(v -> showTab(true));
        tabLab.setOnClickListener(v -> showTab(false));
        showTab(true);
        setContentView(root);
        ui.post(tickLoop);
    }

    // ---------------- Listen tab ----------------

    private View buildListen() {
        LinearLayout c = col();
        c.setPadding(dp(16), dp(8), dp(16), dp(24));
        c.addView(text("Bark Reader", 26, TEXT, true));
        c.addView(text("Hold the phone near your dog, tap Listen, and let it bark.", 15, MUTED, false));

        listenBtn = new Button(this);
        listenBtn.setText("🎤  Listen");
        listenBtn.setTextSize(20);
        listenBtn.setTextColor(Color.BLACK);
        listenBtn.setAllCaps(false);
        listenBtn.setBackground(round(ACCENT, 40));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(72));
        lp.topMargin = dp(20);
        c.addView(listenBtn, lp);
        listenBtn.setOnClickListener(v -> { if (recording) recording = false; else startListening(); });

        Button file = new Button(this);
        file.setText("Analyze a .wav file");
        file.setAllCaps(false);
        file.setTextColor(TEXT);
        file.setBackground(round(CARD, 12));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(48));
        fp.topMargin = dp(10);
        c.addView(file, fp);
        file.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            startActivityForResult(i, REQ_FILE);
        });

        status = text("", 14, MUTED, false);
        status.setPadding(0, dp(12), 0, 0);
        c.addView(status);

        resultCard = col();
        resultCard.setBackground(round(CARD, 16));
        resultCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(12);
        c.addView(resultCard, rp);
        meaning = text("", 22, TEXT, true);
        conf = text("", 14, MUTED, false);
        resultCard.addView(meaning);
        resultCard.addView(conf);
        resultCard.addView(section("Energy (arousal)"));
        for (int i = 0; i < 3; i++) resultCard.addView(barRow(BarkModel.AROUSAL[i], aBars, aPct, i, ACCENT));
        resultCard.addView(section("Mood (valence)"));
        int[] vc = {BAD, MUTED, GOOD};
        for (int i = 0; i < 3; i++) resultCard.addView(barRow(BarkModel.VALENCE[i], vBars, vPct, i, vc[i]));
        resultCard.setVisibility(View.GONE);

        TextView note = text("How accurate is it? On dogs it never heard during training it gets energy "
                + "right about 46% of the time and mood about 40% (random guessing: 33%). "
                + "Treat it as a hint and watch body language too. Trained on Husky and Shiba Inu "
                + "barks (EmotionalCanines dataset, CC BY-NC-SA 4.0, non-commercial).", 12, MUTED, false);
        note.setPadding(0, dp(20), 0, 0);
        c.addView(note);
        return scroll(c);
    }

    private void startListening() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        recording = true;
        listenBtn.setText("■  Stop");
        listenBtn.setBackground(round(BAD, 40));
        new Thread(this::recordLoop).start();
    }

    private void recordLoop() {
        int sr = BarkFeatures.SR;
        int min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord rec;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, sr));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException();
        } catch (Exception e) {
            ui.post(() -> { setStatus("Couldn't open the microphone."); stopUi(); });
            return;
        }
        short[] buf = new short[sr * MAX_SECONDS];
        int got = 0;
        rec.startRecording();
        while (recording && got < buf.length) {
            int n = rec.read(buf, got, Math.min(1600, buf.length - got));
            if (n <= 0) break;
            got += n;
            final int secs = MAX_SECONDS - got / sr;
            ui.post(() -> setStatus("Listening… " + secs + " s left (tap Stop when done)"));
        }
        rec.stop();
        rec.release();
        recording = false;
        float[] x = new float[got];
        for (int i = 0; i < got; i++) x[i] = buf[i] / 32768f;
        ui.post(this::stopUi);
        analyze(x);
    }

    private void stopUi() {
        listenBtn.setText("🎤  Listen");
        listenBtn.setBackground(round(ACCENT, 40));
    }

    private void analyze(float[] raw) {
        float[] x = Audio.trim(raw, 0.02);
        if (x == null) {
            ui.post(() -> { setStatus("No bark heard. Try closer to the dog."); resultCard.setVisibility(View.GONE); });
            return;
        }
        BarkModel.Result r = model.predict(BarkFeatures.compute(x));
        ui.post(() -> show(r, x.length / (double) BarkFeatures.SR));
    }

    private void show(BarkModel.Result r, double secs) {
        setStatus(String.format("Analyzed %.1f s of sound.", secs));
        meaning.setText(r.meaning());
        double c = r.confidence();
        String level = c >= 0.6 ? "fairly sure" : c >= 0.45 ? "unsure" : "a guess";
        conf.setText(String.format("Confidence: %.0f%% (%s)", c * 100, level));
        conf.setTextColor(c >= 0.6 ? GOOD : c >= 0.45 ? WARN : MUTED);
        for (int i = 0; i < 3; i++) {
            aBars[i].setProgress((int) Math.round(r.arousal[i] * 100));
            aPct[i].setText(Math.round(r.arousal[i] * 100) + "%");
            vBars[i].setProgress((int) Math.round(r.valence[i] * 100));
            vPct[i].setText(Math.round(r.valence[i] * 100) + "%");
        }
        resultCard.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_FILE || res != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        setStatus("Reading file…");
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                analyze(Audio.readWav(in));
            } catch (Exception e) {
                ui.post(() -> setStatus("Couldn't read that file: " + e.getMessage()));
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        if (req == REQ_MIC) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else setStatus("Microphone permission is needed to listen.");
        }
    }

    // ---------------- Experimental tab ----------------

    private View buildLab() {
        LinearLayout c = col();
        c.setPadding(dp(16), dp(8), dp(16), dp(24));
        c.addView(text("Neural Bridge (experimental)", 22, TEXT, true));
        TextView banner = text("SIMULATION ONLY. There is no brain-interface hardware. Nothing is "
                + "transmitted; this shows how the safety locks behave.", 13, Color.BLACK, true);
        banner.setBackground(round(WARN, 10));
        banner.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(8);
        c.addView(banner, bp);

        c.addView(section("Species profile"));
        speciesSp = spinner(new String[]{"canine", "avian", "feline"});
        c.addView(speciesSp);
        c.addView(section("Simulated animal state"));
        stateSp = spinner(Interlock.STATES);
        c.addView(stateSp);
        c.addView(section("Concept to send"));
        conceptSp = spinner(Interlock.PRESETS);
        c.addView(conceptSp);

        intensityLabel = section("");
        c.addView(intensityLabel);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(20);
        c.addView(sb);
        updateIntensity(20);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { updateIntensity(p); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        vitals = text("", 15, TEXT, false);
        vitals.setPadding(0, dp(12), 0, dp(4));
        c.addView(vitals);

        Button send = new Button(this);
        send.setText("Send concept (simulated)");
        send.setAllCaps(false);
        send.setTextColor(Color.BLACK);
        send.setBackground(round(ACCENT, 12));
        c.addView(send, new LinearLayout.LayoutParams(-1, dp(52)));
        send.setOnClickListener(v -> {
            try {
                addLog(lock.send(conceptSp.getSelectedItemPosition(), intensity,
                        (String) speciesSp.getSelectedItem()), GOOD);
            } catch (IllegalStateException e) {
                addLog("BLOCKED: " + e.getMessage(), BAD);
            }
            refreshVitals();
        });

        LinearLayout resetRow = new LinearLayout(this);
        resetRow.setPadding(0, dp(10), 0, 0);
        operator = new EditText(this);
        operator.setHint("Your name (to reset)");
        operator.setHintTextColor(MUTED);
        operator.setTextColor(TEXT);
        operator.setInputType(InputType.TYPE_CLASS_TEXT);
        resetRow.addView(operator, new LinearLayout.LayoutParams(0, -2, 1));
        Button reset = new Button(this);
        reset.setText("Reset link");
        reset.setAllCaps(false);
        reset.setTextColor(TEXT);
        reset.setBackground(round(CARD, 12));
        resetRow.addView(reset, new LinearLayout.LayoutParams(-2, dp(48)));
        c.addView(resetRow);
        reset.setOnClickListener(v -> {
            try {
                lock.reset(operator.getText().toString());
                addLog("Link reset by " + operator.getText(), ACCENT);
            } catch (IllegalStateException e) {
                addLog(e.getMessage(), WARN);
            }
            refreshVitals();
        });

        TextView rules = text("Safety rules: 500 ms between pulses · link cuts if heart rate rises over 15% "
                + "or stress is high · intensity capped at 0.05 · fear concepts never sent · "
                + "only a named person can reset.", 12, MUTED, false);
        rules.setPadding(0, dp(12), 0, 0);
        c.addView(rules);

        c.addView(section("Log"));
        log = text("", 13, TEXT, false);
        log.setTypeface(Typeface.MONOSPACE);
        c.addView(log);
        return scroll(c);
    }

    private void updateIntensity(int p) {
        intensity = Math.max(1, p) / 1000.0;
        intensityLabel.setText(String.format("Intensity: %.3f %s", intensity,
                intensity > Interlock.MAX_INTENSITY ? "(over cap — will be blocked)" : ""));
    }

    private final Runnable tickLoop = new Runnable() {
        @Override public void run() {
            if (stateSp != null) lock.tick((String) stateSp.getSelectedItem());
            refreshVitals();
            ui.postDelayed(this, 500);
        }
    };

    private void refreshVitals() {
        if (vitals == null) return;
        String link = lock.severed ? "CUT" : "OK";
        vitals.setText(String.format("Heart rate %.0f bpm (baseline %.0f, limit %.0f) · stress %.2f · link %s",
                lock.hr, lock.baselineHr, lock.baselineHr * 1.15, lock.stress, link));
        vitals.setTextColor(lock.severed ? BAD : TEXT);
    }

    private void addLog(String line, int color) {
        log.append(line + "\n\n");
        log.setTextColor(TEXT);
    }

    // ---------------- helpers ----------------

    private void showTab(boolean listen) {
        listenTab.setVisibility(listen ? View.VISIBLE : View.GONE);
        labTab.setVisibility(listen ? View.GONE : View.VISIBLE);
        tabListen.setBackground(round(listen ? ACCENT : CARD, 10));
        tabListen.setTextColor(listen ? Color.BLACK : TEXT);
        tabLab.setBackground(round(listen ? CARD : WARN, 10));
        tabLab.setTextColor(listen ? TEXT : Color.BLACK);
    }

    private void setStatus(String s) { status.setText(s); }

    private Button tabButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout barRow(String label, ProgressBar[] bars, TextView[] pct, int i, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView l = text(label, 14, TEXT, false);
        row.addView(l, new LinearLayout.LayoutParams(dp(78), -2));
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        row.addView(pb, new LinearLayout.LayoutParams(0, dp(16), 1));
        TextView p = text("", 14, MUTED, false);
        p.setGravity(Gravity.END);
        row.addView(p, new LinearLayout.LayoutParams(dp(48), -2));
        bars[i] = pb;
        pct[i] = p;
        return row;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, cv, parent);
                t.setTextColor(TEXT);
                return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setBackground(round(CARD, 10));
        s.setPadding(dp(8), dp(10), dp(8), dp(10));
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refreshVitals(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        return s;
    }

    private TextView section(String s) {
        TextView t = text(s, 13, MUTED, true);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private ScrollView scroll(View v) {
        ScrollView s = new ScrollView(this);
        s.addView(v);
        return s;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
