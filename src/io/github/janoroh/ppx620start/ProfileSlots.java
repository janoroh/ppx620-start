package io.github.janoroh.ppx620start;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Drei Speicherplaetze fuer Farbprofile. Ein Platz haelt das Netzprofil und, falls eigens eingestellt, das
 * Akkuprofil ("r,g,b,look" bzw. "r,g,b,look;r,g,b,look"); ohne Akkuteil wird es beim Laden wieder abgeleitet.
 */
final class ProfileSlots {
    static final int COUNT = 3;

    private ProfileSlots() {
    }

    private static String key(int n) {
        return "slot" + n;
    }

    /** {Netz[4], Akku[4] oder null}, null wenn leer. */
    static int[][] get(Context c, int n) {
        String v = Settings.prefs(c).getString(key(n), null);
        if (v == null) return null;
        try {
            String[] parts = v.split(";");
            return new int[][]{nums(parts[0]), parts.length > 1 ? nums(parts[1]) : null};
        } catch (Exception e) {
            return null;
        }
    }

    static void save(Context c, int n) {
        SharedPreferences p = Settings.prefs(c);
        Settings s = Settings.load(c);
        String v = join(s.base(false));
        if (p.contains(Settings.BATTERY_KEYS[0])) v += ";" + join(s.battery);
        p.edit().putString(key(n), v).commit();
        Logbook.log(c, "Profile slot " + n + " saved: " + v);
    }

    static boolean load(Context c, int n) {
        int[][] slot = get(c, n);
        if (slot == null) return false;
        SharedPreferences.Editor e = Settings.prefs(c).edit();
        for (int i = 0; i < 4; i++) e.putInt(Settings.KEYS[i], slot[0][i]);
        for (int i = 0; i < 4; i++) {
            if (slot[1] != null) e.putInt(Settings.BATTERY_KEYS[i], slot[1][i]);
            else e.remove(Settings.BATTERY_KEYS[i]);
        }
        e.commit();
        Logbook.log(c, "Profile slot " + n + " loaded: " + Settings.load(c));
        return true;
    }

    static String describe(int[] v) {
        return v[0] + "/" + v[1] + "/" + v[2] + " L" + v[3];
    }

    private static String join(int[] v) {
        return v[0] + "," + v[1] + "," + v[2] + "," + v[3];
    }

    private static int[] nums(String s) {
        String[] p = s.split(",");
        int[] v = new int[4];
        for (int i = 0; i < 4; i++) v[i] = Integer.parseInt(p[i].trim());
        return v;
    }
}
