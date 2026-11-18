package com.rumpa.gemduel;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.HashMap;
import java.util.Random;

/**
 * Bright glossy "candy shop" pieces, rendered once into bitmaps.
 * t: 0 red jelly bean, 1 orange lozenge, 2 yellow lemon drop, 3 green gummy cube,
 * 4 blue lollipop, 5 purple rosette, 6 color bomb, 7 cherry, 8 choco block (spv = hit points),
 * 9 treasure chest.
 * spv for candies: 0 normal, 1 striped (clears row), 2 striped (clears column), 3 wrapped,
 * 5 lightning (clears 3 rows + 3 columns), 6 super gem (huge area blast).
 */
public class Gems {
    public static final int[] COL = {0xFFFF3B47, 0xFFFF9F1A, 0xFFFFE53B, 0xFF3FD95B, 0xFF3C8CFF, 0xFFB65CFF};
    static final int SZ = 160;
    static final HashMap<Integer, Bitmap> cache = new HashMap<>();
    static final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    static final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    static final RectF dst = new RectF();

    static {
        flashPaint.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_ATOP));
    }

    public static int lighten(int c, float f) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        r += (int) ((255 - r) * f); g += (int) ((255 - g) * f); b += (int) ((255 - b) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int darken(int c, float f) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        r = (int) (r * (1 - f)); g = (int) (g * (1 - f)); b = (int) (b * (1 - f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** rad = radius of the candy body in canvas pixels. */
    public static void draw(Canvas cv, Paint p, int t, int spv, float cx, float cy, float rad, boolean flash) {
        Bitmap b = get(t, spv);
        float half = rad / 0.38f * 0.5f;
        dst.set(cx - half, cy - half, cx + half, cy + half);
        cv.drawBitmap(b, null, dst, flash ? flashPaint : bmpPaint);
    }

    /** Same as draw() but translucent (used for faint decorations). */
    public static void drawAlpha(Canvas cv, int t, int spv, float cx, float cy, float rad, int alpha) {
        Bitmap b = get(t, spv);
        float half = rad / 0.38f * 0.5f;
        dst.set(cx - half, cy - half, cx + half, cy + half);
        bmpPaint.setAlpha(alpha);
        cv.drawBitmap(b, null, dst, bmpPaint);
        bmpPaint.setAlpha(255);
    }

    static Bitmap get(int t, int spv) {
        int key = t * 16 + spv;
        Bitmap b = cache.get(key);
        if (b == null) {
            b = render(t, spv);
            cache.put(key, b);
        }
        return b;
    }

    static Bitmap render(int t, int spv) {
        Bitmap bm = Bitmap.createBitmap(SZ, SZ, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = SZ / 2f, cy = SZ / 2f, r = SZ * 0.38f;
        if (t == 6) bomb(c, p, cx, cy, r);
        else if (t == 7) cherry(c, p, cx, cy, r);
        else if (t == 8) block(c, p, cx, cy, r, spv);
        else if (t == 9) chest(c, p, cx, cy, r);
        else candy(c, p, t, spv, cx, cy, spv == 3 ? r * 0.86f : r);
        return bm;
    }

    static Path rounded(Path src, float rad) {
        Paint ep = new Paint();
        ep.setStyle(Paint.Style.FILL);
        ep.setPathEffect(new CornerPathEffect(rad));
        Path out = new Path();
        ep.getFillPath(src, out);
        return out;
    }

    /** Gummy fish facing right (the candy colour is the fish colour). */
    static Path fishShape(float cx, float cy, float r) {
        Path s = new Path();
        s.addOval(new RectF(cx - r * 0.78f, cy - r * 0.62f, cx + r * 0.7f, cy + r * 0.62f), Path.Direction.CW);
        s.moveTo(cx - r * 0.55f, cy);
        s.lineTo(cx - r * 1.2f, cy - r * 0.62f);
        s.lineTo(cx - r * 1.2f, cy + r * 0.62f);
        s.close();
        s.moveTo(cx - r * 0.1f, cy - r * 0.5f);
        s.lineTo(cx + r * 0.3f, cy - r * 1.0f);
        s.lineTo(cx + r * 0.5f, cy - r * 0.45f);
        s.close();
        Matrix m = new Matrix();
        m.postRotate(-12, cx, cy);
        s.transform(m);
        return rounded(s, r * 0.22f);
    }

    static Path shape(int t, float cx, float cy, float r) {
        Path s = new Path();
        Matrix m = new Matrix();
        switch (t) {
            case 0: // jelly bean
                s.addRoundRect(new RectF(cx - r * 1.1f, cy - r * 0.72f, cx + r * 1.1f, cy + r * 0.72f), r * 0.72f, r * 0.72f, Path.Direction.CW);
                m.postRotate(-32, cx, cy);
                s.transform(m);
                return s;
            case 1: // lozenge
                s.addCircle(cx, cy, r * 1.02f, Path.Direction.CW);
                return s;
            case 2: // lemon drop
                s.moveTo(cx - r * 1.2f, cy);
                s.cubicTo(cx - r * 0.55f, cy - r * 1.1f, cx + r * 0.55f, cy - r * 1.1f, cx + r * 1.2f, cy);
                s.cubicTo(cx + r * 0.55f, cy + r * 1.1f, cx - r * 0.55f, cy + r * 1.1f, cx - r * 1.2f, cy);
                s.close();
                m.postRotate(-26, cx, cy);
                s.transform(m);
                return s;
            case 3: // gummy cube
                s.addRect(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f, Path.Direction.CW);
                return rounded(s, r * 0.45f);
            case 4: // lollipop head
                s.addCircle(cx, cy, r * 1.02f, Path.Direction.CW);
                return s;
            default: // rosette
                s.addCircle(cx, cy, r * 0.72f, Path.Direction.CW);
                for (int i = 0; i < 7; i++) {
                    double a = -Math.PI / 2 + i * 2 * Math.PI / 7;
                    s.addCircle(cx + (float) Math.cos(a) * r * 0.62f, cy + (float) Math.sin(a) * r * 0.62f, r * 0.44f, Path.Direction.CW);
                }
                return s;
        }
    }

    static void gloss(Canvas c, Paint p, float cx, float cy, float r) {
        RectF hl = new RectF(cx - r * 0.66f, cy - r * 0.86f, cx + r * 0.02f, cy - r * 0.28f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(hl.left, hl.top, hl.right, hl.bottom, 0xF5FFFFFF, 0x18FFFFFF, Shader.TileMode.CLAMP));
        c.drawOval(hl, p);
        p.setShader(null);
        p.setColor(0x40FFFFFF);
        c.drawOval(new RectF(cx + r * 0.18f, cy + r * 0.42f, cx + r * 0.72f, cy + r * 0.74f), p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx - r * 0.5f, cy - r * 0.58f, r * 0.07f, p);
    }

    static void ovalShadow(Canvas c, Paint p, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        p.setMaskFilter(new BlurMaskFilter(r * 0.12f, BlurMaskFilter.Blur.NORMAL));
        c.drawOval(new RectF(cx - r * 0.85f, cy + r * 0.7f, cx + r * 0.85f, cy + r * 1.2f), p);
        p.setMaskFilter(null);
    }

    static void candy(Canvas c, Paint p, int t, int spv, float cx, float cy, float r) {
        int base = COL[t];
        Path body = spv == 4 ? fishShape(cx, cy, r) : shape(t, cx, cy, r);

        if (spv == 3) { // wrapper ends
            Path ends = new Path();
            ends.moveTo(cx - r * 0.7f, cy);
            ends.lineTo(cx - r * 1.55f, cy - r * 0.6f);
            ends.lineTo(cx - r * 1.55f, cy + r * 0.6f);
            ends.close();
            ends.moveTo(cx + r * 0.7f, cy);
            ends.lineTo(cx + r * 1.55f, cy - r * 0.6f);
            ends.lineTo(cx + r * 1.55f, cy + r * 0.6f);
            ends.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(0, cy - r * 0.6f, 0, cy + r * 0.6f, lighten(base, 0.6f), base, Shader.TileMode.CLAMP));
            c.drawPath(ends, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.08f);
            p.setColor(0xEEFFFFFF);
            c.drawPath(ends, p);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        p.setMaskFilter(new BlurMaskFilter(r * 0.13f, BlurMaskFilter.Blur.NORMAL));
        c.save();
        c.translate(0, r * 0.16f);
        c.drawPath(body, p);
        c.restore();
        p.setMaskFilter(null);

        p.setColor(0xFFFFFFFF); p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.45f, r * 1.8f,
                new int[]{lighten(base, 0.72f), base, darken(base, 0.36f)}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(body, p);
        p.setShader(null);

        c.save();
        c.clipPath(body);
        // type details
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        if (spv == 4) {
            p.setStrokeWidth(r * 0.1f);
            p.setColor(0x77FFFFFF);
            c.drawArc(new RectF(cx - r * 0.2f, cy - r * 0.5f, cx + r * 0.45f, cy + r * 0.5f), 120f, 120f, false, p);
            c.drawArc(new RectF(cx - r * 0.6f, cy - r * 0.45f, cx + r * 0.05f, cy + r * 0.45f), 120f, 120f, false, p);
        } else if (t == 1) {
            p.setStrokeWidth(r * 0.14f);
            p.setColor(0xB0FFE0B2);
            c.drawCircle(cx, cy, r * 0.62f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF); p.setShader(new RadialGradient(cx - r * 0.1f, cy - r * 0.1f, r * 0.4f, lighten(base, 0.6f), darken(base, 0.1f), Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r * 0.36f, p);
            p.setShader(null);
        } else if (t == 4) {
            p.setStrokeWidth(r * 0.14f);
            p.setColor(0xCCFFFFFF);
            for (int k = 0; k < 3; k++) {
                float rr = r * (0.2f + 0.24f * k);
                c.drawArc(new RectF(cx - rr, cy - rr, cx + rr, cy + rr), k * 80f, 270f, false, p);
            }
        } else if (t == 3) {
            p.setStrokeWidth(r * 0.1f);
            p.setColor(0x88FFFFFF);
            c.drawRoundRect(new RectF(cx - r * 0.5f, cy - r * 0.5f, cx + r * 0.5f, cy + r * 0.5f), r * 0.25f, r * 0.25f, p);
        } else if (t == 5) {
            p.setStrokeWidth(r * 0.08f);
            p.setColor(0x66FFFFFF);
            c.drawCircle(cx, cy, r * 0.32f, p);
        } else if (t == 2) {
            p.setStrokeWidth(r * 0.08f);
            p.setColor(0x66FFFFFF);
            c.drawLine(cx - r * 0.8f, cy + r * 0.2f, cx + r * 0.8f, cy - r * 0.2f, p);
        }
        if (spv == 5) { // lightning bolt
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setShadowLayer(r * 0.18f, 0, 0, 0xFFFFEB3B);
            Path bolt = new Path();
            bolt.moveTo(cx + r * 0.28f, cy - r * 0.85f);
            bolt.lineTo(cx - r * 0.38f, cy + r * 0.05f);
            bolt.lineTo(cx - r * 0.02f, cy + r * 0.05f);
            bolt.lineTo(cx - r * 0.3f, cy + r * 0.85f);
            bolt.lineTo(cx + r * 0.42f, cy - r * 0.15f);
            bolt.lineTo(cx + r * 0.04f, cy - r * 0.15f);
            bolt.close();
            c.drawPath(bolt, p);
            p.clearShadowLayer();
        }
        if (spv == 6) { // super gem starburst
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(r * 0.16f);
            p.setColor(0xF0FFFFFF);
            p.setShadowLayer(r * 0.2f, 0, 0, 0xFFFFEB3B);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + Math.PI / 8;
                c.drawLine(cx + (float) Math.cos(a) * r * 0.3f, cy + (float) Math.sin(a) * r * 0.3f,
                        cx + (float) Math.cos(a) * r * 0.78f, cy + (float) Math.sin(a) * r * 0.78f, p);
            }
            p.clearShadowLayer();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, r * 0.2f, p);
        }
        // stripes
        p.setStyle(Paint.Style.FILL);
        if (spv == 1 || spv == 2) {
            p.setColor(0xE6FFFFFF);
            for (int k = -2; k <= 2; k++) {
                if (spv == 1) c.drawRect(cx - r * 1.4f, cy + k * r * 0.46f - r * 0.1f, cx + r * 1.4f, cy + k * r * 0.46f + r * 0.1f, p);
                else c.drawRect(cx + k * r * 0.46f - r * 0.1f, cy - r * 1.4f, cx + k * r * 0.46f + r * 0.1f, cy + r * 1.4f, p);
            }
        }
        // bounce light
        p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(0, cy + r * 0.1f, 0, cy + r * 1.1f, 0x00FFFFFF, 0x55FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(cx - r * 1.4f, cy + r * 0.1f, cx + r * 1.4f, cy + r * 1.3f, p);
        p.setShader(null);
        c.restore();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.06f);
        p.setColor(0x77000000 | (darken(base, 0.45f) & 0xFFFFFF));
        c.drawPath(body, p);

        if (spv == 3) {
            p.setStrokeWidth(r * 0.1f);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, r * 1.2f, p);
        }
        if (spv == 4) { // eye
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx + r * 0.38f, cy - r * 0.2f, r * 0.2f, p);
            p.setColor(0xFF1A1030);
            c.drawCircle(cx + r * 0.44f, cy - r * 0.18f, r * 0.1f, p);
        }
        gloss(c, p, cx, cy, r);
    }

    static void bomb(Canvas c, Paint p, float cx, float cy, float r) {
        ovalShadow(c, p, cx, cy, r);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF); p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.8f,
                new int[]{0xFFB48A78, 0xFF6D4C41, 0xFF2A1812}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.04f, p);
        p.setShader(null);
        Random rn = new Random(7);
        for (int i = 0; i < 18; i++) {
            double a = rn.nextDouble() * Math.PI * 2, d = Math.sqrt(rn.nextDouble()) * r * 0.82;
            float x = cx + (float) (Math.cos(a) * d), y = cy + (float) (Math.sin(a) * d);
            p.setColor(COL[i % 6]);
            c.save();
            c.rotate(rn.nextInt(180), x, y);
            c.drawRoundRect(new RectF(x - r * 0.15f, y - r * 0.055f, x + r * 0.15f, y + r * 0.055f), r * 0.05f, r * 0.05f, p);
            c.restore();
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.05f);
        p.setColor(0x88000000);
        c.drawCircle(cx, cy, r * 1.04f, p);
        gloss(c, p, cx, cy, r);
    }

    static void cherry(Canvas c, Paint p, float cx, float cy, float r) {
        float[] xs = {cx - r * 0.38f, cx + r * 0.38f};
        float yy = cy + r * 0.38f, rr = r * 0.5f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        p.setMaskFilter(new BlurMaskFilter(r * 0.1f, BlurMaskFilter.Blur.NORMAL));
        for (float x : xs) c.drawOval(new RectF(x - rr, yy + rr * 0.8f, x + rr, yy + rr * 1.35f), p);
        p.setMaskFilter(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.11f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF2E9E3C);
        Path stem = new Path();
        stem.moveTo(xs[0], yy - rr * 0.7f);
        stem.quadTo(cx - r * 0.1f, cy - r * 0.6f, cx + r * 0.15f, cy - r * 0.9f);
        stem.moveTo(xs[1], yy - rr * 0.7f);
        stem.quadTo(cx + r * 0.3f, cy - r * 0.5f, cx + r * 0.15f, cy - r * 0.9f);
        c.drawPath(stem, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(cx, cy - r, cx + r * 0.5f, cy - r * 0.6f, 0xFFB9F6CA, 0xFF2E9E3C, Shader.TileMode.CLAMP));
        Path leaf = new Path();
        leaf.moveTo(cx + r * 0.15f, cy - r * 0.9f);
        leaf.quadTo(cx + r * 0.6f, cy - r * 1.05f, cx + r * 0.75f, cy - r * 0.65f);
        leaf.quadTo(cx + r * 0.4f, cy - r * 0.6f, cx + r * 0.15f, cy - r * 0.9f);
        c.drawPath(leaf, p);
        p.setShader(null);
        for (float x : xs) {
            p.setColor(0xFFFFFFFF); p.setShader(new RadialGradient(x - rr * 0.35f, yy - rr * 0.4f, rr * 1.7f,
                    new int[]{0xFFFF9AA2, 0xFFFF1744, 0xFF9B0020}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, yy, rr, p);
            p.setShader(null);
            p.setColor(0xDDFFFFFF);
            c.drawOval(new RectF(x - rr * 0.6f, yy - rr * 0.7f, x - rr * 0.1f, yy - rr * 0.3f), p);
        }
    }

    static void chest(Canvas c, Paint p, float cx, float cy, float r) {
        ovalShadow(c, p, cx, cy, r);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, cy - r, 0, cy + r, 0xFFE8B063, 0xFF8D5A2B, Shader.TileMode.CLAMP));
        RectF body = new RectF(cx - r * 0.95f, cy - r * 0.25f, cx + r * 0.95f, cy + r * 0.9f);
        c.drawRoundRect(body, r * 0.14f, r * 0.14f, p);
        p.setShader(new LinearGradient(0, cy - r, 0, cy - r * 0.1f, 0xFFF2C98A, 0xFFB5793A, Shader.TileMode.CLAMP));
        RectF lid = new RectF(cx - r * 0.95f, cy - r * 0.95f, cx + r * 0.95f, cy - r * 0.15f);
        c.drawRoundRect(lid, r * 0.42f, r * 0.42f, p);
        p.setShader(null);
        p.setColor(0xFFFFD54F);
        c.drawRoundRect(new RectF(cx - r * 1.02f, cy - r * 0.32f, cx + r * 1.02f, cy - r * 0.08f), r * 0.06f, r * 0.06f, p);
        p.setColor(0xFF8A5A00);
        c.drawRoundRect(new RectF(cx - r * 0.2f, cy - r * 0.4f, cx + r * 0.2f, cy + r * 0.3f), r * 0.08f, r * 0.08f, p);
        p.setColor(0xFFFFEB3B);
        c.drawCircle(cx, cy - r * 0.05f, r * 0.09f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.06f);
        p.setColor(0xFF4A2C10);
        c.drawRoundRect(body, r * 0.14f, r * 0.14f, p);
        c.drawRoundRect(lid, r * 0.42f, r * 0.42f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66FFFFFF);
        c.drawRoundRect(new RectF(cx - r * 0.7f, cy - r * 0.85f, cx - r * 0.15f, cy - r * 0.45f), r * 0.2f, r * 0.2f, p);
        p.setColor(0x55FFFFFF);
        c.drawRoundRect(new RectF(cx - r * 0.75f, cy - r * 0.05f, cx + r * 0.4f, cy + r * 0.15f), r * 0.08f, r * 0.08f, p);
    }

    static void block(Canvas c, Paint p, float cx, float cy, float r, int hp) {
        int base = hp >= 3 ? 0xFF6D4C41 : hp == 2 ? 0xFF8D6E63 : 0xFFBCAAA4;
        float h = r * 0.98f;
        RectF b = new RectF(cx - h, cy - h, cx + h, cy + h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        p.setMaskFilter(new BlurMaskFilter(r * 0.12f, BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(new RectF(b.left, b.top + r * 0.15f, b.right, b.bottom + r * 0.15f), r * 0.25f, r * 0.25f, p);
        p.setMaskFilter(null);
        p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(0, b.top, 0, b.bottom, lighten(base, 0.35f), darken(base, 0.35f), Shader.TileMode.CLAMP));
        c.drawRoundRect(b, r * 0.25f, r * 0.25f, p);
        p.setShader(null);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                float x0 = cx - h * 0.84f + i * h * 0.86f, y0 = cy - h * 0.84f + j * h * 0.86f;
                RectF q = new RectF(x0, y0, x0 + h * 0.82f, y0 + h * 0.82f);
                p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(q.left, q.top, q.right, q.bottom, lighten(base, 0.4f), darken(base, 0.2f), Shader.TileMode.CLAMP));
                c.drawRoundRect(q, r * 0.1f, r * 0.1f, p);
                p.setShader(null);
            }
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.06f);
        p.setColor(0xFF3A2218);
        c.drawRoundRect(b, r * 0.25f, r * 0.25f, p);
        if (hp <= 2) {
            p.setStrokeWidth(r * 0.08f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xEE2A160C);
            Path cr = new Path();
            cr.moveTo(cx - h * 0.5f, cy - h * 0.9f);
            cr.lineTo(cx - h * 0.15f, cy - h * 0.3f);
            cr.lineTo(cx - h * 0.45f, cy + h * 0.1f);
            cr.lineTo(cx, cy + h * 0.5f);
            if (hp == 1) {
                cr.moveTo(cx + h * 0.6f, cy - h * 0.8f);
                cr.lineTo(cx + h * 0.2f, cy - h * 0.2f);
                cr.lineTo(cx + h * 0.55f, cy + h * 0.25f);
                cr.lineTo(cx + h * 0.15f, cy + h * 0.9f);
            }
            c.drawPath(cr, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55FFFFFF);
        c.drawRoundRect(new RectF(b.left + r * 0.12f, b.top + r * 0.08f, b.right - r * 0.12f, b.top + r * 0.5f), r * 0.2f, r * 0.2f, p);
    }
}
