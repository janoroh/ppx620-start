package io.github.janoroh.ppx620start;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Protokoll in Logcat (Tag PPX620Start) und in eine Datei im App-Ordner auf /sdcard. */
final class Logbook {
    static final String TAG = "PPX620Start";
    private static final long MAX_BYTES = 512 * 1024;

    static File file(Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        return new File(dir, "protokoll.txt");
    }

    static synchronized void log(Context c, String msg) {
        double up = SystemClock.elapsedRealtime() / 1000.0;
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date())
                + String.format(Locale.ROOT, " up=%7.2f ", up) + msg;
        Log.i(TAG, line);
        try {
            File f = file(c);
            if (f.length() > MAX_BYTES) {
                File old = new File(f.getParentFile(), "protokoll_alt.txt");
                old.delete();
                f.renameTo(old);
            }
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "Protokolldatei: " + e);
        }
    }

    /** Letzte Zeilen der Protokolldatei fuer die Anzeige. */
    static String tail(Context c, int maxBytes) {
        try (RandomAccessFile r = new RandomAccessFile(file(c), "r")) {
            long len = r.length();
            long start = Math.max(0, len - maxBytes);
            r.seek(start);
            byte[] b = new byte[(int) (len - start)];
            r.readFully(b);
            String s = new String(b, StandardCharsets.UTF_8);
            int nl = s.indexOf('\n');
            return start > 0 && nl >= 0 ? s.substring(nl + 1) : s;
        } catch (Exception e) {
            return "(noch kein Protokoll)";
        }
    }
}
