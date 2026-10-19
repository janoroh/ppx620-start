package io.github.janoroh.ppx620start;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Ablauf nach dem Einschalten:
 *  1. Farbprofil setzen (der Kernel setzt Economy schon bei ~9,5 s, die App startet erst danach).
 *  2. Chip-Startereignis feststellen (SoC-HDMI-Switch 1 -> 0 bei ~29,9 s, Fenster 25-40 s).
 *  3. Ziel "launcher": fruehestens delayMs nach dem Ereignis den Herstellerrueckweg ausloesen
 *     (Broadcast EXT_HDMI_STATUS STATUS=0 an die InputSourceActivity). Ziel "hdmi": nichts tun,
 *     der Chip bleibt auf HDMI.
 *  4. Absicherung (dauerhaft): Ist das Eingangsmenue geschlossen (v56=0), das Bild aber weiter HDMI
 *     (sw=0), hat der Chip den Rueckweg ignoriert und die Fernbedienung ist blockiert. Dann Menue
 *     wieder oeffnen und den Rueckweg wiederholen, sobald der Chip ihn annimmt.
 * Schreibt nur das Farbprofil (wie die Farb-App), sendet nur diesen einen Broadcast-Typ und oeffnet
 * nur das exportierte Eingangsmenue (dessen onCreate schaltet nichts um).
 */
final class Worker {
    private static final String SW = "/sys/class/switch/hdmi/state";
    private static final String DLPC = "/sys/class/dlpc343x/";
    /**
     * Ruhezeit (s), die der Videochip nach seinem letzten Ereignis braucht (Start oder Umschaltbefehl, auch ein
     * ignorierter), bevor er den Rueckweg annimmt. Jeder zu fruehe Befehl startet die Frist neu. Gemessen: 11,1 und
     * 11,9 s ignoriert, 13,1 s angenommen; Versuche im Abstand von 7-11 s blieben alle wirkungslos.
     */
    private static final double QUIET_S = 14.0;
    /** Das Eingangsmenue schliesst sich etwa so lange nach seinem Umschaltbefehl (Vorhang). */
    private static final double MENU_CLOSE_S = 3.5;
    private static final int MAX_RESCUE = 4;

    private final Context c;
    private final Settings s;
    /** Zeitpunkt (Uptime, s) des letzten Umschaltbefehls an den Videochip, soweit bekannt oder geschaetzt. */
    private double lastCommand = -1;

    Worker(Context c) {
        this.c = c;
        this.s = Settings.load(c);
    }

    private void log(String m) {
        Logbook.log(c, m);
    }

    static double up() {
        return SystemClock.elapsedRealtime() / 1000.0;
    }

    private static String f2(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    void run() {
        boolean afterBoot = up() < 90;
        log("=== Start (" + (afterBoot ? "after boot" : "service restarted") + "), boot_id "
                + read("/proc/sys/kernel/random/boot_id") + ", " + s);
        log("State: sw=" + read(SW) + " v56=" + v56() + " rgb=" + read(DLPC + "rgbcurrent") + " looks=" + read(DLPC + "looks"));
        double chip = -1;
        if (afterBoot) {
            if (s.color) {
                waitUntil(20.0);
                applyColor("start");
            }
            startModeWatch();
            chip = detectChip();
            if (chip > 0 && s.toLauncher()) toLauncher(chip);
            else if (chip > 0) log("Target HDMI: no switch back, the video chip stays on HDMI");
            if (s.color) applyColor("after start sequence");
        }
        if (!afterBoot) startModeWatch();
        if (s.guard) guard(chip);
        else waitForever();
        log("=== Done");
    }

    private void startModeWatch() {
        Thread t = new Thread(() -> {
            try {
                watchModes();
            } catch (Throwable e) {
                log("Error in mode watch: " + e);
            }
        }, "ppx620modes");
        t.setDaemon(true);
        t.start();
    }

    /** Ohne Absicherung den Dienst trotzdem am Leben halten, damit die Modusverfolgung weiterlaeuft. */
    private static void waitForever() {
        while (true) SystemClock.sleep(60_000);
    }

    /** Wartet auf das Chip-Startereignis (sw 1 -> 0 bei 25-40 s); liefert dessen Zeit oder -1. */
    private double detectChip() {
        boolean seen1 = false;
        String last = "";
        while (true) {
            double t = up();
            String sw = read(SW);
            if (!sw.equals(last)) {
                log("sw=" + sw + " v56=" + v56());
                last = sw;
            }
            if ("1".equals(sw)) seen1 = true;
            if ("0".equals(sw) && seen1 && t >= 25 && t <= 40) {
                log("Chip start event detected (sw 1->0) at " + f2(t) + " s");
                return t;
            }
            if ("0".equals(sw) && !seen1 && t > 30.5) {
                log("sw was already 0 at start; assuming chip start event at 29.9 s");
                return 29.9;
            }
            if (t > 45) {
                log("No chip start event until 45 s (no HDMI device?)");
                return -1;
            }
            SystemClock.sleep(100);
        }
    }

    private void toLauncher(double chip) {
        double target = chip + s.delayMs / 1000.0;
        log("Target launcher: waiting until " + f2(target) + " s (chip event + " + s.delayMs + " ms)");
        waitUntil(target);
        for (int attempt = 1; attempt <= 5; attempt++) {
            String v = v56();
            String sw = read(SW);
            if (!"0".equals(sw)) {
                log("sw=" + sw + ": Android picture already active - no broadcast");
                return;
            }
            if (!"1".equals(v)) {
                log("v56=" + v + ": input menu is closed - left to the unblocking function");
                return;
            }
            double t0 = up();
            log("Broadcast EXT_HDMI_STATUS STATUS=0, attempt " + attempt + " (+" + f2(t0 - chip) + " s after chip event)");
            lastCommand = t0;
            sendReturn();
            boolean delivered = false;
            while (up() - t0 < 3) {
                if ("0".equals(v56())) { delivered = true; break; }
                SystemClock.sleep(50);
            }
            if (!delivered) {
                log("v56 stayed 1: receiver not ready yet - retrying in 3 s");
                SystemClock.sleep(3000);
                continue;
            }
            double t1 = up();
            log("Delivered: v56=0 after " + f2(t1 - t0) + " s");
            if (waitSw1(3)) log("SUCCESS: video chip switched (sw=1) after " + f2(up() - t1) + " s");
            else log("FAILED: video chip did not switch (sw stayed 0) - left to the unblocking function");
            SystemClock.sleep(4500); // Vorhang ~3,5 s abwarten
            return;
        }
        log("Not received after 5 attempts");
    }

    /**
     * Dauerhafte Absicherung gegen die Tastenblockade. Blockiert = Menue zu (v56 != 1) und Bild HDMI (sw=0)
     * seit mindestens 3 s. Dann das Menue wieder oeffnen und den Rueckweg senden, jeweils erst QUIET_S nach dem
     * letzten Befehl an den Chip; hoechstens MAX_RESCUE Versuche je Blockade.
     */
    private void guard(double chip) {
        log("Remote-control unblocking active");
        double stuckSince = -1;
        lastCommand = chip;
        int tries = 0;
        boolean gaveUp = false;
        while (true) {
            boolean stuck = "0".equals(read(SW)) && "0".equals(v56());
            if (!stuck) {
                if (stuckSince >= 0) log("Blockage cleared (sw=" + read(SW) + " v56=" + v56() + ")");
                stuckSince = -1;
                tries = 0;
                gaveUp = false;
            } else if (stuckSince < 0) {
                stuckSince = up();
                // Das Menue hat sich eben geschlossen: sein Rueckweg (z. B. Taste Zurueck) ging kurz vorher an den Chip
                lastCommand = Math.max(lastCommand, stuckSince - MENU_CLOSE_S);
            } else if (up() - stuckSince >= 3 && !gaveUp) {
                if (tries == 0) log("Blockage detected: input menu closed, picture still on HDMI");
                if (tries >= MAX_RESCUE) {
                    log("No success after " + MAX_RESCUE + " attempts - giving up until the state changes");
                    gaveUp = true;
                } else {
                    // Menue braucht ~3 s bis zum Senden; gesendet wird fruehestens QUIET_S nach dem letzten Befehl
                    double open = lastCommand + QUIET_S - 3;
                    if (up() < open) {
                        log("Waiting for the video chip's quiet period: next attempt at " + f2(open + 3) + " s ("
                                + f2(QUIET_S) + " s after the last command at " + f2(lastCommand) + " s)");
                        waitUntil(open);
                    }
                    tries++;
                    if (reopenAndReturn(tries, chip)) {
                        stuckSince = -1;
                        tries = 0;
                    }
                }
            }
            SystemClock.sleep(500);
        }
    }

    /** Eingangsmenue wieder oeffnen und den Rueckweg senden; true bei sw -> 1. */
    private boolean reopenAndReturn(int attempt, double chip) {
        try {
            c.startActivity(new Intent()
                    .setClassName("com.org.moderategale.tvsettings",
                            "com.org.moderategale.inputsourcelibrary.ui.InputSourceActivity")
                    .putExtra("status", 1)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            log("Could not open the input menu: " + e);
            return false;
        }
        SystemClock.sleep(3000);
        if (!"0".equals(read(SW))) {
            log("sw=" + read(SW) + " - picture is Android by now, no broadcast");
            return true;
        }
        double t0 = up();
        lastCommand = t0;
        log("Retry " + attempt + ": input menu reopened, broadcast EXT_HDMI_STATUS STATUS=0"
                + (chip > 0 ? " (+" + f2(t0 - chip) + " s after chip event)" : ""));
        sendReturn();
        if (waitSw1(3)) {
            log("SUCCESS: video chip switched (sw=1) after " + f2(up() - t0) + " s");
            SystemClock.sleep(4500);
            return true;
        }
        log("No switch (sw stayed 0)");
        return false;
    }

    private void sendReturn() {
        Intent i = new Intent("android.intent.action.EXT_HDMI_STATUS");
        i.putExtra("STATUS", 0);
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        c.sendBroadcast(i);
    }

    private static boolean waitSw1(double seconds) {
        double t0 = up();
        while (up() - t0 < seconds) {
            if ("1".equals(read(SW))) return true;
            SystemClock.sleep(50);
        }
        return false;
    }

    /**
     * Alle Zugriffe dieser App auf den DLPC-Treiber laufen nacheinander. Der Treiber setzt beim Schreiben eines
     * Kanals alle drei Stroeme neu und liest dafuer die anderen zurueck; ueberschneidet sich das mit einem Lesezugriff,
     * landen falsche Werte im Chip (gemessen: 1023, 782, 262 statt der geschriebenen Werte).
     */
    static final Object DLPC_LOCK = new Object();

    /** Stroeme {R, G, B, Look} lesen; zwei uebereinstimmende Lesungen, sonst null (Lesefehler des Treibers). */
    static int[] readActive() {
        synchronized (DLPC_LOCK) {
            int[] last = null;
            for (int i = 0; i < 4; i++) {
                int[] v = readOnce();
                if (v != null && last != null && java.util.Arrays.equals(v, last)) return v;
                last = v;
                SystemClock.sleep(60);
            }
            return null;
        }
    }

    private static int[] readOnce() {
        int[] rgb = PowerMode.parse(read(DLPC + "rgbcurrent"));
        if (rgb == null) return null;
        try {
            int look = Integer.parseInt(read(DLPC + "looks"));
            for (int x : rgb) if (x < 0 || x > 1023) return null;
            return new int[]{rgb[0], rgb[1], rgb[2], look};
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmt(int[] v) {
        return v == null ? "unreadable" : v[0] + "/" + v[1] + "/" + v[2] + " look " + v[3];
    }

    /** Farbprofil fuer den aktuellen Modus und Betrieb (Netz/Akku) setzen und nachpruefen. */
    void applyColor(String why) {
        synchronized (DLPC_LOCK) {
            int[] cur = readActive();
            PowerMode.init(c);
            PowerMode.Mode m = PowerMode.fromPreset(cur);
            if (m != null) PowerMode.set(c, m);
            int[] p = PowerMode.profile(s, PowerMode.current);
            String mode = PowerMode.current + (PowerMode.onBattery() ? " battery" : "")
                    + (PowerMode.current == PowerMode.Mode.ECO ? "" : " x" + f2(PowerMode.factor(s, PowerMode.current)));
            if (java.util.Arrays.equals(cur, p)) {
                log("Color (" + why + ", " + mode + "): already set (" + fmt(cur) + ")");
                return;
            }
            log("Color (" + why + ", " + mode + ") before: " + fmt(cur) + ", target " + fmt(p));
            String[] files = {"redcurrent", "greencurrent", "bluecurrent", "looks"};
            for (int attempt = 1; attempt <= 3; attempt++) {
                String err = "";
                // nur die abweichenden Werte schreiben: jeder Schreibvorgang setzt im Treiber alle drei Stroeme neu
                for (int i = 0; i < 4; i++) {
                    if (cur == null || cur[i] != p[i]) {
                        err += write(DLPC + files[i], p[i]);
                        SystemClock.sleep(40);
                    }
                }
                SystemClock.sleep(150);
                cur = readActive();
                if (java.util.Arrays.equals(cur, p)) {
                    log("Color (" + why + ") after: " + fmt(cur) + (attempt > 1 ? " (attempt " + attempt + ")" : "")
                            + (err.isEmpty() ? "" : " ERROR:" + err));
                    return;
                }
                log("Color (" + why + ") readback " + fmt(cur) + " differs from target - writing again");
                cur = null;   // beim naechsten Versuch alle Werte schreiben
            }
            log("Color (" + why + ") could not be set reliably: " + fmt(readActive()));
        }
    }

    /**
     * Energiemodus oder Netzteil gewechselt: dann liegen die Werks-Stroeme an (siehe PowerMode). Sobald sie 1 s
     * stabil sind, das eigene Profil fuer diesen Modus setzen. Laeuft dauerhaft; wirkt nur bei eingeschalteter Farbe.
     */
    void watchModes() {
        log("Following energy modes and power supply");
        String seen = "";
        double since = 0;
        while (true) {
            int[] v = readActive();
            String rgb = fmt(v);
            PowerMode.Mode m = PowerMode.fromPreset(v);
            if (m == null) {
                seen = "";
            } else if (!rgb.equals(seen)) {
                seen = rgb;
                since = up();
            } else if (up() - since >= 1.0) {
                Settings now = Settings.load(c);
                if (now.color) {
                    PowerMode.set(c, m);
                    log("Factory values detected: " + m + (PowerMode.onBattery() ? " battery" : "") + " (" + rgb + ")");
                    new Worker(c).applyColor("mode change");
                }
                seen = "";
                SystemClock.sleep(1500);
            }
            SystemClock.sleep(800);   // selten lesen: jeder Lesezugriff kann mit Schreibvorgaengen kollidieren
        }
    }

    private static void waitUntil(double t) {
        while (up() < t) SystemClock.sleep(100);
    }

    static String read(String path) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String l = r.readLine();
            return l == null ? "" : l.trim();
        } catch (Exception e) {
            return "?(" + e.getClass().getSimpleName() + ")";
        }
    }

    private static String write(String path, int value) {
        try (FileOutputStream out = new FileOutputStream(path)) {
            out.write(Integer.toString(value).getBytes(StandardCharsets.US_ASCII));
            return "";
        } catch (Exception e) {
            return " " + path + ":" + e.getMessage();
        }
    }

    private static Method propGet;

    /** persist.sys.hdmi.v56 lesen: SystemProperties per Reflexion (unter Android 9 erlaubt), sonst getprop. */
    static String v56() {
        try {
            if (propGet == null) propGet = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
            return ((String) propGet.invoke(null, "persist.sys.hdmi.v56")).trim();
        } catch (Throwable t) {
            try {
                Process p = new ProcessBuilder("/system/bin/getprop", "persist.sys.hdmi.v56").redirectErrorStream(true).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String l = r.readLine();
                    p.waitFor();
                    return l == null ? "" : l.trim();
                }
            } catch (Exception e) {
                return "?";
            }
        }
    }
}
