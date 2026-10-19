package io.github.janoroh.ppx620start;

import android.content.Context;
import android.content.SharedPreferences;

/** Einstellungen der App (SharedPreferences). */
final class Settings {
    /** Abstand UART-Rueckweg zum Chip-Startereignis. Gemessen: +11,9 s ignoriert, +13,1 s angenommen. */
    static final int DEFAULT_DELAY_MS = 13000;
    /** Farbprofil userSlot1 des Nutzers: Rot, Gruen, Blau, Look. */
    static final int[] DEFAULT_COLOR = {650, 110, 330, 14};

    /** Ziel nach dem Start: "launcher" (Rueckweg zu Android) oder "hdmi" (Chip bleibt auf HDMI). */
    final String target;
    /** Absicherung gegen Tastenblockade: Menue geschlossen, Bild aber weiter HDMI -> Rueckweg wiederholen. */
    final boolean guard;
    final boolean color;
    final int delayMs;
    final int red, green, blue, look;
    /**
     * Akkuprofil {Rot, Gruen, Blau, Look}. Am Akku wirken gleiche Stroeme anders (gleiche Werte gaben deutlichen
     * Magentastich). Solange nicht eigens eingestellt, wird es abgeleitet: die eigene Korrektur am Netz relativ
     * zum Werks-Economy (590/168/300) auf das Werks-Economy am Akku (365/170/135) uebertragen, mit dem Werks-Look
     * des Akkubetriebs (1). Mit Look 14 am Akku blieb das Bild bei jeder Stromwahl rotstichig (Test 19.10.2026),
     * die Werkseinstellung mit Look 1 wirkte besser.
     */
    final int[] battery;
    static final String[] KEYS = {"red", "green", "blue", "look"};
    static final String[] BATTERY_KEYS = {"bat_red", "bat_green", "bat_blue", "bat_look"};
    // Rot nach Augenabgleich am Akku (19.10.2026: 650 -> 330 bei Look 1; das Werksverhaeltnis 365/590 gab 402,
    // noch etwas zu rot); Gruen und Blau wie im Werksverhaeltnis, das passte.
    static final double[] BATTERY_FACTOR = {330 / 650.0, 170 / 168.0, 135 / 300.0};
    static final int BATTERY_LOOK = 1;

    private Settings(SharedPreferences p) {
        target = "launcher".equals(p.getString("target", "hdmi")) ? "launcher" : "hdmi";
        guard = p.getBoolean("guard", true);
        color = p.getBoolean("color", true);
        delayMs = p.getInt("delay_ms", DEFAULT_DELAY_MS);
        red = p.getInt("red", DEFAULT_COLOR[0]);
        green = p.getInt("green", DEFAULT_COLOR[1]);
        blue = p.getInt("blue", DEFAULT_COLOR[2]);
        look = p.getInt("look", DEFAULT_COLOR[3]);
        int[] mains = {red, green, blue, look};
        battery = new int[4];
        for (int i = 0; i < 4; i++) {
            int derived = i == 3 ? BATTERY_LOOK : Math.max(51, Math.min(960, (int) Math.round(mains[i] * BATTERY_FACTOR[i])));
            battery[i] = p.getInt(BATTERY_KEYS[i], derived);
        }
    }

    /** Grundprofil (Economy) {Rot, Gruen, Blau, Look} fuer Netz- oder Akkubetrieb. */
    int[] base(boolean onBattery) {
        return onBattery ? battery.clone() : new int[]{red, green, blue, look};
    }

    static String[] keys(boolean onBattery) {
        return onBattery ? BATTERY_KEYS : KEYS;
    }

    boolean toLauncher() {
        return "launcher".equals(target);
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static Settings load(Context c) {
        return new Settings(prefs(c));
    }

    @Override
    public String toString() {
        return "target=" + target + " unblock=" + guard + " color=" + color + " delay=" + delayMs
                + "ms profile=" + red + "," + green + "," + blue + ",look " + look
                + " battery=" + battery[0] + "," + battery[1] + "," + battery[2] + ",look " + battery[3];
    }
}
