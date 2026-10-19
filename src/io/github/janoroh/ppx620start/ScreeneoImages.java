package io.github.janoroh.ppx620start;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * Liest die Original-Testbilder der installierten Screeneo-Farb-App. Sie liegen als Qt-Ressourcen (rcc) in deren
 * nativer Bibliothek; diese App liefert davon nichts aus.
 * rcc-Aufbau: Baum (Eintraege zu 22 Byte: Namensoffset u32, Flags u16, bei Ordnern Anzahl u32 + erstes Kind u32,
 * bei Dateien Land u16 + Sprache u16 + Datenoffset u32; danach Zeitstempel u64), Namen (Laenge u16, Hash u32,
 * UTF-16BE), Daten (Laenge u32 + Bytes; Flag 1 = zlib mit vorangestellter Groesse u32).
 */
final class ScreeneoImages {
    static final String PKG = "com.screeneo.app.ColorAdjustment";
    private static final String LIB = "libscreeneo-color-adjustment_arm64-v8a.so";
    private static final String DIR = "testpatterns";
    private static final int ENTRY = 22;
    private static final int RANGE = 2_000_000;

    /** Name ("A1 Skintone.jpg") -> {Position des Laengenfelds, Flags}. */
    private static Map<String, long[]> index;
    private static MappedByteBuffer buf;
    private static boolean tried;

    private ScreeneoImages() {
    }

    /** Leer, wenn die Screeneo-App fehlt oder ihre Bibliothek anders aufgebaut ist. */
    static synchronized Map<String, long[]> index(Context c) {
        if (tried) return index;
        tried = true;
        Map<String, long[]> found = null;
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(PKG, 0);
            File f = new File(ai.nativeLibraryDir, LIB);
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                buf = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, raf.length());
            }
            found = parse(buf);
            Logbook.log(c, found == null ? "Screeneo test images: library not recognized"
                    : "Screeneo test images: " + found.size() + " found");
        } catch (Exception e) {
            Logbook.log(c, "Screeneo test images unavailable: " + e);
        }
        index = found != null ? found : new LinkedHashMap<>();
        return index;
    }

    static Bitmap load(Context c, String name) {
        long[] e = index(c).get(name);
        if (e == null) return null;
        try {
            ByteBuffer b = buf.duplicate();
            int pos = (int) e[0];
            byte[] data = new byte[(int) u32(b, pos)];
            b.position(pos + 4);
            b.get(data);
            if (e[1] == 1) data = inflate(data);
            return data == null ? null : BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception | OutOfMemoryError ex) {
            return null;
        }
    }

    private static Map<String, long[]> parse(ByteBuffer b) {
        byte[] key = DIR.getBytes(StandardCharsets.UTF_16BE);
        int at = find(b, key, 0);
        while (at >= 6 && u16(b, at - 6) != DIR.length()) at = find(b, key, at + 1);
        if (at < 6) return null;
        int name = at - 6;
        int lo = Math.max(0, name - RANGE), hi = Math.min(b.limit() - 16, name + RANGE);
        for (int q = lo; q < hi; q++) {
            // Ordner-Eintrag "testpatterns" im Baum: Namensoffset = name - Namensbasis
            if (u16(b, q + 4) != 2) continue;
            long x = u32(b, q);
            if (x == 0 || x > name || name - x > 1_000_000) continue;
            int base = (int) (name - x);
            if (readName(b, base) == null) continue;
            long count = u32(b, q + 6), first = u32(b, q + 10);
            if (count < 1 || count > 200 || first > 100_000) continue;
            // Baumanfang: Wurzel mit Namensoffset 0 und Ordner-Flag; Kinder muessen Bilddateien sein
            for (int t = q - ENTRY; t >= 0 && t >= q - ENTRY * 3000; t -= ENTRY) {
                if (u32(b, t) != 0 || u16(b, t + 4) != 2) continue;
                List<Object[]> files = children(b, t, base, (int) first, (int) count);
                if (files != null) return withData(b, files, base);
            }
        }
        return null;
    }

    private static List<Object[]> children(ByteBuffer b, int tree, int base, int first, int count) {
        List<Object[]> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long e = tree + (long) ENTRY * (first + i);
            if (e + ENTRY > b.limit()) return null;
            String n = readName(b, base + u32(b, (int) e));
            if (n == null) return null;
            String l = n.toLowerCase();
            if (!l.endsWith(".jpg") && !l.endsWith(".jpeg") && !l.endsWith(".png")) return null;
            out.add(new Object[]{n, u16(b, (int) e + 4), u32(b, (int) e + 10)});
        }
        return out;
    }

    /** Datenbasis suchen: alle unkomprimierten Bilder muessen dort mit JPEG- oder PNG-Kennung beginnen. */
    private static Map<String, long[]> withData(ByteBuffer b, List<Object[]> files, int from) {
        long minOff = Long.MAX_VALUE;
        for (Object[] f : files) if ((Integer) f[1] == 0) minOff = Math.min(minOff, (Long) f[2]);
        if (minOff == Long.MAX_VALUE) return null;
        for (int p = from; p < b.limit() - 4; p++) {
            if (!magic(b, p)) continue;
            long dataBase = p - 4 - minOff;
            if (dataBase < 0) continue;
            Map<String, long[]> out = new LinkedHashMap<>();
            boolean ok = true;
            for (Object[] f : files) {
                long pos = dataBase + (Long) f[2];
                if (pos + 8 > b.limit()) { ok = false; break; }
                long len = u32(b, (int) pos);
                if (len < 8 || pos + 4 + len > b.limit() || ((Integer) f[1] == 0 && !magic(b, (int) pos + 4))) { ok = false; break; }
                out.put((String) f[0], new long[]{pos, (Integer) f[1]});
            }
            if (ok) return out;
        }
        return null;
    }

    private static boolean magic(ByteBuffer b, int p) {
        int a = b.get(p) & 0xff, c = b.get(p + 1) & 0xff, d = b.get(p + 2) & 0xff;
        return (a == 0xff && c == 0xd8 && d == 0xff) || (a == 0x89 && c == 'P' && d == 'N' && b.get(p + 3) == 'G');
    }

    private static String readName(ByteBuffer b, long p) {
        if (p < 0 || p + 6 > b.limit()) return null;
        int n = u16(b, (int) p);
        if (n < 1 || n > 120 || p + 6 + 2L * n > b.limit()) return null;
        StringBuilder s = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char ch = b.getChar((int) p + 6 + 2 * i);
            if (ch < 32 || ch > 126) return null;
            s.append(ch);
        }
        return s.toString();
    }

    private static int find(ByteBuffer b, byte[] pat, int from) {
        int end = b.limit() - pat.length;
        outer:
        for (int i = Math.max(0, from); i <= end; i++) {
            for (int j = 0; j < pat.length; j++) if (b.get(i + j) != pat[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static byte[] inflate(byte[] z) {
        if (z.length < 4) return null;
        int size = (z[0] & 0xff) << 24 | (z[1] & 0xff) << 16 | (z[2] & 0xff) << 8 | (z[3] & 0xff);
        Inflater inf = new Inflater();
        try {
            inf.setInput(z, 4, z.length - 4);
            byte[] out = new byte[size];
            int n = inf.inflate(out);
            return n == size ? out : null;
        } catch (Exception e) {
            return null;
        } finally {
            inf.end();
        }
    }

    private static int u16(ByteBuffer b, int p) {
        return b.getShort(p) & 0xffff;
    }

    private static long u32(ByteBuffer b, int p) {
        return b.getInt(p) & 0xffffffffL;
    }
}
