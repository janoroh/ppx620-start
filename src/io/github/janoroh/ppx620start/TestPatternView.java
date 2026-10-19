package io.github.janoroh.ppx620start;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Gezeichnete Testmuster: Farbbalken, Farbtafel (24 Felder nach den veroeffentlichten sRGB-Referenzwerten
 * des ColorChecker Classic), 100 % Schwarz, Ausrichtung, Weiss, dazu Fotos aus assets/testbilder
 * (Unsplash-Lizenz, Quellen in assets/testbilder/CREDITS.txt) oder die Originale der installierten Screeneo-App.
 */
public class TestPatternView extends View {
    enum Kind { OFF, BARS, CHECKER, BLACK, ALIGN, WHITE, ASSET, FILE, SCREENEO }

    static final class Item {
        final Kind kind;
        final String label;
        final String source;

        Item(Kind kind, String label, String source) {
            this.kind = kind;
            this.label = label;
            this.source = source;
        }
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Item> items = new ArrayList<>();
    private int index;
    private Bitmap photo;
    /** true: ganzes Bild zeigen (Vollbild); false: bildfuellend zuschneiden. */
    private boolean fit;

    private static final int[] BARS = {
            Color.rgb(192, 192, 192), Color.rgb(192, 192, 0), Color.rgb(0, 192, 192), Color.rgb(0, 192, 0),
            Color.rgb(192, 0, 192), Color.rgb(192, 0, 0), Color.rgb(0, 0, 192)};
    private static final int[] SKIN = {
            Color.rgb(255, 224, 196), Color.rgb(234, 192, 160), Color.rgb(214, 160, 120),
            Color.rgb(180, 120, 84), Color.rgb(140, 88, 60), Color.rgb(92, 56, 38)};

    public TestPatternView(Context c) {
        super(c);
    }

    void setItems(List<Item> list, int startIndex) {
        items = list;
        setIndex(startIndex);
    }

    int getIndex() {
        return index;
    }

    String getLabel() {
        return items.isEmpty() ? "" : items.get(index).label;
    }

    void setFit(boolean fit) {
        this.fit = fit;
        invalidate();
    }

    void setIndex(int i) {
        if (items.isEmpty()) return;
        index = (i % items.size() + items.size()) % items.size();
        if (photo != null) { photo.recycle(); photo = null; }
        Item it = items.get(index);
        try {
            if (it.kind == Kind.ASSET) {
                try (InputStream in = getContext().getAssets().open("testbilder/" + it.source + ".jpg")) {
                    photo = BitmapFactory.decodeStream(in);
                }
            } else if (it.kind == Kind.SCREENEO) {
                photo = ScreeneoImages.load(getContext(), it.source);
            } else if (it.kind == Kind.FILE) {
                photo = BitmapFactory.decodeFile(new File(it.source).getPath());
            }
        } catch (Exception e) {
            photo = null;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        Kind k = items.isEmpty() ? Kind.OFF : items.get(index).kind;
        switch (k) {
            case OFF:
            case BLACK:
                c.drawColor(Color.BLACK);
                return;
            case WHITE:
                c.drawColor(Color.WHITE);
                return;
            case ALIGN:
                drawAlignment(c, w, h);
                return;
            case BARS:
                drawBars(c, w, h);
                return;
            case CHECKER:
                drawChecker(c, w, h);
                return;
            default:
                c.drawColor(Color.BLACK);
                if (photo == null) return;
                p.setShader(null);
                p.setFilterBitmap(true);
                int pw = photo.getWidth(), ph = photo.getHeight();
                if (fit) {
                    float s = Math.min((float) w / pw, (float) h / ph);
                    int dw = Math.round(pw * s), dh = Math.round(ph * s);
                    c.drawBitmap(photo, null, new Rect((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), p);
                } else {
                    float s = Math.max((float) w / pw, (float) h / ph);
                    int sw = Math.round(w / s), sh = Math.round(h / s);
                    int sx = (pw - sw) / 2, sy = (ph - sh) / 2;
                    c.drawBitmap(photo, new Rect(sx, sy, sx + sw, sy + sh), new Rect(0, 0, w, h), p);
                }
        }
    }

    private void drawBars(Canvas c, int w, int h) {
        p.setShader(null);
        float barH = h * 0.42f, bw = w / (float) BARS.length;
        for (int i = 0; i < BARS.length; i++) {
            p.setColor(BARS[i]);
            c.drawRect(i * bw, 0, (i + 1) * bw, barH, p);
        }
        float y0 = barH, gH = h * 0.14f, gw = w / 11f;
        for (int i = 0; i <= 10; i++) {
            int g = Math.round(255 * i / 10f);
            p.setColor(Color.rgb(g, g, g));
            c.drawRect(i * gw, y0, (i + 1) * gw, y0 + gH, p);
        }
        int[] ends = {Color.RED, Color.GREEN, Color.BLUE, Color.WHITE};
        float y = y0 + gH, vH = h * 0.08f;
        for (int end : ends) {
            p.setShader(new LinearGradient(0, 0, w, 0, Color.BLACK, end, Shader.TileMode.CLAMP));
            c.drawRect(0, y, w, y + vH, p);
            y += vH;
        }
        p.setShader(null);
        float sw = w / (float) SKIN.length;
        for (int i = 0; i < SKIN.length; i++) {
            p.setColor(SKIN[i]);
            c.drawRect(i * sw, y, (i + 1) * sw, h, p);
        }
    }

    /** sRGB-Referenzwerte der 24 Felder des ColorChecker Classic (BabelColor, D50 nach sRGB). */
    private static final int[][] CHECKER = {
            {115, 82, 68}, {194, 150, 130}, {98, 122, 157}, {87, 108, 67}, {133, 128, 177}, {103, 189, 170},
            {214, 126, 44}, {80, 91, 166}, {193, 90, 99}, {94, 60, 108}, {157, 188, 64}, {224, 163, 46},
            {56, 61, 150}, {70, 148, 73}, {175, 54, 60}, {231, 199, 31}, {187, 86, 149}, {8, 133, 161},
            {243, 243, 242}, {200, 200, 200}, {160, 160, 160}, {122, 122, 121}, {85, 85, 85}, {52, 52, 52}};

    private void drawChecker(Canvas c, int w, int h) {
        c.drawColor(Color.BLACK);
        p.setShader(null);
        float gap = Math.min(w, h) * 0.02f;
        float cw = (w - gap * 7) / 6f, ch = (h - gap * 5) / 4f;
        for (int i = 0; i < 24; i++) {
            int col = i % 6, row = i / 6;
            p.setColor(Color.rgb(CHECKER[i][0], CHECKER[i][1], CHECKER[i][2]));
            float x = gap + col * (cw + gap), y = gap + row * (ch + gap);
            c.drawRect(x, y, x + cw, y + ch, p);
        }
    }

    /** Ausrichtungsmuster: Raster, Rahmen, Diagonalen, Kreise in der Mitte und in den Ecken. */
    private void drawAlignment(Canvas c, int w, int h) {
        c.drawColor(Color.BLACK);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.rgb(90, 90, 90));
        p.setStrokeWidth(1);
        int step = Math.max(20, h / 12);
        for (int x = w / 2 % step; x < w; x += step) c.drawLine(x, 0, x, h, p);
        for (int y = h / 2 % step; y < h; y += step) c.drawLine(0, y, w, y, p);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(3);
        c.drawRect(1.5f, 1.5f, w - 1.5f, h - 1.5f, p);
        c.drawLine(w / 2f, 0, w / 2f, h, p);
        c.drawLine(0, h / 2f, w, h / 2f, p);
        p.setStrokeWidth(1.5f);
        c.drawLine(0, 0, w, h, p);
        c.drawLine(w, 0, 0, h, p);
        p.setStrokeWidth(3);
        float r = h * 0.42f;
        c.drawCircle(w / 2f, h / 2f, r, p);
        c.drawCircle(w / 2f, h / 2f, r / 2, p);
        float cr = h * 0.12f;
        c.drawCircle(cr * 1.2f, cr * 1.2f, cr, p);
        c.drawCircle(w - cr * 1.2f, cr * 1.2f, cr, p);
        c.drawCircle(cr * 1.2f, h - cr * 1.2f, cr, p);
        c.drawCircle(w - cr * 1.2f, h - cr * 1.2f, cr, p);
        p.setStyle(Paint.Style.FILL);
    }
}
