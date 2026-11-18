package com.rumpa.gemduel;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Small glossy cartoon icons drawn with canvas (no image files needed). r = half size. */
public class Icons {
    public static final int HAMMER = 0, SHUFFLE = 1, COIN = 2, HEART = 3, GIFT = 4, WHEEL = 5, SHOP = 6,
            TROPHY = 7, GEAR = 8, FRIENDS = 9, SOUND = 10, SOUND_OFF = 11, HELP = 12, PLUS = 13, STAR = 14,
            MOVES = 15, PIN = 16, CROSS = 17, PALETTE = 18, CHEST = 19;

    static final RectF rc = new RectF();
    static final Path pa = new Path();

    static void rad(Paint p, float x, float y, float r, int base) {
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.4f, r * 1.7f,
                new int[]{Gems.lighten(base, 0.6f), base, Gems.darken(base, 0.4f)}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    static void lin(Paint p, float y0, float y1, int a, int b) {
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, y0, 0, y1, a, b, Shader.TileMode.CLAMP));
    }

    static void outline(Canvas c, Paint p, Path path, int col, float w) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(col);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    static void arrow(Canvas c, Paint p, float x0, float y0, float x1, float y1, float w) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFFFFFFFF);
        c.drawLine(x0, y0, x1, y1, p);
        double a = Math.atan2(y1 - y0, x1 - x0);
        float hl = w * 2.2f;
        p.setStyle(Paint.Style.FILL);
        pa.reset();
        pa.moveTo(x1 + (float) Math.cos(a) * hl * 0.5f, y1 + (float) Math.sin(a) * hl * 0.5f);
        pa.lineTo(x1 + (float) Math.cos(a + 2.5) * hl, y1 + (float) Math.sin(a + 2.5) * hl);
        pa.lineTo(x1 + (float) Math.cos(a - 2.5) * hl, y1 + (float) Math.sin(a - 2.5) * hl);
        pa.close();
        c.drawPath(pa, p);
    }

    static final java.util.HashMap<Integer, android.graphics.Bitmap> cache = new java.util.HashMap<>();
    static final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    static final RectF dstR = new RectF();
    static final int ISZ = 128;
    static final float IR = 40f;

    /** Draws a cached bitmap of the icon (rendered once). r = half size on screen. */
    public static void draw(Canvas c, Paint p, int id, float cx, float cy, float r) {
        android.graphics.Bitmap b = cache.get(id);
        if (b == null) {
            b = android.graphics.Bitmap.createBitmap(ISZ, ISZ, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas bc = new Canvas(b);
            Paint bpaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            drawRaw(bc, bpaint, id, ISZ / 2f, ISZ / 2f, IR);
            cache.put(id, b);
        }
        float half = ISZ / 2f * (r / IR);
        dstR.set(cx - half, cy - half, cx + half, cy + half);
        c.drawBitmap(b, null, dstR, bp);
    }

    static void drawRaw(Canvas c, Paint p, int id, float cx, float cy, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.ROUND);
        switch (id) {
            case HAMMER: {
                c.save();
                c.rotate(-40, cx, cy);
                lin(p, cy - r * 0.2f, cy + r, 0xFFD7A86E, 0xFF8D5A2B);
                rc.set(cx - r * 0.16f, cy - r * 0.2f, cx + r * 0.16f, cy + r * 1.05f);
                c.drawRoundRect(rc, r * 0.14f, r * 0.14f, p);
                lin(p, cy - r, cy - r * 0.1f, 0xFFFF8FB8, 0xFFE91E63);
                rc.set(cx - r * 0.7f, cy - r * 0.98f, cx + r * 0.7f, cy - r * 0.12f);
                c.drawRoundRect(rc, r * 0.26f, r * 0.26f, p);
                p.setShader(null);
                p.setColor(0x66FFFFFF);
                rc.set(cx - r * 0.55f, cy - r * 0.9f, cx + r * 0.5f, cy - r * 0.6f);
                c.drawRoundRect(rc, r * 0.14f, r * 0.14f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.07f);
                p.setColor(0xFF8E1040);
                rc.set(cx - r * 0.7f, cy - r * 0.98f, cx + r * 0.7f, cy - r * 0.12f);
                c.drawRoundRect(rc, r * 0.26f, r * 0.26f, p);
                c.restore();
                break;
            }
            case SHUFFLE: {
                arrow(c, p, cx - r * 0.7f, cy + r * 0.45f, cx + r * 0.72f, cy - r * 0.45f, r * 0.2f);
                arrow(c, p, cx - r * 0.7f, cy - r * 0.45f, cx + r * 0.72f, cy + r * 0.45f, r * 0.2f);
                break;
            }
            case COIN: {
                rad(p, cx, cy, r, 0xFFFFC107);
                c.drawCircle(cx, cy, r, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.12f);
                p.setColor(0xFFB77900);
                c.drawCircle(cx, cy, r * 0.88f, p);
                p.setStrokeWidth(r * 0.08f);
                p.setColor(0xFFFFE57F);
                c.drawCircle(cx, cy, r * 0.62f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFF3C4);
                pa.reset();
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = (i % 2 == 0) ? r * 0.42f : r * 0.2f;
                    float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                    if (i == 0) pa.moveTo(x, y); else pa.lineTo(x, y);
                }
                pa.close();
                c.drawPath(pa, p);
                break;
            }
            case HEART: {
                pa.reset();
                pa.moveTo(cx, cy + r * 0.95f);
                pa.cubicTo(cx - r * 1.45f, cy + r * 0.1f, cx - r * 0.95f, cy - r * 1.0f, cx, cy - r * 0.3f);
                pa.cubicTo(cx + r * 0.95f, cy - r * 1.0f, cx + r * 1.45f, cy + r * 0.1f, cx, cy + r * 0.95f);
                pa.close();
                rad(p, cx, cy, r * 1.1f, 0xFFFF2D55);
                c.drawPath(pa, p);
                outline(c, p, pa, 0xFF8E0A2A, r * 0.08f);
                p.setColor(0xCCFFFFFF);
                rc.set(cx - r * 0.75f, cy - r * 0.7f, cx - r * 0.25f, cy - r * 0.35f);
                c.drawOval(rc, p);
                break;
            }
            case GIFT: {
                lin(p, cy - r * 0.1f, cy + r * 0.9f, 0xFFFF6B8B, 0xFFD81B60);
                rc.set(cx - r * 0.8f, cy - r * 0.1f, cx + r * 0.8f, cy + r * 0.9f);
                c.drawRoundRect(rc, r * 0.12f, r * 0.12f, p);
                lin(p, cy - r * 0.55f, cy - r * 0.05f, 0xFFFF8FA9, 0xFFE91E63);
                rc.set(cx - r * 0.92f, cy - r * 0.55f, cx + r * 0.92f, cy - r * 0.05f);
                c.drawRoundRect(rc, r * 0.12f, r * 0.12f, p);
                p.setShader(null);
                p.setColor(0xFFFFD54F);
                c.drawRect(cx - r * 0.14f, cy - r * 0.55f, cx + r * 0.14f, cy + r * 0.9f, p);
                rc.set(cx - r * 0.6f, cy - r * 1.0f, cx - r * 0.02f, cy - r * 0.5f);
                c.drawOval(rc, p);
                rc.set(cx + r * 0.02f, cy - r * 1.0f, cx + r * 0.6f, cy - r * 0.5f);
                c.drawOval(rc, p);
                p.setColor(0xFFFFB300);
                c.drawCircle(cx, cy - r * 0.55f, r * 0.14f, p);
                break;
            }
            case WHEEL: {
                int[] cols = {0xFFEF5350, 0xFF7E57C2, 0xFFFFA726, 0xFF26A69A, 0xFF42A5F5, 0xFFEC407A, 0xFFFFCA28, 0xFF66BB6A};
                rc.set(cx - r, cy - r, cx + r, cy + r);
                for (int i = 0; i < 8; i++) {
                    p.setColor(cols[i]);
                    c.drawArc(rc, -90 + i * 45, 45, true, p);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.14f);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, r * 0.22f, p);
                p.setColor(0xFFD50000);
                pa.reset();
                pa.moveTo(cx - r * 0.2f, cy - r * 1.15f);
                pa.lineTo(cx + r * 0.2f, cy - r * 1.15f);
                pa.lineTo(cx, cy - r * 0.75f);
                pa.close();
                c.drawPath(pa, p);
                break;
            }
            case SHOP: {
                pa.reset();
                pa.moveTo(cx - r * 0.7f, cy - r * 0.4f);
                pa.lineTo(cx + r * 0.7f, cy - r * 0.4f);
                pa.lineTo(cx + r * 0.88f, cy + r * 0.95f);
                pa.lineTo(cx - r * 0.88f, cy + r * 0.95f);
                pa.close();
                lin(p, cy - r * 0.4f, cy + r, 0xFF8BE08B, 0xFF2E9E3C);
                c.drawPath(pa, p);
                outline(c, p, pa, 0xFF1B6B27, r * 0.07f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.14f);
                p.setColor(0xFFFFFFFF);
                rc.set(cx - r * 0.42f, cy - r * 0.95f, cx + r * 0.42f, cy - r * 0.05f);
                c.drawArc(rc, 180, 180, false, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFF59D);
                c.drawCircle(cx, cy + r * 0.3f, r * 0.25f, p);
                break;
            }
            case TROPHY: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.14f);
                p.setColor(0xFFFFB300);
                rc.set(cx - r * 1.05f, cy - r * 0.8f, cx - r * 0.4f, cy);
                c.drawArc(rc, 90, 180, false, p);
                rc.set(cx + r * 0.4f, cy - r * 0.8f, cx + r * 1.05f, cy);
                c.drawArc(rc, 270, 180, false, p);
                p.setStyle(Paint.Style.FILL);
                pa.reset();
                pa.moveTo(cx - r * 0.7f, cy - r * 0.9f);
                pa.lineTo(cx + r * 0.7f, cy - r * 0.9f);
                pa.quadTo(cx + r * 0.7f, cy + r * 0.3f, cx, cy + r * 0.4f);
                pa.quadTo(cx - r * 0.7f, cy + r * 0.3f, cx - r * 0.7f, cy - r * 0.9f);
                lin(p, cy - r * 0.9f, cy + r * 0.4f, 0xFFFFE082, 0xFFFFA000);
                c.drawPath(pa, p);
                p.setShader(null);
                p.setColor(0xFFFFB300);
                c.drawRect(cx - r * 0.12f, cy + r * 0.35f, cx + r * 0.12f, cy + r * 0.7f, p);
                lin(p, cy + r * 0.65f, cy + r * 0.95f, 0xFFFFD54F, 0xFFC77C00);
                rc.set(cx - r * 0.5f, cy + r * 0.65f, cx + r * 0.5f, cy + r * 0.95f);
                c.drawRoundRect(rc, r * 0.1f, r * 0.1f, p);
                p.setShader(null);
                p.setColor(0x88FFFFFF);
                rc.set(cx - r * 0.5f, cy - r * 0.8f, cx - r * 0.2f, cy - r * 0.1f);
                c.drawOval(rc, p);
                break;
            }
            case GEAR: {
                lin(p, cy - r, cy + r, 0xFFE8EEF7, 0xFF8697B5);
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45, cx, cy);
                    rc.set(cx - r * 0.18f, cy - r * 1.0f, cx + r * 0.18f, cy - r * 0.6f);
                    c.drawRoundRect(rc, r * 0.06f, r * 0.06f, p);
                    c.restore();
                }
                c.drawCircle(cx, cy, r * 0.74f, p);
                p.setShader(null);
                p.setColor(0xFF4A5A7A);
                c.drawCircle(cx, cy, r * 0.3f, p);
                p.setColor(0xAAFFFFFF);
                rc.set(cx - r * 0.55f, cy - r * 0.6f, cx - r * 0.05f, cy - r * 0.3f);
                c.drawOval(rc, p);
                break;
            }
            case FRIENDS: {
                for (int i = 0; i < 2; i++) {
                    float x = cx + (i == 0 ? -r * 0.42f : r * 0.42f);
                    int col = i == 0 ? 0xFFFFD1A8 : 0xFFFFE8CC;
                    int body = i == 0 ? 0xFF4FC3F7 : 0xFFFF8AB5;
                    rad(p, x, cy - r * 0.35f, r * 0.4f, col);
                    c.drawCircle(x, cy - r * 0.35f, r * 0.4f, p);
                    rad(p, x, cy + r * 0.6f, r * 0.6f, body);
                    rc.set(x - r * 0.55f, cy + r * 0.12f, x + r * 0.55f, cy + r * 1.05f);
                    c.drawRoundRect(rc, r * 0.4f, r * 0.4f, p);
                }
                break;
            }
            case SOUND:
            case SOUND_OFF: {
                pa.reset();
                pa.moveTo(cx - r * 0.9f, cy - r * 0.3f);
                pa.lineTo(cx - r * 0.45f, cy - r * 0.3f);
                pa.lineTo(cx + r * 0.1f, cy - r * 0.8f);
                pa.lineTo(cx + r * 0.1f, cy + r * 0.8f);
                pa.lineTo(cx - r * 0.45f, cy + r * 0.3f);
                pa.lineTo(cx - r * 0.9f, cy + r * 0.3f);
                pa.close();
                p.setColor(0xFFFFFFFF);
                c.drawPath(pa, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.14f);
                if (id == SOUND) {
                    rc.set(cx - r * 0.2f, cy - r * 0.45f, cx + r * 0.5f, cy + r * 0.45f);
                    c.drawArc(rc, -50, 100, false, p);
                    rc.set(cx - r * 0.2f, cy - r * 0.85f, cx + r * 0.95f, cy + r * 0.85f);
                    c.drawArc(rc, -50, 100, false, p);
                } else {
                    p.setColor(0xFFFF5252);
                    c.drawLine(cx + r * 0.3f, cy - r * 0.4f, cx + r * 0.95f, cy + r * 0.4f, p);
                    c.drawLine(cx + r * 0.95f, cy - r * 0.4f, cx + r * 0.3f, cy + r * 0.4f, p);
                }
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case HELP: {
                rad(p, cx, cy, r, 0xFF4FA3FF);
                c.drawCircle(cx, cy, r, p);
                p.setShader(null);
                p.setColor(0xFFFFFFFF);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(r * 1.5f);
                c.drawText("?", cx, cy + r * 0.52f, p);
                break;
            }
            case PLUS: {
                rad(p, cx, cy, r, 0xFF3FD95B);
                c.drawCircle(cx, cy, r, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.2f);
                p.setColor(0xFFFFFFFF);
                c.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, p);
                c.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.45f, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case STAR: {
                pa.reset();
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = (i % 2 == 0) ? r * 1.1f : r * 0.5f;
                    float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                    if (i == 0) pa.moveTo(x, y); else pa.lineTo(x, y);
                }
                pa.close();
                rad(p, cx, cy, r * 1.1f, 0xFFFFC107);
                c.drawPath(pa, p);
                outline(c, p, pa, 0xFFB77900, r * 0.08f);
                break;
            }
            case MOVES: {
                rad(p, cx, cy, r, 0xFFFF9F1A);
                c.drawCircle(cx, cy, r, p);
                p.setShader(null);
                p.setColor(0xFFFFFFFF);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(r * 1.0f);
                c.drawText("+5", cx, cy + r * 0.36f, p);
                break;
            }
            case CROSS: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(r * 0.42f);
                p.setColor(0xFF8A5A00);
                c.drawLine(cx - r * 0.75f, cy - r * 0.75f, cx + r * 0.75f, cy + r * 0.75f, p);
                c.drawLine(cx + r * 0.75f, cy - r * 0.75f, cx - r * 0.75f, cy + r * 0.75f, p);
                p.setStrokeWidth(r * 0.3f);
                p.setColor(0xFFFFD54F);
                c.drawLine(cx - r * 0.75f, cy - r * 0.75f, cx + r * 0.75f, cy + r * 0.75f, p);
                c.drawLine(cx + r * 0.75f, cy - r * 0.75f, cx - r * 0.75f, cy + r * 0.75f, p);
                p.setStyle(Paint.Style.FILL);
                rad(p, cx, cy, r * 0.45f, 0xFFFF5252);
                c.drawCircle(cx, cy, r * 0.42f, p);
                p.setShader(null);
                p.setColor(0xCCFFFFFF);
                c.drawCircle(cx - r * 0.14f, cy - r * 0.14f, r * 0.1f, p);
                break;
            }
            case PALETTE: {
                rad(p, cx, cy, r * 1.05f, 0xFFBCAAA4);
                pa.reset();
                pa.moveTo(cx + r * 0.9f, cy);
                pa.cubicTo(cx + r * 0.9f, cy - r * 1.15f, cx - r * 0.4f, cy - r * 1.2f, cx - r * 0.85f, cy - r * 0.5f);
                pa.cubicTo(cx - r * 1.3f, cy + r * 0.2f, cx - r * 0.7f, cy + r * 1.05f, cx + r * 0.1f, cy + r * 0.95f);
                pa.cubicTo(cx + r * 0.55f, cy + r * 0.9f, cx + r * 0.35f, cy + r * 0.45f, cx + r * 0.55f, cy + r * 0.35f);
                pa.cubicTo(cx + r * 0.8f, cy + r * 0.28f, cx + r * 0.9f, cy + r * 0.2f, cx + r * 0.9f, cy);
                pa.close();
                c.drawPath(pa, p);
                p.setShader(null);
                int[] dots = {0xFFFF3B47, 0xFFFFE53B, 0xFF3FD95B, 0xFF3C8CFF};
                float[] dxs = {-r * 0.45f, -r * 0.1f, r * 0.2f, -r * 0.3f};
                float[] dys = {-r * 0.4f, -r * 0.55f, -r * 0.35f, r * 0.25f};
                for (int i = 0; i < 4; i++) {
                    p.setColor(dots[i]);
                    c.drawCircle(cx + dxs[i], cy + dys[i], r * 0.16f, p);
                }
                p.setColor(0xFF6D4C41);
                c.drawCircle(cx + r * 0.25f, cy + r * 0.55f, r * 0.18f, p);
                break;
            }
            case CHEST: {
                lin(p, cy - r * 0.2f, cy + r * 0.85f, 0xFFC98A4B, 0xFF8D5A2B);
                rc.set(cx - r * 0.85f, cy - r * 0.2f, cx + r * 0.85f, cy + r * 0.85f);
                c.drawRoundRect(rc, r * 0.12f, r * 0.12f, p);
                lin(p, cy - r * 0.9f, cy - r * 0.15f, 0xFFE0A860, 0xFFA8703A);
                rc.set(cx - r * 0.85f, cy - r * 0.9f, cx + r * 0.85f, cy - r * 0.15f);
                c.drawRoundRect(rc, r * 0.4f, r * 0.4f, p);
                p.setShader(null);
                p.setColor(0xFFFFD54F);
                rc.set(cx - r * 0.95f, cy - r * 0.28f, cx + r * 0.95f, cy - r * 0.05f);
                c.drawRoundRect(rc, r * 0.06f, r * 0.06f, p);
                p.setColor(0xFFB77900);
                rc.set(cx - r * 0.18f, cy - r * 0.35f, cx + r * 0.18f, cy + r * 0.25f);
                c.drawRoundRect(rc, r * 0.06f, r * 0.06f, p);
                p.setColor(0xFFFFEB3B);
                c.drawCircle(cx, cy - r * 0.05f, r * 0.08f, p);
                break;
            }
            default: { // PIN
                pa.reset();
                pa.moveTo(cx, cy + r);
                pa.cubicTo(cx - r * 1.2f, cy - r * 0.1f, cx - r * 0.8f, cy - r, cx, cy - r);
                pa.cubicTo(cx + r * 0.8f, cy - r, cx + r * 1.2f, cy - r * 0.1f, cx, cy + r);
                rad(p, cx, cy, r, 0xFFFF4D6D);
                c.drawPath(pa, p);
                p.setShader(null);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy - r * 0.25f, r * 0.32f, p);
            }
        }
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
    }
}
