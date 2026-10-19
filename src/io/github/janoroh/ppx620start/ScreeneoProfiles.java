package io.github.janoroh.ppx620start;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Liest die Farbprofile der Screeneo-Farb-App. Sie speichert sie (QML-Settings, Kategorie colorSettings)
 * in /sdcard/.com.screeneo.app.Launcher.preferences.ini als "userSlot1=650, 110, 330, 14".
 */
final class ScreeneoProfiles {
    static final String FILE = "/sdcard/.com.screeneo.app.Launcher.preferences.ini";

    /** Rot, Gruen, Blau, Look des Profils (1 oder 2) oder null, wenn nicht vorhanden/unlesbar. */
    static int[] slot(int n) throws Exception {
        String key = "userSlot" + n + "=";
        boolean inSection = false;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                l = l.trim();
                if (l.startsWith("[")) inSection = l.equals("[colorSettings]");
                else if (inSection && l.startsWith(key)) {
                    String[] p = l.substring(key.length()).split(",");
                    if (p.length < 4) return null;
                    int[] v = new int[4];
                    for (int i = 0; i < 4; i++) v[i] = Integer.parseInt(p[i].trim());
                    return v;
                }
            }
        }
        return null;
    }

    /** Aktive Werte des Projektors: aus rgbcurrent ("red_current=650,green_current=110,blue_current=330") und looks. */
    /** Aktive Werte {R, G, B, Look}; null, wenn der Treiber keine zwei gleichen Lesungen liefert. */
    static int[] active() {
        return Worker.readActive();
    }
}
