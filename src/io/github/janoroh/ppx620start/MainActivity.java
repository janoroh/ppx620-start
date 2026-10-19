package io.github.janoroh.ppx620start;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Bedienung per Fernbedienung: Startverhalten, Farbkorrektur mit Testbild, Status, Protokoll. */
public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 1;

    private final Handler handler = new Handler();
    private ScrollView scroll;
    private boolean fullscreen;
    private TestPatternView pattern;
    private TextView statusSource, statusColor, statusMode, logView;
    private Button targetBtn, guardBtn, colorBtn, delayText, patternBtn, logBtn, takeActiveBtn, reapplyBtn;
    private TextView mismatchText;
    private final SeekBar[] bars = new SeekBar[4];
    private final TextView[] values = new TextView[4];
    private boolean updatingBars;
    private int pendingSlot;

    /** Fuer welchen Betrieb die Regler gerade gelten (Netz/Akku); wechselt mit dem Netzteil. */
    private boolean barsBattery;
    private TextView profileLabel;
    private final TextView[] slotText = new TextView[ProfileSlots.COUNT];
    /** Fotos in assets/testbilder (Unsplash-Lizenz, Quellen in CREDITS.txt), benannt wie die Screeneo-Testbilder;
     *  bei installierter Screeneo-App werden stattdessen deren Originale gezeigt. */
    private static final String[] PHOTOS = {"A1", "A2", "B1", "B2", "C1", "C2", "C3", "D1", "D2", "D3", "D4", "D5", "D6", "D7"};
    private static final String[] PHOTO_NAMES = {"A1 Skintone", "A2 Clarity", "B1 Saturation 1", "B2 Saturation 2",
            "C1 Reds", "C2 Greens", "C3 Blues", "D1 Full Range 1", "D2 Full Range 2", "D3 Greys", "D4 High Key",
            "D5 Low Key 1", "D6 Low Key 2", "D7 Low Key 3"};
    private static final int[] MIN = {51, 51, 51, 0};
    private static final int[] MAX = {960, 960, 960, 18};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // links das Testbild, rechts die Bedienung auf eigenem, ruhigem Hintergrund (nicht ueberlappend)
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        FrameLayout patternArea = new FrameLayout(this);
        patternArea.setBackgroundColor(Color.BLACK);
        pattern = new TestPatternView(this);
        // Hintergrunddienst sicherstellen (z. B. direkt nach der Erstinstallation, vor dem ersten Neustart)
        startForegroundService(new android.content.Intent(this, StartService.class));
        pattern.setItems(buildItems(null), 1);
        if (screeneoInstalled()) loadScreeneoPhotos();
        patternArea.addView(pattern, new FrameLayout.LayoutParams(-1, -1));
        root.addView(patternArea, new LinearLayout.LayoutParams(0, -1, 1f));

        scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        scroll.setBackgroundColor(Color.rgb(28, 28, 30));
        scroll.addView(panel);
        root.addView(scroll, new LinearLayout.LayoutParams(dp(330), -1));

        TextView title = text(panel, getString(R.string.app_name), 22, true);
        title.setTextColor(Color.rgb(255, 179, 0));
        text(panel, getString(R.string.subtitle), 13, false);

        // Startverhalten
        header(panel, R.string.section_start);
        targetBtn = button(panel, v -> toggleTarget());
        guardBtn = button(panel, v -> toggle("guard"));
        LinearLayout delayRow = row(panel);
        button(delayRow, v -> delay(-1000)).setText(R.string.delay_minus);
        delayText = button(delayRow, null);
        delayText.setFocusable(false);
        delayText.setBackground(null);
        delayText.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        button(delayRow, v -> delay(1000)).setText(R.string.delay_plus);
        small(panel, R.string.start_help);

        // Farbe
        header(panel, R.string.section_color);
        colorBtn = button(panel, v -> toggle("color"));
        LinearLayout patRow = row(panel);
        button(patRow, v -> showPattern(pattern.getIndex() - 1)).setText("◀");
        patternBtn = button(patRow, null);
        patternBtn.setFocusable(false);
        patternBtn.setBackground(null);
        patternBtn.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        button(patRow, v -> showPattern(pattern.getIndex() + 1)).setText("▶");
        button(panel, v -> setFullscreen(true)).setText(R.string.fullscreen);
        button(panel, v -> showCredits()).setText(R.string.credits);
        profileLabel = text(panel, "", 14, true);
        int[] labels = {R.string.red, R.string.green, R.string.blue, R.string.look};
        for (int i = 0; i < 4; i++) addBar(panel, i, getString(labels[i]));
        small(panel, R.string.color_help);
        small(panel, R.string.modes_help);
        mismatchText = text(panel, "", 13, false);
        mismatchText.setTextColor(Color.rgb(255, 213, 79));
        takeActiveBtn = button(panel, v -> takeOver(ScreeneoProfiles.active()));
        takeActiveBtn.setText(R.string.take_active);
        reapplyBtn = button(panel, v -> applyAsync());
        reapplyBtn.setText(R.string.apply_now);
        // eigene Speicherplaetze
        header(panel, R.string.section_slots);
        for (int n = 1; n <= ProfileSlots.COUNT; n++) {
            final int slot = n;
            slotText[n - 1] = text(panel, "", 13, false);
            LinearLayout r = row(panel);
            button(r, v -> loadSlot(slot)).setText(R.string.slot_load);
            button(r, v -> confirmSaveSlot(slot)).setText(R.string.slot_save);
        }
        small(panel, R.string.slots_help);

        // darunter: Profile aus der Screeneo-App uebernehmen
        if (screeneoInstalled()) {
            button(panel, v -> takeSlot(1)).setText(R.string.take_slot1);
            button(panel, v -> takeSlot(2)).setText(R.string.take_slot2);
        }

        // Status und Protokoll
        header(panel, R.string.section_status);
        statusSource = text(panel, "", 15, false);
        statusColor = text(panel, "", 15, false);
        statusMode = text(panel, "", 15, false);
        logBtn = button(panel, v -> {
            logView.setVisibility(logView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            refresh();
        });
        logView = text(panel, "", 11, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setVisibility(View.GONE);

        setContentView(root);
        targetBtn.requestFocus();
        loadBars();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 2000);
        }
    };

    // ---------- Aufbau ----------
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(ViewGroup parent, String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(235, 235, 235));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        parent.addView(t);
        return t;
    }

    private void header(ViewGroup parent, int res) {
        TextView t = text(parent, getString(res), 18, true);
        t.setTextColor(Color.rgb(255, 179, 0));
        t.setPadding(0, dp(16), 0, dp(4));
    }

    private void small(ViewGroup parent, int res) {
        TextView t = text(parent, getString(res), 12, false);
        t.setTextColor(Color.rgb(170, 170, 170));
        t.setPadding(0, dp(4), 0, dp(4));
    }

    private LinearLayout row(ViewGroup parent) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(r);
        return r;
    }

    private Button button(ViewGroup parent, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setTextSize(15);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundResource(R.drawable.btn_bg);
        btn.setPadding(dp(14), dp(4), dp(14), dp(4));
        btn.setMinHeight(dp(36));
        btn.setMinimumHeight(dp(36));
        if (l != null) btn.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                parent instanceof LinearLayout && ((LinearLayout) parent).getOrientation() == LinearLayout.HORIZONTAL ? -2 : -1, -2);
        p.setMargins(0, dp(3), dp(6), dp(3));
        parent.addView(btn, p);
        return btn;
    }

    private void addBar(ViewGroup parent, int i, String label) {
        LinearLayout r = row(parent);
        TextView name = text(r, label, 15, false);
        name.setLayoutParams(new LinearLayout.LayoutParams(dp(52), -2));
        SeekBar sb = new SeekBar(this);
        sb.setMin(MIN[i]);
        sb.setMax(MAX[i]);
        sb.setKeyProgressIncrement(1);
        sb.setBackgroundResource(R.drawable.seek_bg);
        sb.setFocusable(true);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                values[i].setText(String.valueOf(v));
                if (!updatingBars) {
                    keepBatteryProfile();
                    Settings.prefs(MainActivity.this).edit().putInt(Settings.keys(barsBattery)[i], v).apply();
                    handler.removeCallbacks(applyLater);
                    handler.postDelayed(applyLater, 150);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        r.addView(sb, new LinearLayout.LayoutParams(0, dp(40), 1f));
        TextView val = text(r, "", 15, true);
        val.setLayoutParams(new LinearLayout.LayoutParams(dp(44), -2));
        val.setGravity(Gravity.END);
        bars[i] = sb;
        values[i] = val;
    }

    // ---------- Testbilder ----------
    private void showPattern(int i) {
        pattern.setIndex(i);
        refresh();
    }

    private void showCredits() {
        String text;
        try (java.io.InputStream in = getAssets().open("testbilder/CREDITS.txt")) {
            text = new String(readAll(in), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            text = "";
        }
        new android.app.AlertDialog.Builder(this).setTitle(R.string.credits).setMessage(text)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return b.toByteArray();
    }

    /**
     * Testbildliste. screeneo: Name -> Ressourcenname der Original-Screeneo-Fotos; null oder unvollstaendig
     * heisst, die mitgelieferten Fotos zu zeigen.
     */
    private java.util.List<TestPatternView.Item> buildItems(java.util.Map<String, String> screeneo) {
        String[] pn = getResources().getStringArray(R.array.pattern_names);
        java.util.List<TestPatternView.Item> items = new java.util.ArrayList<>();
        items.add(new TestPatternView.Item(TestPatternView.Kind.OFF, pn[0], null));
        items.add(new TestPatternView.Item(TestPatternView.Kind.BARS, pn[1], null));
        items.add(new TestPatternView.Item(TestPatternView.Kind.CHECKER, pn[2], null));
        for (int i = 0; i < PHOTOS.length; i++) {
            if (screeneo != null) items.add(new TestPatternView.Item(TestPatternView.Kind.SCREENEO, PHOTO_NAMES[i], screeneo.get(PHOTO_NAMES[i])));
            else items.add(new TestPatternView.Item(TestPatternView.Kind.ASSET, PHOTO_NAMES[i], PHOTOS[i]));
        }
        items.add(new TestPatternView.Item(TestPatternView.Kind.BLACK, pn[3], null));
        items.add(new TestPatternView.Item(TestPatternView.Kind.ALIGN, pn[4], null));
        items.add(new TestPatternView.Item(TestPatternView.Kind.WHITE, pn[5], null));
        return items;
    }

    /** Originalfotos aus der Screeneo-App suchen (dauert etwas) und, wenn alle da sind, statt der eigenen zeigen. */
    private void loadScreeneoPhotos() {
        new Thread(() -> {
            java.util.Map<String, long[]> idx = ScreeneoImages.index(this);
            java.util.Map<String, String> names = new java.util.HashMap<>();
            for (String res : idx.keySet()) {
                int dot = res.lastIndexOf('.');
                names.put(dot > 0 ? res.substring(0, dot) : res, res);
            }
            for (String n : PHOTO_NAMES) if (!names.containsKey(n)) return;
            runOnUiThread(() -> {
                pattern.setItems(buildItems(names), pattern.getIndex());
                refresh();
            });
        }).start();
    }

    private boolean screeneoInstalled() {
        try {
            getPackageManager().getApplicationInfo(ScreeneoImages.PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void setFullscreen(boolean on) {
        fullscreen = on;
        pattern.setFit(on);
        scroll.setVisibility(on ? View.GONE : View.VISIBLE);
        if (on) Toast.makeText(this, R.string.fullscreen_hint, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (fullscreen && e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case android.view.KeyEvent.KEYCODE_DPAD_LEFT: showPattern(pattern.getIndex() - 1); return true;
                case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: showPattern(pattern.getIndex() + 1); return true;
                case android.view.KeyEvent.KEYCODE_BACK:
                case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
                case android.view.KeyEvent.KEYCODE_ENTER: setFullscreen(false); return true;
                default: return true;
            }
        }
        if (fullscreen) return true;
        return super.dispatchKeyEvent(e);
    }

    // ---------- Aktionen ----------
    private final Runnable applyLater = this::applyAsync;

    /** Ein Hintergrund-Thread fuer alle Schreibvorgaenge; laeuft schon einer, genuegt einer danach. */
    private final java.util.concurrent.ExecutorService applier = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean applyQueued = new java.util.concurrent.atomic.AtomicBoolean();

    private void applyAsync() {
        if (!applyQueued.compareAndSet(false, true)) return;
        applier.execute(() -> {
            applyQueued.set(false);
            new Worker(this).applyColor("app");
            runOnUiThread(this::refreshStatus);
        });
    }

    private void takeOver(int[] v) {
        if (v == null || v[0] > MAX[0] || v[1] > MAX[1] || v[2] > MAX[2] || v[0] < MIN[0] || v[1] < MIN[1] || v[2] < MIN[2]) {
            Toast.makeText(this, R.string.slot_missing, Toast.LENGTH_LONG).show();
            return;
        }
        // Die Werte gelten fuer den Modus, in dem man gerade ist; gespeichert wird das Economy-Grundprofil
        PowerMode.init(this);
        int[] base = PowerMode.toBase(v, PowerMode.current);
        String[] keys = Settings.keys(PowerMode.onBattery());
        SharedPreferences.Editor e = Settings.prefs(this).edit();
        for (int i = 0; i < 4; i++) e.putInt(keys[i], Math.max(MIN[i], Math.min(MAX[i], base[i])));
        e.commit();
        Logbook.log(this, "Color values taken over in " + PowerMode.current + (PowerMode.onBattery() ? " (battery)" : "") + ": " + v[0] + "," + v[1] + "," + v[2]
                + ",look " + v[3] + " -> economy profile " + base[0] + "," + base[1] + "," + base[2]);
        loadBars();
        applyAsync();
        Toast.makeText(this, getString(R.string.taken, v[0] + " / " + v[1] + " / " + v[2] + " · " + v[3]), Toast.LENGTH_LONG).show();
    }

    private void takeSlot(int n) {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingSlot = n;
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        try {
            takeOver(ScreeneoProfiles.slot(n));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.slot_missing) + " (" + e.getMessage() + ")", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code != REQ_STORAGE) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && pendingSlot > 0) takeSlot(pendingSlot);
        else Toast.makeText(this, R.string.need_storage, Toast.LENGTH_LONG).show();
    }

    private void toggle(String key) {
        SharedPreferences p = Settings.prefs(this);
        p.edit().putBoolean(key, !p.getBoolean(key, true)).commit();
        Logbook.log(this, "Settings changed (remote): " + Settings.load(this));
        refresh();
    }

    private void toggleTarget() {
        Settings.prefs(this).edit().putString("target", Settings.load(this).toLauncher() ? "hdmi" : "launcher").commit();
        Logbook.log(this, "Settings changed (remote): " + Settings.load(this));
        refresh();
    }

    private void delay(int stepMs) {
        SharedPreferences p = Settings.prefs(this);
        int d = Math.max(5000, Math.min(60000, p.getInt("delay_ms", Settings.DEFAULT_DELAY_MS) + stepMs));
        p.edit().putInt("delay_ms", d).commit();
        Logbook.log(this, "Settings changed (remote): " + Settings.load(this));
        refresh();
    }

    // ---------- Anzeige ----------
    private void loadBars() {
        Settings s = Settings.load(this);
        barsBattery = PowerMode.onBattery();
        int[] v = s.base(barsBattery);
        profileLabel.setText(barsBattery ? R.string.profile_battery : R.string.profile_mains);
        refreshSlots();
        updatingBars = true;
        for (int i = 0; i < 4; i++) {
            bars[i].setProgress(v[i]);
            values[i].setText(String.valueOf(v[i]));
        }
        updatingBars = false;
    }

    private void refresh() {
        Settings s = Settings.load(this);
        targetBtn.setText(s.toLauncher() ? R.string.target_launcher : R.string.target_hdmi);
        guardBtn.setText(s.guard ? R.string.guard_on : R.string.guard_off);
        colorBtn.setText(s.color ? R.string.color_on : R.string.color_off);
        delayText.setText(getString(R.string.delay, s.delayMs / 1000));
        patternBtn.setText(pattern.getLabel());
        logBtn.setText(logView.getVisibility() == View.VISIBLE ? R.string.hide_log : R.string.show_log);
        refreshStatus();
    }

    private void refreshSlots() {
        for (int n = 1; n <= ProfileSlots.COUNT; n++) {
            int[][] s = ProfileSlots.get(this, n);
            String t = s == null ? getString(R.string.slot_empty)
                    : getString(R.string.slot_mains) + " " + ProfileSlots.describe(s[0]) + " · "
                    + getString(R.string.slot_battery) + " " + (s[1] == null ? getString(R.string.slot_derived) : ProfileSlots.describe(s[1]));
            slotText[n - 1].setText(getString(R.string.slot_title, n) + ": " + t);
        }
    }

    private void loadSlot(int n) {
        if (!ProfileSlots.load(this, n)) {
            Toast.makeText(this, R.string.slot_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        loadBars();
        applyAsync();
        Toast.makeText(this, getString(R.string.slot_loaded, n), Toast.LENGTH_SHORT).show();
    }

    private void confirmSaveSlot(int n) {
        Runnable save = () -> {
            ProfileSlots.save(this, n);
            refreshSlots();
            Toast.makeText(this, getString(R.string.slot_saved, n), Toast.LENGTH_SHORT).show();
        };
        if (ProfileSlots.get(this, n) == null) {
            save.run();
            return;
        }
        new android.app.AlertDialog.Builder(this).setMessage(getString(R.string.slot_overwrite, n))
                .setPositiveButton(R.string.slot_save, (d, w) -> save.run())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** Erste Aenderung am abgeleiteten Akkuprofil: alle vier Werte festschreiben, damit es eigenstaendig bleibt. */
    private void keepBatteryProfile() {
        if (!barsBattery) return;
        SharedPreferences p = Settings.prefs(this);
        if (p.contains(Settings.BATTERY_KEYS[0])) return;
        int[] b = Settings.load(this).battery;
        SharedPreferences.Editor e = p.edit();
        for (int i = 0; i < 4; i++) e.putInt(Settings.BATTERY_KEYS[i], b[i]);
        e.commit();
    }

    private void refreshStatus() {
        if (PowerMode.onBattery() != barsBattery) loadBars();
        String sw = Worker.read("/sys/class/switch/hdmi/state");
        statusSource.setText(getString(R.string.status_source, getString("0".equals(sw) ? R.string.source_hdmi : R.string.source_android)));
        int[] a = ScreeneoProfiles.active();
        statusColor.setText(a == null ? "" : getString(R.string.status_color,
                String.valueOf(a[0]), String.valueOf(a[1]), String.valueOf(a[2]), String.valueOf(a[3])));
        Settings s = Settings.load(this);
        PowerMode.init(this);
        PowerMode.Mode m = PowerMode.current;
        String modeName = getString(m == PowerMode.Mode.ECO ? R.string.mode_eco
                : m == PowerMode.Mode.STANDARD ? R.string.mode_standard : R.string.mode_presentation);
        double f = PowerMode.factor(s, m);
        if (barsBattery) modeName = modeName + getString(R.string.on_battery);
        statusMode.setText(f == 1 ? getString(R.string.status_mode, modeName)
                : getString(R.string.status_mode_scaled, modeName, String.format(java.util.Locale.getDefault(), "%.2f", f)));
        // Erwartet ist das Profil fuer den aktuellen Modus (in Standard/Praesentation hochskaliert)
        int[] p = PowerMode.profile(s, m);
        boolean differs = a != null && (a[0] != p[0] || a[1] != p[1] || a[2] != p[2] || a[3] != p[3]);
        int vis = differs ? View.VISIBLE : View.GONE;
        mismatchText.setVisibility(vis);
        takeActiveBtn.setVisibility(vis);
        reapplyBtn.setVisibility(vis);
        if (differs) mismatchText.setText(getString(R.string.mismatch, a[0] + "/" + a[1] + "/" + a[2] + ", Look " + a[3]));
        if (logView.getVisibility() == View.VISIBLE) logView.setText(Logbook.tail(this, 5000));
    }
}
