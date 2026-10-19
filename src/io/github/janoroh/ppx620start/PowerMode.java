package io.github.janoroh.ppx620start;

/**
 * Energiemodi des Projektors. Ein Moduswechsel und das Ein-/Ausstecken des Netzteils setzen feste Werks-Stroeme
 * (gemessen 19.10.2026) und ueberschreiben damit das Farbprofil; am Akku wechselt zusaetzlich der Look auf 1,
 * zurueck am Netz auf 3. Erkennt man die Werkswerte, laesst sich sofort das passende eigene Profil setzen.
 * Am Akku gilt ein eigenes Grundprofil (Settings.battery). Die eigenen Profile behalten das Verhaeltnis Rot/Gruen/Blau der Kalibrierung und werden nur gleichmaessig
 * heller (Standard x1,25, Praesentation bis zum Anschlag 960) - die Werks-Modi werden vor allem ueber Gruen heller.
 */
final class PowerMode {
    enum Mode { ECO, STANDARD, PRESENTATION }

    static final int MAX_CURRENT = 960;
    static final double STANDARD_FACTOR = 1.25;

    /** Werks-Stroeme {Rot, Gruen, Blau} je Modus, am Netz und am Akku. */
    private static final int[][] PRESETS = {
            {590, 168, 300}, {940, 390, 420}, {960, 960, 700},
            {365, 170, 135}, {960, 447, 355}, {960, 950, 600}};
    private static final Mode[] PRESET_MODE = {
            Mode.ECO, Mode.STANDARD, Mode.PRESENTATION, Mode.ECO, Mode.STANDARD, Mode.PRESENTATION};

    /** Zuletzt erkannter Modus (Dienst und Bedienung laufen im selben Prozess; gespeichert fuer Neustarts). */
    static volatile Mode current = Mode.ECO;
    private static boolean loaded;

    static synchronized void init(android.content.Context c) {
        if (loaded) return;
        loaded = true;
        try {
            current = Mode.valueOf(Settings.prefs(c).getString("power_mode", "ECO"));
        } catch (Exception e) {
            current = Mode.ECO;
        }
    }

    static void set(android.content.Context c, Mode m) {
        loaded = true;
        current = m;
        Settings.prefs(c).edit().putString("power_mode", m.name()).apply();
    }

    private PowerMode() {
    }

    /** Modus, dessen Werks-Stroeme gerade anliegen, sonst null. */
    static Mode fromPreset(int[] rgb) {
        if (rgb == null) return null;
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i][0] == rgb[0] && PRESETS[i][1] == rgb[1] && PRESETS[i][2] == rgb[2]) return PRESET_MODE[i];
        }
        return null;
    }

    /** "red_current=650,green_current=110,blue_current=330" -> {650, 110, 330}; null wenn unlesbar. */
    static int[] parse(String rgbcurrent) {
        try {
            int[] v = new int[3];
            String[] parts = rgbcurrent.trim().split(",");
            for (int i = 0; i < 3; i++) v[i] = Integer.parseInt(parts[i].substring(parts[i].indexOf('=') + 1).trim());
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    /** Netzteil gezogen? (rk-ac/online = 0) */
    static boolean onBattery() {
        return "0".equals(Worker.read("/sys/class/power_supply/rk-ac/online"));
    }

    static double factor(Settings s, Mode m) {
        int[] b = s.base(onBattery());
        double max = MAX_CURRENT / (double) Math.max(b[0], Math.max(b[1], b[2]));
        if (max < 1) max = 1;
        switch (m) {
            case STANDARD: return Math.min(STANDARD_FACTOR, max);
            case PRESENTATION: return max;
            default: return 1;
        }
    }

    /** Profil fuer den Modus: {Rot, Gruen, Blau, Look}. */
    static int[] profile(Settings s, Mode m) {
        double f = factor(s, m);
        int[] b = s.base(onBattery());
        return new int[]{scale(b[0], f), scale(b[1], f), scale(b[2], f), b[3]};
    }

    /** Summe der Werks-Stroeme in Economy am Netz (590 + 168 + 300) als grobes Mass fuer die Economy-Helligkeit. */
    private static final int ECO_SUM = 1058;

    /**
     * Werte {R, G, B, Look}, die im Modus m so angezeigt werden sollen, in das Grundprofil (Economy) umrechnen.
     * Standard: durch 1,25 teilen. Praesentation: dort ist Rot immer am Anschlag, die Werte legen also nur das
     * Farbverhaeltnis fest; Economy wird dann etwa so hell wie das Werks-Economy (gleiche Stromsumme).
     */
    static int[] toBase(int[] v, Mode m) {
        double f;
        if (m == Mode.STANDARD) f = 1 / STANDARD_FACTOR;
        else if (m == Mode.PRESENTATION) f = ECO_SUM / (double) Math.max(1, v[0] + v[1] + v[2]);
        else f = 1;
        double max = MAX_CURRENT / (double) Math.max(v[0], Math.max(v[1], v[2]));
        if (f > max) f = max;   // Verhaeltnis erhalten, auch wenn ein Wert sonst ueber 960 laege
        return new int[]{scale(v[0], f), scale(v[1], f), scale(v[2], f), v[3]};
    }

    private static int scale(int v, double f) {
        return Math.max(51, Math.min(MAX_CURRENT, (int) Math.round(v * f)));
    }
}
