package com.rumpa.gemduel;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity implements GameView.Listener {
    static final int MAX_LIVES = 5;
    static final long REGEN = 25 * 60 * 1000L;

    static class Friend {
        int id, unlocked, stars;
        String name, pic;
    }

    FrameLayout root;
    GameView gv;
    LinearLayout home;
    ScrollView scroll;
    MapView map;
    TextView livesTv, infoTv;
    TopBarView topBar;
    NavBarView nav;
    Bitmap myAvatar;
    AlertDialog shopDlg;
    Button soundBtn, socialBtn;
    Sfx sfx;
    SharedPreferences sp;
    volatile List<Friend> friends = new ArrayList<>();
    final Map<String, Bitmap> avatars = new ConcurrentHashMap<>();
    boolean claiming;

    final Runnable tick = new Runnable() {
        public void run() {
            if (home != null) {
                if (home.getVisibility() == View.VISIBLE) updUi();
                home.postDelayed(this, 1000);
            }
        }
    };

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------ level map
    /** Top status bar: profile, stars, lives, coins, boosters. */
    class TopBarView extends View {
        final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF[] hit = new RectF[5];
        final RectF r = new RectF();
        final Path clipP = new Path();
        String name = "Guest", sub = "Tap to login", timer = "";
        Bitmap avatar;
        Shader pillSh;
        int lives = 5, coinsN, starsN, ham, shuf, lastCoins = -1, lastStars = -1;
        float dCoins = -1, dStars = -1;
        long coinBump, starBump;

        float bump(long t, long now) { return Math.max(0f, 1f - (now - t) / 400f); }

        TopBarView(Context c) {
            super(c);
            for (int i = 0; i < 5; i++) hit[i] = new RectF();
        }

        @Override
        protected void onMeasure(int wm, int hm) { setMeasuredDimension(MeasureSpec.getSize(wm), dp(108)); }

        void pill(Canvas cv, RectF b) {
            float rad = b.height() / 2;
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(0x66000000);
            r.set(b.left, b.top + dp(2), b.right, b.bottom + dp(2));
            cv.drawRoundRect(r, rad, rad, tp);
            if (pillSh == null) pillSh = new LinearGradient(0, 0, 0, b.height(), 0xFF7352CC, 0xFF35206F, Shader.TileMode.CLAMP);
            cv.save();
            cv.translate(0, b.top);
            r.set(b.left, 0, b.right, b.height());
            tp.setColor(0xFFFFFFFF);
            tp.setShader(pillSh);
            cv.drawRoundRect(r, rad, rad, tp);
            tp.setShader(null);
            cv.restore();
            tp.setColor(0x30FFFFFF);
            r.set(b.left + dp(8), b.top + dp(3), b.right - dp(8), b.top + b.height() * 0.45f);
            cv.drawRoundRect(r, dp(10), dp(10), tp);
            tp.setStyle(Paint.Style.STROKE);
            tp.setStrokeWidth(dp(2));
            tp.setColor(0x99FFFFFF);
            cv.drawRoundRect(b, rad, rad, tp);
            tp.setStyle(Paint.Style.FILL);
        }

        void txt(Canvas cv, String s, float x, float y, float sz, int col, Paint.Align al) {
            tp.setStyle(Paint.Style.FILL);
            tp.setTextAlign(al);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextSize(sz);
            tp.setColor(col);
            cv.drawText(s, x, y, tp);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight(), pad = dp(10), ph = dp(42);
            long now = android.os.SystemClock.uptimeMillis();
            if (dCoins < 0) { dCoins = coinsN; dStars = starsN; lastCoins = coinsN; lastStars = starsN; }
            if (coinsN != lastCoins) { if (coinsN > lastCoins) coinBump = now; lastCoins = coinsN; }
            if (starsN != lastStars) { if (starsN > lastStars) starBump = now; lastStars = starsN; }
            dCoins += (coinsN - dCoins) * 0.14f;
            if (Math.abs(coinsN - dCoins) < 0.6f) dCoins = coinsN;
            dStars += (starsN - dStars) * 0.14f;
            if (Math.abs(starsN - dStars) < 0.6f) dStars = starsN;
            tp.setColor(0xFFFFFFFF); tp.setShader(new LinearGradient(0, 0, 0, H, 0xFF3A1C71, 0xFF22114D, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, tp);
            tp.setShader(null);
            tp.setColor(0xFFFFC94D);
            cv.drawRect(0, H - dp(3), W, H, tp);
            float r1 = dp(6), r2 = r1 + ph + dp(8);
            hit[0].set(pad, r1, W * 0.64f, r1 + ph);
            hit[1].set(W * 0.67f, r1, W - pad, r1 + ph);
            hit[2].set(pad, r2, W * 0.36f, r2 + ph);
            hit[3].set(W * 0.385f, r2, W * 0.68f, r2 + ph);
            hit[4].set(W * 0.705f, r2, W - pad, r2 + ph);
            for (int i = 0; i < 5; i++) pill(cv, hit[i]);

            RectF b = hit[0];
            float cy = b.centerY(), ar = ph * 0.38f, ax = b.left + ph * 0.55f;
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(0xFF3949AB);
            cv.drawCircle(ax, cy, ar, tp);
            if (avatar != null) {
                cv.save();
                clipP.reset();
                clipP.addCircle(ax, cy, ar, Path.Direction.CW);
                cv.clipPath(clipP);
                r.set(ax - ar, cy - ar, ax + ar, cy + ar);
                cv.drawBitmap(avatar, null, r, tp);
                cv.restore();
            } else {
                Gems.draw(cv, tp, 0, 0, ax, cy, ar * 0.7f, false);
            }
            tp.setStyle(Paint.Style.STROKE);
            tp.setStrokeWidth(dp(2));
            tp.setColor(0xFFFFFFFF);
            cv.drawCircle(ax, cy, ar, tp);
            txt(cv, name, b.left + ph * 1.05f, cy - dp(1), dp(15), 0xFFFFFFFF, Paint.Align.LEFT);
            txt(cv, sub, b.left + ph * 1.05f, cy + dp(14), dp(11), 0xFFD8CCFF, Paint.Align.LEFT);

            b = hit[1];
            cy = b.centerY();
            Icons.draw(cv, tp, Icons.STAR, b.left + ph * 0.55f, cy, ph * 0.3f);
            txt(cv, String.valueOf(Math.round(dStars)), b.left + ph * 0.95f, cy + dp(6), dp(17) * (1f + 0.3f * bump(starBump, now)), 0xFFFFFFFF, Paint.Align.LEFT);

            b = hit[2];
            cy = b.centerY();
            Icons.draw(cv, tp, Icons.HEART, b.left + ph * 0.55f, cy, ph * 0.3f * (1f + 0.09f * (float) Math.sin(now / 170.0)));
            txt(cv, lives + "/5", b.left + ph * 0.95f, cy - dp(1), dp(15), 0xFFFFFFFF, Paint.Align.LEFT);
            txt(cv, timer, b.left + ph * 0.95f, cy + dp(14), dp(11), lives >= 5 ? 0xFF9CFFB0 : 0xFFFFD1D1, Paint.Align.LEFT);

            b = hit[3];
            cy = b.centerY();
            Icons.draw(cv, tp, Icons.COIN, b.left + ph * 0.55f, cy, ph * 0.3f);
            txt(cv, String.valueOf(Math.round(dCoins)), b.left + ph * 0.95f, cy + dp(6), dp(17) * (1f + 0.3f * bump(coinBump, now)), 0xFFFFE082, Paint.Align.LEFT);
            Icons.draw(cv, tp, Icons.PLUS, b.right - ph * 0.45f, cy, ph * 0.26f * (1f + 0.12f * (float) Math.sin(now / 220.0)));

            b = hit[4];
            cy = b.centerY();
            Icons.draw(cv, tp, Icons.HAMMER, b.left + ph * 0.45f, cy, ph * 0.26f);
            txt(cv, String.valueOf(ham), b.left + ph * 0.78f, cy + dp(6), dp(15), 0xFFFFFFFF, Paint.Align.LEFT);
            Icons.draw(cv, tp, Icons.SHUFFLE, b.centerX() + ph * 0.25f, cy, ph * 0.26f);
            txt(cv, String.valueOf(shuf), b.centerX() + ph * 0.58f, cy + dp(6), dp(15), 0xFFFFFFFF, Paint.Align.LEFT);
            postInvalidateDelayed(33);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (e.getAction() == MotionEvent.ACTION_UP) {
                for (int i = 0; i < 5; i++) if (hit[i].contains(e.getX(), e.getY())) { tapTop(i); return true; }
            }
            return true;
        }
    }

    /** Bottom navigation bar with glossy icons and notification dots. */
    class NavBarView extends View {
        final Paint np = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int[] ic = {Icons.SHOP, Icons.WHEEL, Icons.GIFT, Icons.FRIENDS, Icons.GEAR};
        final String[] lb = {"Shop", "Wheel", "Gift", "Friends", "More"};
        boolean dotGift, dotWheel;
        int tapIdx = -1;
        long tapT;

        NavBarView(Context c) { super(c); }

        @Override
        protected void onMeasure(int wm, int hm) { setMeasuredDimension(MeasureSpec.getSize(wm), dp(72)); }

        @Override
        protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight(), cw = W / 5f;
            np.setStyle(Paint.Style.FILL);
            np.setColor(0xFFFFFFFF); np.setShader(new LinearGradient(0, 0, 0, H, 0xFF5B3BB0, 0xFF22114D, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, np);
            np.setShader(null);
            np.setColor(0xFFFFC94D);
            cv.drawRect(0, 0, W, dp(3), np);
            for (int i = 0; i < 5; i++) {
                long now = android.os.SystemClock.uptimeMillis();
                float cx = cw * (i + 0.5f), cy = H * 0.42f + (float) Math.sin(now / 500.0 + i * 1.1) * dp(2) * 0.8f;
                float sq = 1f;
                if (i == tapIdx && now - tapT < 350) sq = 1f + 0.3f * (float) Math.sin(Math.PI * (now - tapT) / 350.0);
                np.setColor(0x33FFFFFF);
                cv.drawCircle(cx, cy, dp(23), np);
                Icons.draw(cv, np, ic[i], cx, cy, dp(15) * sq);
                np.setTextAlign(Paint.Align.CENTER);
                np.setTypeface(Typeface.DEFAULT_BOLD);
                np.setTextSize(dp(11));
                np.setColor(0xFFFFFFFF);
                cv.drawText(lb[i], cx, H * 0.88f, np);
                if ((i == 1 && dotWheel) || (i == 2 && dotGift)) {
                    np.setColor(0xFFFF1744);
                    cv.drawCircle(cx + dp(18), cy - dp(18), dp(6) * (1f + 0.25f * (float) Math.sin(now / 150.0)), np);
                    np.setStyle(Paint.Style.STROKE);
                    np.setStrokeWidth(dp(2));
                    np.setColor(0xFFFFFFFF);
                    cv.drawCircle(cx + dp(18), cy - dp(18), dp(6) * (1f + 0.25f * (float) Math.sin(now / 150.0)), np);
                    np.setStyle(Paint.Style.FILL);
                }
            }
            postInvalidateDelayed(33);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (e.getAction() == MotionEvent.ACTION_UP) {
                tapIdx = (int) (e.getX() / (getWidth() / 5f));
                tapT = android.os.SystemClock.uptimeMillis();
                sfx.click();
                tapNav(tapIdx);
            }
            return true;
        }
    }

    /** Small icon / candy picture used in lists. kind 0 = Icons id a, kind 1 = candy type a (special b). */
    class IconView extends View {
        final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int kind, a, b;

        IconView(Context c, int kind, int a, int b) { super(c); this.kind = kind; this.a = a; this.b = b; }

        @Override
        protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight();
            float m = Math.min(W, H);
            if (kind == 0) Icons.draw(cv, ip, a, W / 2, H / 2, m * 0.38f);
            else if (kind == 2) {
                ip.setStyle(Paint.Style.FILL);
                ip.setColor(0xFFFFFFFF);
                ip.setShader(new LinearGradient(0, H * 0.2f, 0, H * 0.8f, 0xFFFFB6D0, 0xFFE91E63, Shader.TileMode.CLAMP));
                ip.setColor(0xFFFFFFFF);
                cv.drawRoundRect(new RectF(W / 2 - m * 0.3f, H / 2 - m * 0.3f, W / 2 + m * 0.3f, H / 2 + m * 0.3f), m * 0.14f, m * 0.14f, ip);
                ip.setShader(null);
                ip.setColor(0x88FFFFFF);
                cv.drawOval(new RectF(W / 2 - m * 0.22f, H / 2 - m * 0.26f, W / 2 + m * 0.05f, H / 2 - m * 0.1f), ip);
            } else Gems.draw(cv, ip, a, b, W / 2, H / 2, m * 0.34f, false);
        }
    }

    class AvatarView extends View {
        final Paint ap = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path cp = new Path();
        final RectF ar = new RectF();

        AvatarView(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - dp(3);
            ap.setStyle(Paint.Style.FILL);
            ap.setColor(0xFF3949AB);
            cv.drawCircle(cx, cy, r, ap);
            if (myAvatar != null && loggedIn()) {
                cv.save();
                cp.reset();
                cp.addCircle(cx, cy, r, Path.Direction.CW);
                cv.clipPath(cp);
                ar.set(cx - r, cy - r, cx + r, cy + r);
                cv.drawBitmap(myAvatar, null, ar, ap);
                cv.restore();
            } else {
                Gems.draw(cv, ap, 0, 0, cx, cy, r * 0.6f, false);
            }
            ap.setStyle(Paint.Style.STROKE);
            ap.setStrokeWidth(dp(3));
            ap.setColor(0xFFFFC94D);
            cv.drawCircle(cx, cy, r, ap);
        }
    }

    // ------------------------------------------------------------ eye-candy dialog toolkit
    class SparkleView extends View {
        final Paint spp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float[][] q = new float[20][4];

        SparkleView(Context c) {
            super(c);
            Random r = new Random(4);
            for (float[] a : q) { a[0] = r.nextFloat(); a[1] = r.nextFloat(); a[2] = r.nextFloat() * 6.28f; a[3] = 0.6f + r.nextFloat(); }
        }

        @Override
        protected void onDraw(Canvas cv) {
            long now = android.os.SystemClock.uptimeMillis();
            float W = getWidth(), H = getHeight();
            spp.setStyle(Paint.Style.FILL);
            for (float[] a : q) {
                float al = 0.5f + 0.5f * (float) Math.sin(now / 420.0 + a[2]);
                float sz = dp(6) * a[3] * (0.4f + al);
                float x = a[0] * W, y = a[1] * H;
                spp.setColor(((int) (200 * al) << 24) | 0xFFFFFF);
                cv.drawRect(x - sz * 0.12f, y - sz, x + sz * 0.12f, y + sz, spp);
                cv.drawRect(x - sz, y - sz * 0.12f, x + sz, y + sz * 0.12f, spp);
            }
            postInvalidateDelayed(40);
        }
    }

    /** Three glossy stars that pop in one after another. */
    class StarsView extends View {
        final Paint sp3 = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path pth = new Path();
        final int earned;
        final boolean animate;
        long t0;

        StarsView(Context c, int earned, boolean animate) { super(c); this.earned = earned; this.animate = animate; }

        @Override
        protected void onMeasure(int wm, int hm) { setMeasuredDimension(MeasureSpec.getSize(wm), dp(104)); }

        @Override
        protected void onDraw(Canvas cv) {
            long now = android.os.SystemClock.uptimeMillis();
            if (t0 == 0) t0 = now;
            float age = now - t0, W = getWidth(), H = getHeight();
            boolean going = false;
            for (int i = 0; i < 3; i++) {
                float cx = W / 2 + (i - 1) * dp(96), cy = H / 2 + (i == 1 ? -dp(8) : dp(6));
                float r = dp(38) * (i == 1 ? 1.18f : 1f);
                boolean got = i < earned;
                float k = animate ? Math.max(0f, Math.min(1f, (age - 250 - i * 380) / 480f)) : 1f;
                if (animate && k < 1f) going = true;
                if (animate && k <= 0f) continue;
                float s = animate ? GameView.easeOutBack(k) : 1f;
                cv.save();
                cv.rotate(animate ? (1f - k) * 60f : 0f, cx, cy);
                if (got) {
                    Icons.draw(cv, sp3, Icons.STAR, cx, cy, r * s * 0.9f);
                } else {
                    pth.reset();
                    for (int j = 0; j < 10; j++) {
                        double a = -Math.PI / 2 + j * Math.PI / 5;
                        float rr = (j % 2 == 0) ? r * 0.98f * s : r * 0.46f * s;
                        float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                        if (j == 0) pth.moveTo(x, y); else pth.lineTo(x, y);
                    }
                    pth.close();
                    sp3.setStyle(Paint.Style.FILL);
                    sp3.setColor(0xFF4D3C94);
                    cv.drawPath(pth, sp3);
                    sp3.setStyle(Paint.Style.STROKE);
                    sp3.setStrokeWidth(dp(3));
                    sp3.setColor(0xFF7F6BD0);
                    cv.drawPath(pth, sp3);
                    sp3.setStyle(Paint.Style.FILL);
                }
                cv.restore();
            }
            if (going) postInvalidateOnAnimation();
        }
    }

    /** Bouncing gift box with rotating golden rays. */
    class GiftView extends View {
        final Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path ray = new Path();

        GiftView(Context c) { super(c); }

        @Override
        protected void onMeasure(int wm, int hm) { setMeasuredDimension(MeasureSpec.getSize(wm), dp(130)); }

        @Override
        protected void onDraw(Canvas cv) {
            long now = android.os.SystemClock.uptimeMillis();
            float W = getWidth(), H = getHeight(), cx = W / 2, cy = H / 2;
            gp.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 12; i++) {
                cv.save();
                cv.rotate(now / 45f + i * 30f, cx, cy);
                ray.reset();
                ray.moveTo(cx, cy);
                ray.lineTo(cx - dp(14), cy - dp(80));
                ray.lineTo(cx + dp(14), cy - dp(80));
                ray.close();
                gp.setColor(0x38FFE082);
                cv.drawPath(ray, gp);
                cv.restore();
            }
            float bob = (float) Math.sin(now / 280.0) * dp(5);
            cv.save();
            cv.rotate((float) Math.sin(now / 450.0) * 7f, cx, cy + bob);
            Icons.draw(cv, gp, Icons.GIFT, cx, cy + bob, dp(46));
            cv.restore();
            postInvalidateDelayed(33);
        }
    }

    TextView label(String t, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    View goalPill(int kind, int a, int b, String text) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(8), dp(4), dp(14), dp(4));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF8E6AE0, 0xFF4B2E9E});
        g.setCornerRadius(dp(20));
        g.setStroke(dp(2), 0x99FFFFFF);
        c.setBackground(g);
        c.addView(new IconView(this, kind, a, b), new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        t.setPadding(dp(8), 0, 0, 0);
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        c.setLayoutParams(lp);
        return c;
    }

    TextView textPill(String text, int c1, int c2, int textColor) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(textColor);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{c1, c2});
        g.setCornerRadius(dp(20));
        g.setStroke(dp(2), 0x88FFFFFF);
        t.setBackground(g);
        return t;
    }

    View rewardCard(int icon, String text) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(4), dp(10), dp(4), dp(8));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF9A78EA, 0xFF5232A8});
        g.setCornerRadius(dp(16));
        g.setStroke(dp(2), 0xAAFFFFFF);
        c.setBackground(g);
        c.addView(new IconView(this, 0, icon, 0), new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFE082);
        t.setGravity(Gravity.CENTER);
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        c.setLayoutParams(lp);
        return c;
    }

    View streakRow(int streak) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        int cur = (streak - 1) % 7 + 1;
        for (int i = 1; i <= 7; i++) {
            TextView t = new TextView(this);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextSize(14);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (i < cur) { g.setColor(0xFFFFC107); t.setText("✓"); t.setTextColor(0xFF5A3A00); }
            else if (i == cur) { g.setColor(0xFFFF4081); g.setStroke(dp(3), 0xFFFFFFFF); t.setText(String.valueOf(i)); t.setTextColor(0xFFFFFFFF); }
            else { g.setColor(0x44FFFFFF); t.setText(String.valueOf(i)); t.setTextColor(0xCCFFFFFF); }
            t.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMargins(dp(2), 0, dp(2), 0);
            row.addView(t, lp);
        }
        return row;
    }

    /** Selectable booster picture card (level screen). */
    View boostCard(final boolean[] want, final int idx, int gemType, int gemSp, final int count, final String name) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(6), dp(8), dp(6), dp(8));
        final TextView t = new TextView(this);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        c.addView(new IconView(this, 1, gemType, gemSp), new LinearLayout.LayoutParams(dp(58), dp(58)));
        c.addView(t);
        final Runnable paint = new Runnable() {
            public void run() {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{want[idx] ? 0xFFB08CFF : 0xFF8061D6, want[idx] ? 0xFF6A3FD6 : 0xFF48299A});
                g.setCornerRadius(dp(16));
                g.setStroke(dp(3), want[idx] ? 0xFFFFD54F : 0x88FFFFFF);
                c.setBackground(g);
                t.setTextColor(count > 0 ? (want[idx] ? 0xFFFFE082 : 0xFFFFFFFF) : 0xAAFFFFFF);
                t.setText((want[idx] ? "✓ " : "") + name + (count > 0 ? "  x" + count : "\nGet in Shop"));
            }
        };
        paint.run();
        c.setAlpha(count > 0 ? 1f : 0.65f);
        c.setOnClickListener(v -> {
            if (count <= 0) { toast("Buy this booster in the Shop"); return; }
            want[idx] = !want[idx];
            sfx.click();
            paint.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        c.setLayoutParams(lp);
        return c;
    }

    /** Candy-style popup: ribbon title, glossy card, sparkles, 3D buttons. */
    class Dlg {
        final Dialog d;
        final LinearLayout body, btns;

        Dlg(String title, int ribbonColor) {
            d = new Dialog(MainActivity.this, R.style.GemCustomDialog);
            FrameLayout root = new FrameLayout(MainActivity.this);
            root.setPadding(dp(16), dp(6), dp(16), dp(10));
            FrameLayout card = new FrameLayout(MainActivity.this);
            GradientDrawable bgd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF6D47D0, 0xFF2B1766});
            bgd.setCornerRadius(dp(28));
            bgd.setStroke(dp(4), 0xFFFFC94D);
            card.setBackground(bgd);
            card.addView(new SparkleView(MainActivity.this), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout content = new LinearLayout(MainActivity.this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(18), dp(40), dp(18), dp(16));
            body = new LinearLayout(MainActivity.this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.CENTER_HORIZONTAL);
            btns = new LinearLayout(MainActivity.this);
            btns.setOrientation(LinearLayout.VERTICAL);
            content.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = dp(10);
            content.addView(btns, bp);
            ScrollView sv = new ScrollView(MainActivity.this);
            sv.addView(content);
            card.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.topMargin = dp(24);
            root.addView(card, cp);

            TextView rb = new TextView(MainActivity.this);
            rb.setText(title);
            rb.setTextSize(20);
            rb.setTypeface(Typeface.DEFAULT_BOLD);
            rb.setTextColor(0xFFFFFFFF);
            rb.setShadowLayer(4, 0, 2, 0xFF000000);
            rb.setGravity(Gravity.CENTER);
            rb.setPadding(dp(28), dp(8), dp(28), dp(12));
            GradientDrawable top = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Gems.lighten(ribbonColor, 0.4f), ribbonColor});
            top.setCornerRadius(dp(18));
            top.setStroke(dp(3), Gems.darken(ribbonColor, 0.45f));
            GradientDrawable base = new GradientDrawable();
            base.setColor(Gems.darken(ribbonColor, 0.5f));
            base.setCornerRadius(dp(18));
            LayerDrawable ld = new LayerDrawable(new Drawable[]{base, top});
            ld.setLayerInset(1, 0, 0, 0, dp(4));
            rb.setBackground(ld);
            FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            root.addView(rb, rp);
            rb.setScaleX(0.5f);
            rb.setScaleY(0.5f);
            rb.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator(2.2f)).setStartDelay(120).setDuration(500).start();

            d.setContentView(root);
            Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setDimAmount(0.65f);
            }
        }

        Dlg add(View v) { body.addView(v); return this; }

        Dlg add(View v, int delay) { body.addView(v); popIn(v, delay); return this; }

        Dlg addLp(View v, LinearLayout.LayoutParams lp, int delay) { body.addView(v, lp); popIn(v, delay); return this; }

        Dlg cancelable(boolean c) { d.setCancelable(c); d.setCanceledOnTouchOutside(c); return this; }

        Button button(String text, int color, boolean big, Runnable r) { return btn(text, color, big, r, true); }

        Button buttonKeep(String text, int color, boolean big, Runnable r) { return btn(text, color, big, r, false); }

        Button btn(String text, int color, boolean big, final Runnable r, final boolean dismiss) {
            Button b = smallBtn(text, color, null);
            b.setTextSize(big ? 20 : 15);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(big ? 58 : 46));
            lp.setMargins(0, dp(8), 0, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                sfx.click();
                if (dismiss) d.dismiss();
                if (r != null) r.run();
            });
            btns.addView(b);
            return b;
        }

        void show() {
            if (isFinishing() || isDestroyed()) return;
            try { d.show(); } catch (Exception ignored) { }
        }
    }

    class TitleView extends View {
        final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float[][] sparks = new float[14][4];
        Shader fillSh;
        float fillTs;

        TitleView(Context c) {
            super(c);
            Random r = new Random(21);
            for (float[] a : sparks) { a[0] = r.nextFloat(); a[1] = r.nextFloat(); a[2] = r.nextFloat() * 6.28f; a[3] = 0.6f + r.nextFloat(); }
        }

        @Override
        protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight();
            long now = android.os.SystemClock.uptimeMillis();
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(0xFFFFFFFF);
            tp.setShader(new RadialGradient(W / 2, H * 0.55f, W * 0.6f, 0x66B58CFF, 0x00B58CFF, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, tp);
            tp.setShader(null);
            for (float[] a : sparks) {
                float al = 0.5f + 0.5f * (float) Math.sin(now / 400.0 + a[2]);
                float sz = dp(6) * a[3] * (0.4f + al);
                float x = a[0] * W, y = a[1] * H;
                tp.setColor(((int) (220 * al) << 24) | 0xFFFFFF);
                cv.drawRect(x - sz * 0.12f, y - sz, x + sz * 0.12f, y + sz, tp);
                cv.drawRect(x - sz, y - sz * 0.12f, x + sz, y + sz * 0.12f, tp);
            }

            String title = "GEM CRUSH";
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setLetterSpacing(0.02f);
            float ts = H * 0.56f;
            tp.setTextSize(ts);
            float total = tp.measureText(title);
            if (total > W * 0.64f) { ts = ts * W * 0.64f / total; tp.setTextSize(ts); total = tp.measureText(title); }
            float x0 = (W - total) / 2f, y = H * 0.66f;
            float[] adv = new float[title.length()];
            float acc = 0;
            for (int i = 0; i < title.length(); i++) {
                adv[i] = acc;
                acc += tp.measureText(title.substring(i, i + 1));
            }
            float sweep = ((now % 2600) / 2600f) * (total + W * 0.5f) - W * 0.25f + x0;
            if (fillSh == null || fillTs != ts) {
                fillSh = new LinearGradient(0, -ts * 0.75f, 0, 0, new int[]{0xFFFFFDE7, 0xFFFFE082, 0xFFFF9800}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
                fillTs = ts;
            }
            Shader shimmer = new LinearGradient(sweep - W * 0.12f, 0, sweep + W * 0.12f, 0,
                    new int[]{0x00FFFFFF, 0xDDFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            for (int pass = 0; pass < 4; pass++) {
                for (int i = 0; i < title.length(); i++) {
                    String ch = title.substring(i, i + 1);
                    float yy = y + (float) Math.sin(now / 380.0 + i * 0.7) * H * 0.04f;
                    float x = x0 + adv[i];
                    if (pass == 0) { // 3D extrusion
                        tp.setShader(null);
                        tp.setStyle(Paint.Style.FILL_AND_STROKE);
                        tp.setStrokeWidth(dp(6));
                        tp.setStrokeJoin(Paint.Join.ROUND);
                        for (int d = 6; d >= 2; d -= 2) {
                            tp.setColor(d > 4 ? 0xFF6A2300 : 0xFF9A3E00);
                            cv.drawText(ch, x, yy + dp(1) * d * 0.9f, tp);
                        }
                    } else if (pass == 1) {
                        tp.setShader(null);
                        tp.setStyle(Paint.Style.STROKE);
                        tp.setStrokeWidth(dp(7));
                        tp.setColor(0xFF2B1559);
                        cv.drawText(ch, x, yy, tp);
                    } else if (pass == 2) {
                        tp.setStyle(Paint.Style.FILL);
                        tp.setColor(0xFFFFFFFF);
                        cv.save();
                        cv.translate(x, yy);
                        tp.setShader(fillSh);
                        cv.drawText(ch, 0, 0, tp);
                        cv.restore();
                    } else {
                        tp.setStyle(Paint.Style.FILL);
                        tp.setColor(0xFFFFFFFF);
                        tp.setShader(shimmer);
                        cv.drawText(ch, x, yy, tp);
                    }
                }
            }
            tp.setShader(null);
            tp.setStyle(Paint.Style.FILL);
            tp.setLetterSpacing(0.25f);
            tp.setTextSize(H * 0.15f);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(0xCCFFFFFF);
            cv.drawText("MATCH  -  POP  -  CRUSH", W / 2, H * 0.95f, tp);
            tp.setLetterSpacing(0f);
            tp.setTextAlign(Paint.Align.LEFT);

            float bob = (float) Math.sin(now / 420.0) * H * 0.05f;
            float lx = x0 - H * 0.36f, rx = x0 + total + H * 0.36f, cy = H * 0.46f;
            cv.save();
            cv.rotate((float) Math.sin(now / 500.0) * 10f, lx, cy + bob);
            Gems.draw(cv, tp, 0, 0, lx, cy + bob, H * 0.24f, false);
            cv.restore();
            cv.save();
            cv.rotate((float) Math.sin(now / 500.0 + 2) * 10f, rx, cy - bob);
            Gems.draw(cv, tp, 4, 0, rx, cy - bob, H * 0.24f, false);
            cv.restore();
            Gems.draw(cv, tp, 2, 0, x0 - H * 0.82f, H * 0.62f - bob * 0.6f, H * 0.16f, false);
            Gems.draw(cv, tp, 5, 0, rx + H * 0.46f, H * 0.62f + bob * 0.6f, H * 0.16f, false);
            postInvalidateDelayed(33);
        }
    }

    class MapView extends View {
        final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path clip = new Path();
        final RectF rect = new RectF();
        final Rect vis = new Rect();
        final float step = dp(104);
        static final int PAGE = 100;
        int pageStart = 1;
        final java.util.HashMap<Integer, Shader> sph = new java.util.HashMap<>();
        final RectF upBtn = new RectF(), downBtn = new RectF();

        MapView(Context c) { super(c); }

        int pageEnd() { return Math.min(GameView.MAX_LEVEL, pageStart + PAGE - 1); }

        int pageOf(int lv) { return ((lv - 1) / PAGE) * PAGE + 1; }

        void setPage(int start) {
            pageStart = start;
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int wm, int hm) {
            int count = pageEnd() - pageStart + 1;
            setMeasuredDimension(MeasureSpec.getSize(wm), (int) (count * step + dp(260)));
        }

        float nx(int i, float w) { return w / 2 + (float) Math.sin((i - 1) * 0.9) * w * 0.28f; }

        float ny(int i) { return getHeight() - dp(110) - (i - pageStart) * step; }

        int levelAt(float y) { return (int) ((getHeight() - dp(110) - y) / step) + pageStart; }

        Shader sphere(int col, float r, boolean boss) {
            int key = col * 2 + (boss ? 1 : 0);
            Shader s = sph.get(key);
            if (s == null) {
                s = new RadialGradient(-r * 0.35f, -r * 0.45f, r * 1.6f,
                        new int[]{Gems.lighten(col, 0.55f), col, Gems.darken(col, 0.5f)}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                sph.put(key, s);
            }
            return s;
        }

        void avatar(Canvas cv, Friend f, float x, float y, float r) {
            Bitmap bm = f.pic == null || f.pic.isEmpty() ? null : avatars.get(f.pic);
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(0xFF3949AB);
            cv.drawCircle(x, y, r, pt);
            if (bm != null) {
                cv.save();
                clip.reset();
                clip.addCircle(x, y, r, Path.Direction.CW);
                cv.clipPath(clip);
                rect.set(x - r, y - r, x + r, y + r);
                cv.drawBitmap(bm, null, rect, pt);
                cv.restore();
            } else {
                pt.setColor(0xFFFFFFFF);
                pt.setTextAlign(Paint.Align.CENTER);
                pt.setTextSize(r * 1.1f);
                String ini = f.name == null || f.name.isEmpty() ? "?" : f.name.substring(0, 1).toUpperCase();
                cv.drawText(ini, x, y + r * 0.38f, pt);
            }
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(dp(2));
            pt.setColor(0xFFFFFFFF);
            cv.drawCircle(x, y, r, pt);
        }

        void navPill(Canvas cv, RectF b, String text, int color) {
            float rad = b.height() / 2;
            pt.setStyle(Paint.Style.FILL);
            pt.setShader(null);
            pt.setColor(0x66000000);
            rect.set(b.left, b.top + dp(3), b.right, b.bottom + dp(3));
            cv.drawRoundRect(rect, rad, rad, pt);
            pt.setColor(color);
            cv.drawRoundRect(b, rad, rad, pt);
            pt.setColor(0x44FFFFFF);
            rect.set(b.left + dp(10), b.top + dp(3), b.right - dp(10), b.top + b.height() * 0.45f);
            cv.drawRoundRect(rect, rad, rad, pt);
            pt.setColor(0xFFFFFFFF);
            pt.setTextAlign(Paint.Align.CENTER);
            pt.setTypeface(Typeface.DEFAULT_BOLD);
            pt.setTextSize(dp(14));
            cv.drawText(text, b.centerX(), b.centerY() + dp(5), pt);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight();
            cv.getClipBounds(vis);
            float vt = vis.top - dp(200), vb = vis.bottom + dp(200);
            int maxL = pageEnd();
            int lo = Math.max(pageStart, Math.min(maxL, levelAt(vb)) - 1);
            int hi = Math.min(maxL, Math.max(pageStart, levelAt(vt)) + 1);
            int unlocked = Math.min(sp.getInt("unlocked", 1), GameView.MAX_LEVEL);
            int wFirst = GameView.world(pageStart), wLast = GameView.world(maxL);
            int wLo = Math.max(wFirst, GameView.world(lo)), wHi = Math.min(wLast, GameView.world(hi));

            pt.setStyle(Paint.Style.FILL);
            pt.setTypeface(Typeface.DEFAULT_BOLD);
            for (int w = wLo; w <= wHi; w++) {
                float bt = ny(Math.min(maxL, (w + 1) * 25)) - step / 2;
                float bb = w == wFirst ? H : ny(w * 25 + 1) + step / 2;
                int[] wc = GameView.worldColors(w);
                pt.setColor(0xFFFFFFFF);
                pt.setShader(new LinearGradient(0, bt, 0, bb, wc[0], wc[1], Shader.TileMode.CLAMP));
                cv.drawRect(0, w == wLast ? 0 : bt, W, bb, pt);
                pt.setShader(null);
                java.util.Random rr = new java.util.Random(w * 77L + 5);
                for (int k = 0; k < 14; k++) {
                    float gx = rr.nextFloat() * W, gr = dp(18) + rr.nextFloat() * dp(26);
                    float gy = bt + rr.nextFloat() * (bb - bt);
                    int gt = rr.nextInt(6);
                    if (gy < vt || gy > vb) continue;
                    Gems.drawAlpha(cv, gt, 0, gx, gy, gr, 70);
                }
                pt.setStyle(Paint.Style.FILL);
                pt.setTextAlign(Paint.Align.CENTER);
                pt.setTextSize(dp(18));
                pt.setColor(0xAAFFFFFF);
                cv.drawText("WORLD " + (w + 1) + " - " + GameView.worldName(w), W / 2, bt + dp(15), pt);
            }

            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeCap(Paint.Cap.ROUND);
            for (int i = lo; i < hi && i < maxL; i++) {
                pt.setPathEffect(null);
                pt.setStrokeWidth(dp(12));
                pt.setColor(0x33000000);
                cv.drawLine(nx(i, W), ny(i) + dp(3), nx(i + 1, W), ny(i + 1) + dp(3), pt);
                pt.setPathEffect(new DashPathEffect(new float[]{dp(14), dp(12)}, 0));
                pt.setStrokeWidth(dp(9));
                pt.setColor(i < unlocked ? 0xFFFFF59D : 0x66FFFFFF);
                cv.drawLine(nx(i, W), ny(i), nx(i + 1, W), ny(i + 1), pt);
            }
            pt.setPathEffect(null);

            pt.setTextAlign(Paint.Align.CENTER);
            for (int i = lo; i <= hi; i++) {
                float x = nx(i, W), y = ny(i);
                boolean boss = i % 25 == 0, open = i <= unlocked;
                float r = boss ? dp(38) : dp(30);
                int col = !open ? 0xFF8A8A9A : i == unlocked ? 0xFFFF4081 : boss ? 0xFFFFB300 : 0xFF4CAF50;
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(0x55000000);
                rect.set(x - r, y + r * 0.7f, x + r, y + r * 1.15f);
                cv.drawOval(rect, pt);
                cv.save();
                cv.translate(x, y);
                pt.setColor(0xFFFFFFFF);
                pt.setShader(sphere(col, r, boss));
                cv.drawCircle(0, 0, r, pt);
                pt.setShader(null);
                cv.restore();
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(boss ? dp(5) : dp(3));
                pt.setColor(boss ? 0xFFFFF176 : 0xFFFFFFFF);
                cv.drawCircle(x, y, r, pt);
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(0x77FFFFFF);
                rect.set(x - r * 0.62f, y - r * 0.85f, x + r * 0.1f, y - r * 0.35f);
                cv.drawOval(rect, pt);
                pt.setTextSize(dp(boss ? 24 : 22));
                pt.setShadowLayer(4, 0, 2, 0xFF000000);
                pt.setColor(0xFFFFFFFF);
                cv.drawText(String.valueOf(i), x, y + dp(8), pt);
                pt.clearShadowLayer();
                if (open) {
                    int s = sp.getInt("stars_" + i, 0);
                    pt.setColor(0x66000000);
                    rect.set(x - dp(34), y + r + dp(4), x + dp(34), y + r + dp(28));
                    cv.drawRoundRect(rect, dp(12), dp(12), pt);
                    pt.setTextSize(dp(16));
                    for (int k = 0; k < 3; k++) {
                        pt.setColor(k < s ? 0xFFFFD54F : 0x88FFFFFF);
                        cv.drawText("★", x + (k - 1) * dp(18), y + r + dp(21), pt);
                    }
                }
            }

            long nowT = android.os.SystemClock.uptimeMillis();
            if (unlocked >= lo && unlocked <= hi) {
                float px = nx(unlocked, W), py = ny(unlocked);
                float ph = (nowT % 1200) / 1200f;
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(dp(4));
                pt.setColor(((int) (200 * (1 - ph)) << 24) | 0xFFFFFF);
                cv.drawCircle(px, py, dp(30) + ph * dp(22), pt);
                float bob = (float) Math.sin(nowT / 220.0) * dp(4);
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(0xFFFFFFFF);
                rect.set(px - dp(30), py - dp(78) + bob, px + dp(30), py - dp(52) + bob);
                cv.drawRoundRect(rect, dp(13), dp(13), pt);
                pt.setColor(0xFFFF4081);
                pt.setTextSize(dp(14));
                pt.setTextAlign(Paint.Align.CENTER);
                cv.drawText("PLAY", px, py - dp(59) + bob, pt);
            }

            int[] cnt = new int[hi - lo + 2];
            for (Friend f : friends) {
                int lv = Math.max(1, Math.min(GameView.MAX_LEVEL, f.unlocked));
                if (lv < lo || lv > hi) continue;
                int k = cnt[lv - lo]++;
                float off = dp(46) + (k / 2) * dp(22);
                float x = nx(lv, W) + (k % 2 == 0 ? off : -off);
                avatar(cv, f, x, ny(lv) + (float) Math.sin(nowT / 400.0 + f.id) * dp(3), dp(17));
            }

            // page navigation (the map shows 100 levels at a time)
            upBtn.setEmpty();
            downBtn.setEmpty();
            if (pageEnd() < GameView.MAX_LEVEL) {
                upBtn.set(W / 2 - dp(120), dp(24), W / 2 + dp(120), dp(68));
                navPill(cv, upBtn, "NEXT LEVELS  " + (pageEnd() + 1) + "+", 0xFFFF8F00);
            }
            if (pageStart > 1) {
                downBtn.set(W / 2 - dp(120), H - dp(68), W / 2 + dp(120), H - dp(24));
                navPill(cv, downBtn, "PREVIOUS LEVELS", 0xFF5E35B1);
            }
            postInvalidateDelayed(33);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float W = getWidth();
                if (upBtn.contains(e.getX(), e.getY())) {
                    setPage(pageStart + PAGE);
                    scroll.postDelayed(() -> scroll.fullScroll(View.FOCUS_DOWN), 160);
                    return true;
                }
                if (downBtn.contains(e.getX(), e.getY())) {
                    setPage(pageStart - PAGE);
                    scroll.postDelayed(() -> scroll.fullScroll(View.FOCUS_UP), 160);
                    return true;
                }
                int c0 = levelAt(e.getY());
                for (int i = Math.max(pageStart, c0 - 1); i <= Math.min(pageEnd(), c0 + 1); i++) {
                    float dx = e.getX() - nx(i, W), dy = e.getY() - ny(i);
                    if (dx * dx + dy * dy < dp(40) * dp(40)) { levelInfo(i); return true; }
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------ lifecycle / layout
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("gd", MODE_PRIVATE);
        installCrashReporter();
        sfx = new Sfx((Vibrator) getSystemService(Context.VIBRATOR_SERVICE));
        sfx.on = sp.getBoolean("sound", true);
        sfx.setMusic(sp.getBoolean("music", true));
        sfx.vol = sp.getInt("svol", 80) / 100f;
        sfx.setMusicVolume(sp.getInt("mvol", 70) / 100f);

        root = new FrameLayout(this);
        gv = new GameView(this);
        gv.lst = this;
        gv.sfx = sfx;
        gv.setVisibility(View.GONE);

        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setBackgroundColor(0xFF1B1030);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(0, dp(4), 0, 0);
        head.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF2B1559, 0xFF1B1030}));

        head.addView(new TitleView(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)));

        topBar = new TopBarView(this);
        head.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        home.addView(head);

        map = new MapView(this);
        scroll = new ScrollView(this);
        scroll.addView(map, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        home.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        nav = new NavBarView(this);
        home.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(gv, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(home, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        setContentView(root);

        updUi();
        home.postDelayed(tick, 1000);
        scrollToCurrent();
        handleIntent(getIntent());
        pushProgress();
        syncInventory();
        loadMyAvatar();
        home.post(this::showLastCrash);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIntent(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sfx.startMusic();
        if (!loggedIn() && !sp.getString("nonce", "").isEmpty()) claim(true);
        else if (loggedIn()) { refreshFriends(); scheduleInvSync(); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        home.removeCallbacks(tick);
    }

    void handleIntent(Intent i) {
        if (i != null && i.getData() != null && "gempop".equals(i.getData().getScheme())) claim(false);
    }

    Button smallBtn(String text, int color, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setTextSize(13);
        bt.setTypeface(Typeface.DEFAULT_BOLD);
        bt.setTextColor(Color.WHITE);
        bt.setShadowLayer(3, 0, 2, 0xFF000000);
        bt.setPadding(dp(2), 0, dp(2), dp(4));
        GradientDrawable top = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Gems.lighten(color, 0.4f), color});
        top.setCornerRadius(dp(18));
        top.setStroke(dp(2), Gems.darken(color, 0.35f));
        GradientDrawable base = new GradientDrawable();
        base.setColor(Gems.darken(color, 0.5f));
        base.setCornerRadius(dp(18));
        LayerDrawable ld = new LayerDrawable(new Drawable[]{base, top});
        ld.setLayerInset(1, 0, 0, 0, dp(4));
        bt.setBackground(ld);
        bt.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(3), dp(8), dp(3), 0);
        bt.setLayoutParams(lp);
        return bt;
    }

    void gotoLevel(final int lv) {
        int pg = map.pageOf(lv);
        if (pg != map.pageStart) map.setPage(pg);
        scroll.postDelayed(() -> scroll.scrollTo(0, Math.max(0, (int) (map.ny(lv) - scroll.getHeight() / 2f))), 160);
    }

    void scrollToCurrent() { gotoLevel(Math.min(sp.getInt("unlocked", 1), GameView.MAX_LEVEL)); }

    void installCrashReporter() {
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String txt = "Gem Crush 14.0  " + new java.util.Date() + "\n" + sw;
                sp.edit().putString("last_crash", txt.length() > 6000 ? txt.substring(0, 6000) : txt).commit();
            } catch (Throwable ignored) { }
            if (old != null) old.uncaughtException(t, e);
        });
    }

    void showLastCrash() {
        final String c = sp.getString("last_crash", "");
        if (c.isEmpty()) return;
        sp.edit().remove("last_crash").apply();
        Dlg dg = new Dlg("GAME CLOSED", 0xFFD32F2F);
        dg.add(label("Sorry, the game closed unexpectedly last time. Please send this report to the developer so it can be fixed.", 13, 0xFFE9E0FF, false));
        TextView tv = new TextView(this);
        tv.setText(c);
        tv.setTextSize(9);
        tv.setTextColor(0xFFFFFFFF);
        tv.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x55000000);
        g.setCornerRadius(dp(10));
        tv.setBackground(g);
        dg.add(tv);
        dg.buttonKeep("COPY REPORT", 0xFF1E88E5, false, () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("crash", c));
            toast("Report copied");
        });
        dg.button("CLOSE", 0xFF6B5B95, false, null);
        dg.show();
    }

    void popIn(View v, int delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(dp(16));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(340).start();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    void showText(String title, String msg) {
        new AlertDialog.Builder(this, R.style.GemDialog).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    void toMap() {
        gv.setVisibility(View.GONE);
        home.setVisibility(View.VISIBLE);
        updUi();
        map.invalidate();
        scrollToCurrent();
        refreshFriends();
        scheduleInvSync();
    }

    // ------------------------------------------------------------ lives
    void refreshLives() {
        int l = sp.getInt("lives", MAX_LIVES);
        long t = sp.getLong("lifeT", 0);
        if (l < MAX_LIVES) {
            long now = System.currentTimeMillis();
            if (t == 0) t = now;
            long gained = (now - t) / REGEN;
            if (gained > 0) {
                l = (int) Math.min(MAX_LIVES, l + gained);
                t = l >= MAX_LIVES ? 0 : t + gained * REGEN;
            }
        } else {
            t = 0;
        }
        sp.edit().putInt("lives", l).putLong("lifeT", t).apply();
    }

    int lives() { refreshLives(); return sp.getInt("lives", MAX_LIVES); }

    void loseLife() {
        refreshLives();
        int l = sp.getInt("lives", MAX_LIVES);
        if (l <= 0) return;
        long t = sp.getLong("lifeT", 0);
        if (l == MAX_LIVES || t == 0) t = System.currentTimeMillis();
        sp.edit().putInt("lives", l - 1).putLong("lifeT", t).apply();
    }

    void addLives(int n) {
        refreshLives();
        int l = Math.min(MAX_LIVES, sp.getInt("lives", MAX_LIVES) + n);
        long t = l >= MAX_LIVES ? 0 : sp.getLong("lifeT", System.currentTimeMillis());
        sp.edit().putInt("lives", l).putLong("lifeT", t).apply();
    }

    String mmss(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    int totalStars() {
        int s = 0;
        for (int i = 1; i <= GameView.MAX_LEVEL; i++) s += sp.getInt("stars_" + i, 0);
        return s;
    }

    String starStr(int s) {
        String st = "";
        for (int k = 0; k < 3; k++) st += k < s ? "★" : "☆";
        return st;
    }

    long todayDay() {
        long now = System.currentTimeMillis();
        return (now + java.util.TimeZone.getDefault().getOffset(now)) / 86400000L;
    }

    void updUi() {
        if (topBar == null || nav == null) return;
        int l = lives();
        topBar.lives = l;
        if (l < MAX_LIVES) {
            long t = sp.getLong("lifeT", System.currentTimeMillis());
            topBar.timer = mmss(REGEN - (System.currentTimeMillis() - t));
        } else {
            topBar.timer = "FULL";
        }
        topBar.coinsN = coins();
        topBar.starsN = totalStars();
        topBar.ham = sp.getInt("hammers", 3);
        topBar.shuf = sp.getInt("shuffles", 2);
        boolean in = loggedIn();
        topBar.name = in ? sp.getString("uname", "Player") : "Guest";
        topBar.sub = in ? "Code " + sp.getString("ucode", "") : "Tap to login";
        topBar.avatar = in ? myAvatar : null;
        topBar.invalidate();
        nav.dotGift = sp.getLong("dailyDay", -10) != todayDay();
        nav.dotWheel = sp.getLong("spinDay", -10) != todayDay();
        nav.invalidate();
    }

    void dailyGift() {
        long today = todayDay();
        long last = sp.getLong("dailyDay", -10);
        if (last == today) { toast("Come back tomorrow for your next gift!"); return; }
        int streak = (last == today - 1) ? sp.getInt("streak", 0) + 1 : 1;
        int extraH = Math.min(streak - 1, 3);
        scheduleInvSync();
        sp.edit().putLong("dailyDay", today).putInt("streak", streak)
                .putInt("hammers", sp.getInt("hammers", 3) + 1 + extraH)
                .putInt("moves5", sp.getInt("moves5", 1) + 1)
                .putInt("shuffles", sp.getInt("shuffles", 2) + 1).apply();
        addLives(2);
        updUi();
        sfx.coin();
        Dlg dg = new Dlg("DAILY GIFT", 0xFFE91E63);
        dg.add(new GiftView(this));
        dg.add(label("Day " + streak + " streak!", 20, 0xFFFFE082, true), 150);
        dg.add(streakRow(streak), 230);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        View[] cards = {rewardCard(Icons.HEART, "+2"), rewardCard(Icons.HAMMER, "+" + (1 + extraH)),
                rewardCard(Icons.SHUFFLE, "+1"), rewardCard(Icons.MOVES, "+1")};
        for (int i = 0; i < cards.length; i++) { row.addView(cards[i]); popIn(cards[i], 350 + i * 130); }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(10);
        dg.addLp(row, rlp, 300);
        dg.add(label("Come back every day for bigger rewards!", 13, 0xFFE9E0FF, false), 900);
        dg.button("COLLECT!", 0xFF2EBF4A, true, null);
        dg.show();
    }

    void noLives() {
        long t = sp.getLong("lifeT", System.currentTimeMillis());
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("No lives left!")
                .setMessage("Next life in " + mmss(REGEN - (System.currentTimeMillis() - t))
                        + ".\nRefill in the Shop (120 coins), claim your daily gift" + (loggedIn() ? " or collect lives from friends." : "."))
                .setPositiveButton("Daily gift", (d, w) -> dailyGift())
                .setNeutralButton(loggedIn() ? "Collect from friends" : "OK", (d, w) -> { if (loggedIn()) collectGifts(); })
                .setNegativeButton("Close", null)
                .show();
    }

    // ------------------------------------------------------------ server plumbing
    static final String DEFAULT_SERVER = "https://abruar.online/gempop";

    String server() {
        String u = sp.getString("server", "");
        return u.isEmpty() ? DEFAULT_SERVER : u;
    }

    boolean loggedIn() { return !sp.getString("token", "").isEmpty(); }

    void call(String action, Api.CB cb, String... kv) {
        Map<String, String> m = new HashMap<>();
        m.put("a", action);
        m.put("token", sp.getString("token", ""));
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        Api.post(server(), "api.php", m, cb);
    }

    /** Callback that runs on the UI thread and shows errors. */
    abstract class UI implements Api.CB {
        public void ok(final JSONObject o) {
            runOnUiThread(() -> {
                if (isFinishing()) return;
                try { done(o); } catch (Exception e) { toast("Error: " + e.getMessage()); }
            });
        }
        public void err(final String e) { runOnUiThread(() -> handleErr(e)); }
        abstract void done(JSONObject o) throws Exception;
    }

    void handleErr(String e) {
        if (e != null && (e.contains("Not logged in") || e.contains("Session expired"))) {
            logout();
            toast("Please login again");
        } else {
            toast("Error: " + e);
        }
    }

    void askServer(final Runnable then) {
        final EditText et = new EditText(this);
        et.setHint("https://yourdomain.com/gempop");
        et.setText(server());
        et.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("Server URL (no trailing slash)")
                .setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    String u = et.getText().toString().trim();
                    while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
                    sp.edit().putString("server", u).apply();
                    if (!u.isEmpty() && then != null) then.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------ Google login
    void loginGoogle() {
        if (server().isEmpty()) { askServer(this::loginGoogle); return; }
        String ch = "0123456789abcdef";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) sb.append(ch.charAt(r.nextInt(16)));
        sp.edit().putString("nonce", sb.toString()).apply();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(server() + "/auth.php?a=start&nonce=" + sb)));
        } catch (Exception e) {
            toast("Could not open browser: " + e.getMessage());
        }
    }

    void claim(final boolean silent) {
        String nonce = sp.getString("nonce", "");
        if (nonce.isEmpty() || server().isEmpty() || claiming) return;
        claiming = true;
        Map<String, String> m = new HashMap<>();
        m.put("a", "claim");
        m.put("nonce", nonce);
        Api.post(server(), "auth.php", m, new Api.CB() {
            public void ok(final JSONObject o) {
                runOnUiThread(() -> {
                    claiming = false;
                    if (o.optBoolean("pending")) {
                        if (!silent) toast("Login not finished yet. Finish it in the browser, then come back.");
                        return;
                    }
                    sp.edit().putString("token", o.optString("token")).putString("uname", o.optString("name"))
                            .putString("ucode", o.optString("code")).putString("upic", o.optString("pic")).remove("nonce").apply();
                    loadMyAvatar();
                    toast("Welcome " + o.optString("name") + "!");
                    pushProgress();
                    syncInventory();
                    refreshFriends();
                    updUi();
                });
            }

            public void err(final String e) {
                runOnUiThread(() -> {
                    claiming = false;
                    if (!silent) toast("Login error: " + e);
                });
            }
        });
    }

    void logout() {
        sp.edit().remove("token").remove("uname").remove("ucode").remove("upic").remove("nonce").apply();
        myAvatar = null;
        friends = new ArrayList<>();
        map.invalidate();
        updUi();
    }

    /** Upload local progress, merge server progress back (works across phones / reinstalls). */
    void pushProgress() {
        if (!loggedIn()) return;
        JSONObject lv = new JSONObject();
        try {
            for (int i = 1; i <= GameView.MAX_LEVEL; i++) {
                int s = sp.getInt("stars_" + i, 0);
                if (s > 0) lv.put(String.valueOf(i), new JSONArray().put(s).put(sp.getInt("lscore_" + i, 0)));
            }
        } catch (Exception ignored) { }
        call("sync", new Api.CB() {
            public void ok(final JSONObject o) {
                runOnUiThread(() -> {
                    SharedPreferences.Editor ed = sp.edit();
                    ed.putInt("unlocked", Math.max(sp.getInt("unlocked", 1), o.optInt("unlocked", 1)));
                    JSONObject lvs = o.optJSONObject("levels");
                    if (lvs != null) {
                        Iterator<String> it = lvs.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            JSONArray a = lvs.optJSONArray(k);
                            if (a == null) continue;
                            if (a.optInt(0) > sp.getInt("stars_" + k, 0)) ed.putInt("stars_" + k, a.optInt(0));
                            if (a.optInt(1) > sp.getInt("lscore_" + k, 0)) ed.putInt("lscore_" + k, a.optInt(1));
                        }
                    }
                    ed.apply();
                    map.invalidate();
                    updUi();
                });
            }

            public void err(String e) { }
        }, "unlocked", String.valueOf(sp.getInt("unlocked", 1)), "levels", lv.toString());
    }

    void refreshFriends() {
        if (!loggedIn()) return;
        call("friends", new Api.CB() {
            public void ok(JSONObject o) {
                JSONArray arr = o.optJSONArray("friends");
                final List<Friend> list = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject j = arr.optJSONObject(i);
                        if (j == null) continue;
                        Friend f = new Friend();
                        f.id = j.optInt("id");
                        f.name = j.optString("name");
                        f.pic = j.optString("pic");
                        f.unlocked = j.optInt("unlocked", 1);
                        f.stars = j.optInt("stars");
                        list.add(f);
                    }
                }
                friends = list;
                runOnUiThread(() -> map.invalidate());
                for (Friend f : list) {
                    if (f.pic != null && !f.pic.isEmpty() && !avatars.containsKey(f.pic)) {
                        Bitmap b = Api.bitmap(f.pic);
                        if (b != null) {
                            avatars.put(f.pic, b);
                            runOnUiThread(() -> map.invalidate());
                        }
                    }
                }
            }

            public void err(String e) { }
        });
    }

    // ------------------------------------------------------------ social dialogs
    void socialDialog() {
        if (!loggedIn()) {
            accountDialog();
            return;
        }
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("Friend code: " + sp.getString("ucode", ""))
                .setItems(new String[]{"Friends progress", "World top 20", "Add friend by code",
                        "Invite friends (share my code)", "Send a life to a friend",
                        "Collect lives from friends", "Logout"}, (d, w) -> {
                    switch (w) {
                        case 0: call("friends", new UI() {
                            void done(JSONObject o) { showText("Friends progress", progressText(o.optJSONArray("board"))); }
                        }); break;
                        case 1: call("world_top", new UI() {
                            void done(JSONObject o) { showText("World top 20", progressText(o.optJSONArray("board"))); }
                        }); break;
                        case 2: addFriend(); break;
                        case 3: invite(); break;
                        case 4: sendLife(); break;
                        case 5: collectGifts(); break;
                        default: logout();
                    }
                })
                .show();
    }

    String progressText(JSONArray a) {
        if (a == null || a.length() == 0) return "No players yet.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.optJSONObject(i);
            if (j == null) continue;
            sb.append(i + 1).append(". ").append(j.optString("name")).append(j.optBoolean("me") ? " (you)" : "")
                    .append("   Level ").append(j.optInt("unlocked")).append("   ★").append(j.optInt("stars")).append("\n");
        }
        return sb.toString();
    }

    String levelBoardText(JSONArray a) {
        if (a == null || a.length() == 0) return "No friends have played this level yet.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.optJSONObject(i);
            if (j == null) continue;
            sb.append(i + 1).append(". ").append(j.optString("name")).append(j.optBoolean("me") ? " (you)" : "")
                    .append("   ").append(j.optInt("score")).append("  ").append(starStr(j.optInt("stars"))).append("\n");
        }
        return sb.toString();
    }

    void addFriend() {
        final EditText et = new EditText(this);
        et.setHint("Friend code e.g. K7M2QX");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("Add friend")
                .setView(et)
                .setPositiveButton("Add", (d, w) -> call("add_friend", new UI() {
                    void done(JSONObject o) {
                        toast("Added " + o.optString("name") + "!");
                        refreshFriends();
                    }
                }, "code", et.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    void invite() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "Play Gem Crush with me! Add my friend code: " + sp.getString("ucode", ""));
        startActivity(Intent.createChooser(i, "Invite friends"));
    }

    void sendLife() {
        final List<Friend> fl = friends;
        if (fl.isEmpty()) { toast("Add a friend first"); return; }
        String[] items = new String[fl.size()];
        for (int i = 0; i < items.length; i++) items[i] = fl.get(i).name + "  (Level " + fl.get(i).unlocked + ")";
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("Send a life to...")
                .setItems(items, (d, which) -> {
                    final Friend f = fl.get(which);
                    call("send_gift", new UI() {
                        void done(JSONObject o) { toast("Life sent to " + f.name + "!"); }
                    }, "friend_id", String.valueOf(f.id));
                })
                .show();
    }

    void collectGifts() {
        if (!loggedIn()) { toast("Login first"); return; }
        call("collect_gifts", new UI() {
            void done(JSONObject o) {
                JSONArray a = o.optJSONArray("names");
                if (a == null || a.length() == 0) { toast("No gifts yet. Ask friends to send you a life!"); return; }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < a.length(); i++) sb.append(i > 0 ? ", " : "").append(a.optString(i));
                addLives(a.length());
                updUi();
                toast("+" + a.length() + " life from " + sb);
            }
        });
    }

    // ------------------------------------------------------------ play flow
    boolean wantStripe, wantBomb;

    void levelInfo(final int lv) {
        int unlocked = Math.min(sp.getInt("unlocked", 1), GameView.MAX_LEVEL);
        if (lv > unlocked) { toast("Locked! Finish level " + unlocked + " first."); return; }
        GameView.Level L = GameView.forLevel(lv);
        int stars = sp.getInt("stars_" + lv, 0), best = sp.getInt("lscore_" + lv, 0);
        int w = GameView.world(lv);
        Dlg dg = new Dlg("LEVEL " + lv + (L.boss ? "  BOSS" : ""), L.boss ? 0xFFD81B60 : 0xFFFF8F00);
        dg.add(label("World " + (w + 1) + "  -  " + GameView.worldName(w), 14, 0xFFE9E0FF, true), 60);
        dg.add(new StarsView(this, stars, false), 100);
        if (best > 0) dg.add(label("Best score: " + best, 13, 0xFFFFE082, true), 120);
        dg.add(label("GOAL", 12, 0xFFFFD54F, true), 140);
        int dly = 160;
        if (L.target > 0) dg.add(goalPill(0, Icons.STAR, 0, "Score " + L.target + " points"), dly += 50);
        if (L.jelly) dg.add(goalPill(2, 0, 0, "Clear all the jelly"), dly += 50);
        if (L.ing > 0) dg.add(goalPill(1, 7, 0, "Bring down " + L.ing + " cherries"), dly += 50);
        for (int i = 0; i < L.ordCount; i++)
            dg.add(goalPill(1, L.ordT[i], 0, "Collect " + L.ordN[i] + " " + GameView.CNAME[L.ordT[i]] + " candies"), dly += 50);
        if (L.blocks > 0) dg.add(goalPill(1, 8, Math.min(3, L.blockHp), "Smash " + L.blocks + " choco blocks"), dly += 50);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER);
        TextView p1 = textPill(L.moves + " MOVES", 0xFFFFB74D, 0xFFE65100, 0xFFFFFFFF);
        TextView p2 = textPill("3 stars: " + L.s3, 0xFFFFE082, 0xFFFFA000, 0xFF5A3A00);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMargins(dp(3), dp(6), dp(3), dp(4));
        info.addView(p1, lp1);
        info.addView(p2, lp1);
        dg.add(info, dly += 50);
        if (!L.twist.isEmpty()) dg.add(textPill("TWIST: " + L.twist, 0xFFFF5252, 0xFFC62828, 0xFFFFFFFF), dly += 50);

        dg.add(label("START BOOSTERS", 12, 0xFFFFD54F, true), dly += 40);
        final boolean[] want = new boolean[2];
        LinearLayout boost = new LinearLayout(this);
        boost.setOrientation(LinearLayout.HORIZONTAL);
        boost.addView(boostCard(want, 0, 0, 1, sp.getInt("pb_stripe", 1), "2 Striped"));
        boost.addView(boostCard(want, 1, 6, 0, sp.getInt("pb_bomb", 1), "Color bomb"));
        dg.add(boost, dly += 50);

        dg.button("PLAY", 0xFF2EBF4A, true, () -> {
            wantStripe = want[0];
            wantBomb = want[1];
            tryLevel(lv);
        });
        if (loggedIn()) {
            dg.button("FRIENDS' SCORES", 0xFF1E88E5, false, () -> call("level_board", new UI() {
                void done(JSONObject o) { showText("Level " + lv + " - friends", levelBoardText(o.optJSONArray("board"))); }
            }, "level", String.valueOf(lv)));
        }
        dg.button("CLOSE", 0xFF6B5B95, false, null);
        dg.show();
    }

    void tryLevel(int lv) {
        if (lives() <= 0) { wantStripe = false; wantBomb = false; toMap(); noLives(); return; }
        gv.startStripes = 0;
        gv.startBomb = 0;
        if (wantStripe && sp.getInt("pb_stripe", 1) > 0) {
            sp.edit().putInt("pb_stripe", sp.getInt("pb_stripe", 1) - 1).apply();
            gv.startStripes = 2;
        }
        if (wantBomb && sp.getInt("pb_bomb", 1) > 0) {
            sp.edit().putInt("pb_bomb", sp.getInt("pb_bomb", 1) - 1).apply();
            gv.startBomb = 1;
        }
        wantStripe = false;
        wantBomb = false;
        gv.hammers = sp.getInt("hammers", 3);
        gv.shuffles = sp.getInt("shuffles", 2);
        gv.moves5 = sp.getInt("moves5", 1);
        gv.crossBlast = sp.getInt("crossBlast", 1);
        gv.colorChg = sp.getInt("colorChg", 1);
        String tp = GameView.tipFor(lv);
        gv.tip = (tp != null && !sp.getBoolean("tip_" + lv, false)) ? tp : null;
        if (gv.tip != null) sp.edit().putBoolean("tip_" + lv, true).apply();
        home.setVisibility(View.GONE);
        gv.setVisibility(View.VISIBLE);
        gv.newGame(lv);
    }

    @Override
    public void onHammerUsed() {
        sp.edit().putInt("hammers", Math.max(0, sp.getInt("hammers", 3) - 1))
                .putInt("stat_boosters", sp.getInt("stat_boosters", 0) + 1).apply();
        scheduleInvSync();
    }

    @Override
    public void onShuffleUsed() {
        sp.edit().putInt("shuffles", Math.max(0, sp.getInt("shuffles", 2) - 1))
                .putInt("stat_boosters", sp.getInt("stat_boosters", 0) + 1).apply();
        scheduleInvSync();
    }

    @Override
    public void onStatCount(String key, int n) {
        sp.edit().putInt("stat_" + key, sp.getInt("stat_" + key, 0) + n).apply();
    }

    @Override
    public void onCrossUsed() {
        sp.edit().putInt("crossBlast", Math.max(0, sp.getInt("crossBlast", 1) - 1))
                .putInt("stat_boosters", sp.getInt("stat_boosters", 0) + 1).apply();
        scheduleInvSync();
    }

    @Override
    public void onColorUsed() {
        sp.edit().putInt("colorChg", Math.max(0, sp.getInt("colorChg", 1) - 1))
                .putInt("stat_boosters", sp.getInt("stat_boosters", 0) + 1).apply();
        scheduleInvSync();
    }

    @Override
    public void onChest() {
        addCoins(10);
    }

    void winDialog(final int lv, int stars, final int score, int coin, boolean gotHammer, JSONArray board) {
        Dlg dg = new Dlg("LEVEL " + lv + " COMPLETE!", 0xFF2EBF4A);
        dg.cancelable(false);
        dg.add(new StarsView(this, stars, true), 0);
        dg.add(label("SCORE", 12, 0xFFB9A8FF, true), 300);
        final TextView sc = label("0", 38, 0xFFFFFFFF, true);
        sc.setShadowLayer(6, 0, 3, 0xFF000000);
        dg.add(sc, 360);
        ValueAnimator va = ValueAnimator.ofInt(0, score);
        va.setStartDelay(500);
        va.setDuration(1300);
        va.addUpdateListener(an -> sc.setText(String.valueOf((Integer) an.getAnimatedValue())));
        va.start();
        LinearLayout rw = new LinearLayout(this);
        rw.setOrientation(LinearLayout.HORIZONTAL);
        rw.setGravity(Gravity.CENTER);
        rw.addView(rewardCard(Icons.COIN, "+" + coin));
        if (gotHammer) rw.addView(rewardCard(Icons.HAMMER, "+1"));
        dg.add(rw, 520);
        if (board != null && board.length() > 0) {
            dg.add(label("FRIENDS ON THIS LEVEL", 12, 0xFFFFD54F, true), 600);
            TextView bt = new TextView(this);
            bt.setText(levelBoardText(board));
            bt.setTextSize(14);
            bt.setTextColor(0xFFFFFFFF);
            bt.setPadding(dp(14), dp(10), dp(14), dp(10));
            GradientDrawable g = new GradientDrawable();
            g.setColor(0x44000000);
            g.setCornerRadius(dp(14));
            bt.setBackground(g);
            dg.add(bt, 640);
        }
        if (lv < GameView.MAX_LEVEL) dg.button("NEXT LEVEL", 0xFF2EBF4A, true, () -> tryLevel(lv + 1));
        dg.button("REPLAY", 0xFF1E88E5, false, () -> tryLevel(lv));
        dg.button("MAP", 0xFF6B5B95, false, () -> toMap());
        dg.show();
        sfx.coin();
    }

    void failDialog(final int lv, int score) {
        Dlg dg = new Dlg("OUT OF MOVES", 0xFFD32F2F);
        dg.cancelable(false);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(84), dp(84));
        il.gravity = Gravity.CENTER_HORIZONTAL;
        dg.addLp(new IconView(this, 1, 0, 0), il, 60);
        dg.add(label("So close!", 22, 0xFFFFE082, true), 120);
        dg.add(label("Score " + score, 17, 0xFFFFFFFF, true), 200);
        final int extra = sp.getInt("moves5", 1);
        if (extra > 0) {
            dg.button("+5 MOVES   (x" + extra + ")", 0xFFFF8F00, true, () -> {
                sp.edit().putInt("moves5", extra - 1).apply();
                gv.moves5 = extra - 1;
                scheduleInvSync();
                gv.extraMoves(5);
            });
        } else if (coins() >= 70) {
            dg.button("+5 MOVES   (70 coins)", 0xFFFF8F00, true, () -> { if (spend(70)) gv.extraMoves(5); });
        }
        dg.button("TRY AGAIN", 0xFF2EBF4A, extra <= 0 && coins() < 70, () -> {
            loseLife();
            if (lives() > 0) tryLevel(lv); else { toMap(); noLives(); }
        });
        dg.button("MAP", 0xFF6B5B95, false, () -> { loseLife(); toMap(); });
        dg.show();
    }

    static long weekId() {
        long now = System.currentTimeMillis();
        return (now + java.util.TimeZone.getDefault().getOffset(now)) / (7 * 86400000L);
    }

    static int weeklyLevel() { return 3000 + (int) ((weekId() % 200) * 15); }

    @Override
    public void onResult(boolean won, int stars, final int score, String msg) {
        if (isFinishing() || isDestroyed()) return;
        final int lv = gv.L.n;
        if (won) {
            boolean firstClear = sp.getInt("stars_" + lv, 0) == 0;
            int streak = sp.getInt("winStreak", 0) + 1;
            int streakBonus = streak % 5 == 0 ? 50 : 0;
            boolean weekly = lv == weeklyLevel() && sp.getLong("wkDone", -10) != weekId();
            final int coin = 10 + stars * 10 + (firstClear ? 30 : 0) + streakBonus + (weekly ? 200 : 0);
            SharedPreferences.Editor ed = sp.edit();
            if (stars > sp.getInt("stars_" + lv, 0)) ed.putInt("stars_" + lv, stars);
            if (score > sp.getInt("lscore_" + lv, 0)) ed.putInt("lscore_" + lv, score);
            if (score > sp.getInt("hiscore", 0)) ed.putInt("hiscore", score);
            if (lv >= sp.getInt("unlocked", 1) && lv < GameView.MAX_LEVEL) ed.putInt("unlocked", lv + 1);
            final boolean hammer = stars == 3;
            if (hammer) ed.putInt("hammers", sp.getInt("hammers", 3) + 1);
            ed.putInt("coins", sp.getInt("coins", 0) + coin);
            ed.putInt("winStreak", streak);
            ed.putInt("stat_wins", sp.getInt("stat_wins", 0) + 1);
            if (weekly) ed.putLong("wkDone", weekId());
            ed.apply();
            if (streakBonus > 0) toast("Win streak x" + streak + "! Bonus +" + streakBonus + " coins");
            if (weekly) toast("Weekly challenge complete! +200 coins");
            scheduleInvSync();
            final int fs = stars;
            if (loggedIn()) {
                call("level_result", new Api.CB() {
                    public void ok(final JSONObject o) {
                        runOnUiThread(() -> winDialog(lv, fs, score, coin, hammer, o.optJSONArray("board")));
                    }

                    public void err(String e) { runOnUiThread(() -> winDialog(lv, fs, score, coin, hammer, null)); }
                }, "level", String.valueOf(lv), "stars", String.valueOf(stars),
                        "score", String.valueOf(score), "won", "1");
            } else {
                winDialog(lv, fs, score, coin, hammer, null);
            }
        } else {
            sp.edit().putInt("winStreak", 0).putInt("stat_losses", sp.getInt("stat_losses", 0) + 1).apply();
            failDialog(lv, score);
        }
    }

    // ------------------------------------------------------------ coins, shop, lucky wheel, awards
    final Random rndSpin = new Random();

    int coins() { return sp.getInt("coins", 0); }

    void addCoins(int n) { sp.edit().putInt("coins", coins() + n).apply(); updUi(); scheduleInvSync(); }

    boolean spend(int n) {
        if (coins() < n) { toast("Not enough coins (you have " + coins() + ")"); return false; }
        sp.edit().putInt("coins", coins() - n).apply();
        updUi();
        scheduleInvSync();
        return true;
    }

    void addItem(String key, int def, int n) { sp.edit().putInt(key, sp.getInt(key, def) + n).apply(); scheduleInvSync(); }

    @Override
    public void onStat(String key) {
        sp.edit().putInt("stat_" + key, sp.getInt("stat_" + key, 0) + 1).apply();
    }

    int stat(String k) { return sp.getInt("stat_" + k, 0); }

    View priceChip(int cost, View.OnClickListener l) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(8), dp(4), dp(12), dp(4));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFE082, 0xFFFFA000});
        g.setCornerRadius(dp(18));
        g.setStroke(dp(2), 0xFF8A5A00);
        c.setBackground(g);
        c.addView(new IconView(this, 0, Icons.COIN, 0), new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView t = new TextView(this);
        t.setText(String.valueOf(cost));
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF5A3A00);
        c.addView(t);
        c.setOnClickListener(l);
        return c;
    }

    void shopDialog() {
        if (shopDlg != null && shopDlg.isShowing()) shopDlg.dismiss();
        final String[] names = {"Hammer", "Shuffle", "+5 Moves", "Striped start", "Color bomb start",
                "Cross Blaster", "Color Changer", "Full lives"};
        final String[] desc = {"Smash any candy", "Mix up the board", "Extra moves in a level", "Begin with 2 striped candies",
                "Begin with a color bomb", "Tap any tile to fire a row + column blast",
                "Tap a candy to change its color", "Refill all 5 hearts"};
        final int[] cost = {60, 50, 70, 80, 100, 90, 90, 120};
        final int[] kind = {0, 0, 0, 1, 1, 0, 0, 0},
                ia = {Icons.HAMMER, Icons.SHUFFLE, Icons.MOVES, 0, 6, Icons.CROSS, Icons.PALETTE, Icons.HEART};
        final int[] ib = {0, 0, 0, 1, 0, 0, 0, 0};
        final int[] own = {sp.getInt("hammers", 3), sp.getInt("shuffles", 2), sp.getInt("moves5", 1),
                sp.getInt("pb_stripe", 1), sp.getInt("pb_bomb", 1), sp.getInt("crossBlast", 1),
                sp.getInt("colorChg", 1), -1};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(6), dp(14), dp(6));
        TextView head = new TextView(this);
        head.setText("Your coins: " + coins());
        head.setTextSize(17);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextColor(0xFFFFE082);
        head.setPadding(dp(4), 0, 0, dp(8));
        box.addView(head);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            GradientDrawable bgd = new GradientDrawable();
            bgd.setColor(0x33FFFFFF);
            bgd.setCornerRadius(dp(16));
            row.setBackground(bgd);
            row.addView(new IconView(this, kind[i], ia[i], ib[i]), new LinearLayout.LayoutParams(dp(52), dp(52)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(10), 0, dp(6), 0);
            TextView n = new TextView(this);
            n.setText(names[i] + (own[i] >= 0 ? "   (you have " + own[i] + ")" : ""));
            n.setTextSize(15);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            n.setTextColor(0xFFFFFFFF);
            TextView d = new TextView(this);
            d.setText(desc[i]);
            d.setTextSize(12);
            d.setTextColor(0xFFD8CCFF);
            col.addView(n);
            col.addView(d);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(priceChip(cost[i], v -> buyItem(idx, names[idx], cost[idx])));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(8));
            box.addView(row, lp);
            popIn(row, 80 + i * 70);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        shopDlg = new AlertDialog.Builder(this, R.style.GemDialog).setTitle("Shop").setView(sv).setNegativeButton("Close", null).create();
        shopDlg.show();
    }

    void buyItem(int i, String name, int cost) {
        if (!spend(cost)) return;
        switch (i) {
            case 0: addItem("hammers", 3, 1); break;
            case 1: addItem("shuffles", 2, 1); break;
            case 2: addItem("moves5", 1, 1); break;
            case 3: addItem("pb_stripe", 1, 1); break;
            case 4: addItem("pb_bomb", 1, 1); break;
            case 5: addItem("crossBlast", 1, 1); break;
            case 6: addItem("colorChg", 1, 1); break;
            default: sp.edit().putInt("lives", MAX_LIVES).putLong("lifeT", 0).apply(); scheduleInvSync();
        }
        sfx.coin();
        updUi();
        toast("Bought: " + name);
        shopDialog();
    }

    static final String[] SPIN_LABEL = {"20 coins", "Hammer", "50 coins", "Shuffle", "+5 Moves", "1 Life", "100 coins", "Bomb start"};
    static final int[] SPIN_COL = {0xFFEF5350, 0xFF7E57C2, 0xFFFFA726, 0xFF26A69A, 0xFF42A5F5, 0xFFEC407A, 0xFFFFCA28, 0xFF66BB6A};
    static final int[] SPIN_W = {18, 14, 14, 12, 10, 8, 14, 10};

    static final int[] SPIN_ICON = {Icons.COIN, Icons.HAMMER, Icons.COIN, Icons.SHUFFLE, Icons.MOVES, Icons.HEART, Icons.COIN, -1};

    class WheelView extends View {
        final Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF wr = new RectF();
        final Path tri = new Path();
        float angle;
        boolean spinning;

        WheelView(Context c) { super(c); }

        @Override
        protected void onMeasure(int wm, int hm) { setMeasuredDimension(dp(300), dp(310)); }

        @Override
        protected void onDraw(Canvas cv) {
            long now = android.os.SystemClock.uptimeMillis();
            float cx = getWidth() / 2f, cy = getHeight() / 2f + dp(6), rim = Math.min(cx, cy) - dp(14), r = rim - dp(16);
            wp.setStyle(Paint.Style.FILL);
            wp.setColor(0x66000000);
            cv.drawCircle(cx, cy + dp(6), rim, wp);
            wp.setColor(0xFFFFFFFF);
            wp.setShader(new RadialGradient(cx - rim * 0.3f, cy - rim * 0.4f, rim * 1.6f,
                    new int[]{0xFFFFF59D, 0xFFFFB300, 0xFF8A5A00}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, rim, wp);
            wp.setShader(null);
            int n = 16;
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                float bx = cx + (float) Math.cos(a) * (rim - dp(8)), by = cy + (float) Math.sin(a) * (rim - dp(8));
                boolean on = (i + (int) (now / (spinning ? 80 : 320))) % 2 == 0;
                if (on) { wp.setColor(0x55FFFFFF); cv.drawCircle(bx, by, dp(7), wp); }
                wp.setColor(on ? 0xFFFFFFFF : 0xFFB8860B);
                cv.drawCircle(bx, by, dp(4), wp);
            }
            wr.set(cx - r, cy - r, cx + r, cy + r);
            for (int i = 0; i < 8; i++) {
                int base = SPIN_COL[i];
                wp.setColor(0xFFFFFFFF);
                wp.setShader(new RadialGradient(cx, cy, r, new int[]{Gems.lighten(base, 0.5f), base, Gems.darken(base, 0.28f)},
                        new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
                cv.drawArc(wr, angle - 90 + i * 45, 45, true, wp);
                wp.setShader(null);
            }
            wp.setStyle(Paint.Style.STROKE);
            wp.setStrokeWidth(dp(2));
            wp.setColor(0xAAFFFFFF);
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(angle - 90 + i * 45);
                cv.drawLine(cx, cy, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, wp);
            }
            wp.setStyle(Paint.Style.FILL);
            wp.setTextAlign(Paint.Align.CENTER);
            wp.setTypeface(Typeface.DEFAULT_BOLD);
            for (int i = 0; i < 8; i++) {
                cv.save();
                cv.rotate(angle + i * 45 + 22.5f, cx, cy);
                if (SPIN_ICON[i] >= 0) Icons.draw(cv, wp, SPIN_ICON[i], cx, cy - r * 0.56f, dp(15));
                else Gems.draw(cv, wp, 6, 0, cx, cy - r * 0.56f, dp(13), false);
                wp.setTextSize(dp(10));
                wp.setColor(0xFFFFFFFF);
                wp.setShadowLayer(3, 0, 1, 0xFF000000);
                cv.drawText(SPIN_LABEL[i], cx, cy - r * 0.83f, wp);
                wp.clearShadowLayer();
                cv.restore();
            }
            // glossy shine
            cv.save();
            Path cp = new Path();
            cp.addCircle(cx, cy, r, Path.Direction.CW);
            cv.clipPath(cp);
            wp.setColor(0xFFFFFFFF);
            wp.setShader(new LinearGradient(0, cy - r, 0, cy, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawRect(cx - r, cy - r, cx + r, cy, wp);
            wp.setShader(null);
            cv.restore();
            // hub
            wp.setColor(0xFFFFFFFF);
            wp.setShader(new RadialGradient(cx - dp(6), cy - dp(8), dp(30), new int[]{0xFFFFF59D, 0xFFFFB300, 0xFF8A5A00},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, dp(24), wp);
            wp.setShader(null);
            Icons.draw(cv, wp, Icons.STAR, cx, cy, dp(13));
            // pointer
            float py = cy - rim - dp(4);
            tri.reset();
            tri.moveTo(cx - dp(15), py);
            tri.lineTo(cx + dp(15), py);
            tri.lineTo(cx, py + dp(34));
            tri.close();
            wp.setColor(0x66000000);
            cv.save();
            cv.translate(0, dp(3));
            cv.drawPath(tri, wp);
            cv.restore();
            wp.setColor(0xFFFFFFFF);
            wp.setShader(new LinearGradient(0, py, 0, py + dp(34), 0xFFFF6B6B, 0xFFB00020, Shader.TileMode.CLAMP));
            cv.drawPath(tri, wp);
            wp.setShader(null);
            wp.setStyle(Paint.Style.STROKE);
            wp.setStrokeWidth(dp(3));
            wp.setColor(0xFFFFFFFF);
            cv.drawPath(tri, wp);
            wp.setStyle(Paint.Style.FILL);
            postInvalidateOnAnimation();
        }
    }

    void spinDialog() {
        final long today = todayDay();
        final WheelView wv = new WheelView(this);
        final boolean freeNow = sp.getLong("spinDay", -10) != today;
        final Dlg dg = new Dlg("LUCKY WHEEL", 0xFFD81B60);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(dp(300), dp(310));
        wl.gravity = Gravity.CENTER_HORIZONTAL;
        dg.addLp(wv, wl, 60);
        final TextView result = label(freeNow ? "Your free spin is ready - good luck!" : "Next free spin tomorrow  -  extra spin 40 coins",
                15, 0xFFFFE082, true);
        dg.add(result, 200);
        final boolean[] spinning = {false};
        final Button go = dg.buttonKeep(freeNow ? "SPIN  (FREE)" : "SPIN  (40 coins)", 0xFFFF8F00, true, null);
        final ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(go,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.05f), PropertyValuesHolder.ofFloat("scaleY", 1f, 1.05f));
        pulse.setDuration(550);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        if (freeNow) pulse.start();
        dg.d.setOnDismissListener(di -> pulse.cancel());
        go.setOnClickListener(v -> {
            if (spinning[0]) return;
            boolean free = sp.getLong("spinDay", -10) != today;
            if (!free && !spend(40)) return;
            if (free) sp.edit().putLong("spinDay", today).apply();
            pulse.cancel();
            go.setScaleX(1f);
            go.setScaleY(1f);
            spinning[0] = true;
            wv.spinning = true;
            go.setEnabled(false);
            result.setText("Spinning...");
            int roll = rndSpin.nextInt(100), idx = 0, acc = 0;
            for (int i = 0; i < 8; i++) { acc += SPIN_W[i]; if (roll < acc) { idx = i; break; } }
            final int win = idx;
            float desired = 360f - (idx * 45f + 22.5f) + (rndSpin.nextFloat() - 0.5f) * 30f;
            float a0 = wv.angle;
            float end = a0 - (a0 % 360f) + 360f * 6 + desired;
            ValueAnimator va = ValueAnimator.ofFloat(a0, end);
            va.setDuration(4600);
            va.setInterpolator(new DecelerateInterpolator(1.6f));
            final int[] lastSeg = {(int) (a0 / 45f)};
            va.addUpdateListener(an -> {
                wv.angle = (Float) an.getAnimatedValue();
                int seg = (int) (wv.angle / 45f);
                if (seg != lastSeg[0]) { lastSeg[0] = seg; sfx.swap(); }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator an) {
                    spinning[0] = false;
                    wv.spinning = false;
                    go.setEnabled(true);
                    go.setText("SPIN  (40 coins)");
                    result.setText("You won: " + SPIN_LABEL[win] + "!");
                    result.setTextSize(20);
                    result.setScaleX(0.5f);
                    result.setScaleY(0.5f);
                    result.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator(3f)).setDuration(500).start();
                    grantSpin(win);
                }
            });
            va.start();
        });
        dg.button("CLOSE", 0xFF6B5B95, false, null);
        dg.show();
    }

    void grantSpin(int i) {
        String msg;
        switch (i) {
            case 0: addCoins(20); msg = "+20 coins!"; break;
            case 1: addItem("hammers", 3, 1); msg = "+1 Hammer!"; break;
            case 2: addCoins(50); msg = "+50 coins!"; break;
            case 3: addItem("shuffles", 2, 1); msg = "+1 Shuffle!"; break;
            case 4: addItem("moves5", 1, 1); msg = "+5 Moves booster!"; break;
            case 5: addLives(1); msg = "+1 Life!"; break;
            case 6: addCoins(100); msg = "JACKPOT! +100 coins!"; break;
            default: addItem("pb_bomb", 1, 1); msg = "Color bomb start booster!";
        }
        updUi();
        sfx.win();
        toast(msg);
    }

    static final String[] ACH_T = {"First Victory", "Level 10 cleared", "Star collector", "Star master",
            "Stripe maker", "Bomb squad", "Wrapped wonder", "Combo Master", "Halfway hero", "Century champion", "Block breaker",
            "Gem Collector", "Master Matcher", "Booster Expert", "Star Champion", "Daily Player", "World Explorer"};
    static final String[] ACH_D = {"Win 1 level", "Clear level 10", "Collect 30 stars", "Collect 90 stars",
            "Make 10 striped candies", "Make 3 color bombs", "Make 5 wrapped candies", "Trigger a Divine! cascade",
            "Clear level 50", "Clear level 100", "Smash 25 choco blocks",
            "Match 2,500 candies", "Match 10,000 candies", "Use 25 boosters", "Collect 150 stars",
            "Reach a 7-day login streak", "Reach world 5 (level 101)"};
    static final int[] ACH_N = {1, 11, 30, 90, 10, 3, 5, 1, 51, 101, 25, 2500, 10000, 25, 150, 7, 101};
    static final int[] ACH_R = {20, 50, 50, 150, 40, 60, 40, 50, 120, 300, 80, 100, 300, 80, 200, 100, 150};

    int achValue(int k) {
        switch (k) {
            case 0: {
                int w = 0;
                for (int i = 1; i <= GameView.MAX_LEVEL; i++) if (sp.getInt("stars_" + i, 0) > 0) w++;
                return w;
            }
            case 1: case 8: case 9: case 16: return sp.getInt("unlocked", 1);
            case 2: case 3: case 14: return totalStars();
            case 4: return stat("striped");
            case 5: return stat("bombs");
            case 6: return stat("wrapped");
            case 7: return stat("divine");
            case 11: case 12: return stat("gems");
            case 13: return stat("boosters");
            case 15: return sp.getInt("streak", 0);
            default: return stat("blocks");
        }
    }

    void achievementsDialog() {
        StringBuilder sb = new StringBuilder();
        int gained = 0;
        SharedPreferences.Editor ed = sp.edit();
        for (int k = 0; k < ACH_T.length; k++) {
            int v = achValue(k);
            boolean done = v >= ACH_N[k];
            if (done && !sp.getBoolean("ach_" + k, false)) {
                ed.putBoolean("ach_" + k, true);
                gained += ACH_R[k];
            }
            sb.append(done ? "✓ " : "○ ").append(ACH_T[k]).append("\n    ").append(ACH_D[k]).append("   ")
                    .append(Math.min(v, ACH_N[k])).append("/").append(ACH_N[k])
                    .append("   (+").append(ACH_R[k]).append(" coins)\n\n");
        }
        if (gained > 0) ed.putInt("coins", coins() + gained);
        ed.apply();
        if (gained > 0) { toast("Awards claimed: +" + gained + " coins!"); updUi(); scheduleInvSync(); }
        showText("Awards", sb.toString());
    }

    // ------------------------------------------------------------ inventory backup (server)
    static final String[] INV_I = {"coins", "hammers", "shuffles", "moves5", "pb_stripe", "pb_bomb",
            "crossBlast", "colorChg", "streak",
            "stat_striped", "stat_bombs", "stat_wrapped", "stat_divine", "stat_blocks",
            "stat_gems", "stat_boosters", "stat_chests", "stat_wins", "stat_losses", "stat_super", "stat_lightning"};
    static final int[] INV_D = {0, 3, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    static final String[] INV_L = {"dailyDay", "spinDay"};

    final Runnable invTask = new Runnable() { public void run() { syncInventory(); } };

    void scheduleInvSync() {
        if (!loggedIn() || home == null) return;
        home.removeCallbacks(invTask);
        home.postDelayed(invTask, 2500);
    }

    String invJson(boolean defaults) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i < INV_I.length; i++)
                o.put(INV_I[i], defaults ? INV_D[i] : sp.getInt(INV_I[i], INV_D[i]));
            for (String k : INV_L) o.put(k, defaults ? 0 : (int) Math.max(0L, sp.getLong(k, 0)));
            for (int k = 0; k < ACH_T.length; k++)
                o.put("ach_" + k, !defaults && sp.getBoolean("ach_" + k, false) ? 1 : 0);
        } catch (Exception ignored) { }
        return o.toString();
    }

    void applyInv(String json) {
        try {
            JSONObject o = new JSONObject(json);
            SharedPreferences.Editor ed = sp.edit();
            for (int i = 0; i < INV_I.length; i++) ed.putInt(INV_I[i], o.optInt(INV_I[i], INV_D[i]));
            for (String k : INV_L) ed.putLong(k, o.optLong(k, 0));
            for (int k = 0; k < ACH_T.length; k++) ed.putBoolean("ach_" + k, o.optInt("ach_" + k, 0) == 1);
            ed.apply();
        } catch (Exception ignored) { }
    }

    /** Coins, boosters, streak, awards are backed up on your server (last change wins). */
    void syncInventory() {
        if (!loggedIn()) return;
        final String cur = invJson(false);
        String last = sp.getString("inv_last", "");
        boolean changed = last.isEmpty() ? !cur.equals(invJson(true)) : !cur.equals(last);
        if (changed) sp.edit().putLong("inv_ts", System.currentTimeMillis()).apply();
        call("inv_sync", new Api.CB() {
            public void ok(final JSONObject o) {
                runOnUiThread(() -> {
                    if (o.optBoolean("adopt")) {
                        applyInv(o.optString("inv"));
                        sp.edit().putString("inv_last", invJson(false)).putLong("inv_ts", o.optLong("ts")).apply();
                        updUi();
                    } else {
                        sp.edit().putString("inv_last", cur).putLong("inv_ts", o.optLong("ts", sp.getLong("inv_ts", 0))).apply();
                    }
                });
            }

            public void err(String e) { }
        }, "inv", cur, "ts", String.valueOf(sp.getLong("inv_ts", 0)));
    }

    @Override
    protected void onPause() {
        super.onPause();
        sfx.pauseMusic();
        if (loggedIn()) syncInventory();
    }

    void scrollToLevel(int lv) { gotoLevel(lv); }

    // ------------------------------------------------------------ daily missions + weekly challenge
    static final String[] MIS_KEY = {"gems", "striped", "bombs", "blocks", "wins", "wrapped", "fish"};
    static final String[] MIS_NAME = {"Match # candies", "Make # striped candies", "Make # color bombs",
            "Smash # choco blocks", "Win # levels", "Make # wrapped candies", "Make # fish candies"};
    static final int[][] MIS_GOAL = {{150, 300, 600}, {5, 10, 15}, {2, 3, 5}, {5, 10, 20}, {2, 3, 5}, {3, 6, 10}, {2, 4, 8}};
    static final int[] MIS_REWARD = {30, 50, 80};

    void rollMissions() {
        long today = todayDay();
        if (sp.getLong("misDay", -10) == today) return;
        Random r = new Random(today * 31 + 7);
        int n = MIS_KEY.length;
        int a = r.nextInt(n), b, c;
        do b = r.nextInt(n); while (b == a);
        do c = r.nextInt(n); while (c == a || c == b);
        SharedPreferences.Editor ed = sp.edit().putLong("misDay", today);
        int[] ms = {a, b, c};
        for (int i = 0; i < 3; i++) {
            int[] gl = MIS_GOAL[ms[i]];
            int g = gl[r.nextInt(gl.length)];
            ed.putInt("mis" + i, ms[i]).putInt("mis" + i + "g", g)
                    .putInt("mis" + i + "b", stat(MIS_KEY[ms[i]]))
                    .putBoolean("mis" + i + "c", false);
        }
        ed.apply();
    }

    void missionsDialog() {
        rollMissions();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(6), dp(14), dp(6));
        TextView head = new TextView(this);
        head.setText("New missions every day!");
        head.setTextSize(15);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextColor(0xFFFFE082);
        head.setPadding(dp(4), 0, 0, dp(8));
        box.addView(head);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            int t = sp.getInt("mis" + i, 0), goal = sp.getInt("mis" + i + "g", 1);
            int prog = Math.min(goal, stat(MIS_KEY[t]) - sp.getInt("mis" + i + "b", 0));
            boolean done = prog >= goal, claimed = sp.getBoolean("mis" + i + "c", false);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            GradientDrawable bgd = new GradientDrawable();
            bgd.setColor(0x33FFFFFF);
            bgd.setCornerRadius(dp(16));
            row.setBackground(bgd);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(10), 0, dp(6), 0);
            TextView title = new TextView(this);
            title.setText(MIS_NAME[t].replace("#", String.valueOf(goal)));
            title.setTextSize(15);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(0xFFFFFFFF);
            TextView progTv = new TextView(this);
            progTv.setText((claimed ? "Claimed!" : prog + " / " + goal) + "   reward " + MIS_REWARD[i] + " coins");
            progTv.setTextSize(12);
            progTv.setTextColor(done && !claimed ? 0xFFFFE082 : 0xFFD8CCFF);
            col.addView(title);
            col.addView(progTv);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button b = smallBtn(claimed ? "Done" : done ? "CLAIM" : "...", claimed ? 0xFF6B5B95 : done ? 0xFF2EBF4A : 0xFF5E35B1,
                    claimed ? null : v -> {
                        if (!sp.getBoolean("mis" + idx + "c", false)
                                && stat(MIS_KEY[sp.getInt("mis" + idx, 0)]) - sp.getInt("mis" + idx + "b", 0) >= sp.getInt("mis" + idx + "g", 1)) {
                            sp.edit().putBoolean("mis" + idx + "c", true).apply();
                            addCoins(MIS_REWARD[idx]);
                            sfx.coin();
                            toast("+" + MIS_REWARD[idx] + " coins!");
                        }
                    });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(92), dp(44));
            blp.setMargins(0, 0, 0, 0);
            b.setLayoutParams(blp);
            row.addView(b);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(8));
            box.addView(row, lp);
            popIn(row, 80 + i * 70);
        }
        ScrollView svv = new ScrollView(this);
        svv.addView(box);
        new AlertDialog.Builder(this, R.style.GemDialog).setTitle("Daily missions").setView(svv).setNegativeButton("Close", null).show();
    }

    void weeklyDialog() {
        int lv = weeklyLevel();
        boolean done = sp.getLong("wkDone", -10) == weekId();
        GameView.Level L = GameView.forLevel(lv);
        Dlg dg = new Dlg("WEEKLY CHALLENGE", 0xFF1E88E5);
        dg.add(label("Week " + ((weekId() % 52) + 1) + "  -  Level " + lv, 17, 0xFFFFFFFF, true), 60);
        dg.add(label(GameView.worldName(GameView.world(lv)) + "  -  " + L.moves + " moves", 13, 0xFFE9E0FF, false), 120);
        dg.add(label(done ? "Completed! +200 coins collected.\nA new challenge starts next week!"
                : "Beat this level before the week ends\nfor a +200 coin reward!", 14, done ? 0xFF9CFFB0 : 0xFFFFE082, true), 180);
        if (!done) dg.button("PLAY CHALLENGE", 0xFF2EBF4A, true, () -> tryLevel(lv));
        dg.button("CLOSE", 0xFF6B5B95, false, null);
        dg.show();
    }

    void worldsDialog() {
        int unlocked = Math.min(sp.getInt("unlocked", 1), GameView.MAX_LEVEL);
        int maxW = GameView.world(unlocked);
        String[] items = new String[maxW + 1];
        for (int w = 0; w <= maxW; w++)
            items[w] = "World " + (w + 1) + " - " + GameView.worldName(w) + "   (levels " + (w * 25 + 1) + "-" + (w * 25 + 25) + ")";
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("Jump to world")
                .setItems(items, (d, w) -> scrollToLevel(w * 25 + 1))
                .setNegativeButton("Current level", (d, w) -> scrollToCurrent())
                .show();
    }

    // ------------------------------------------------------------ top bar / nav / account / menus
    void tapTop(int which) {
        switch (which) {
            case 0: accountDialog(); break;
            case 1: achievementsDialog(); break;
            case 2: livesDialog(); break;
            default: shopDialog();
        }
    }

    void tapNav(int i) {
        switch (i) {
            case 0: shopDialog(); break;
            case 1: spinDialog(); break;
            case 2: dailyGift(); break;
            case 3: socialDialog(); break;
            default: moreDialog();
        }
    }

    void toggleSound() {
        sfx.on = !sfx.on;
        sp.edit().putBoolean("sound", sfx.on).apply();
        toast("Sound effects & vibration " + (sfx.on ? "ON" : "OFF"));
    }

    void toggleMusic() {
        sp.edit().putBoolean("music", !sfx.isMusicOn()).apply();
        sfx.setMusic(!sfx.isMusicOn());
        toast("Music " + (sfx.isMusicOn() ? "ON" : "OFF"));
    }

    void moreDialog() {
        String[] items = {"Awards", "Daily missions", "Weekly challenge", "Jump to world", "Settings",
                "How to play", loggedIn() ? "Account / Logout" : "Login with Google"};
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("More")
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0: achievementsDialog(); break;
                        case 1: missionsDialog(); break;
                        case 2: weeklyDialog(); break;
                        case 3: worldsDialog(); break;
                        case 4: settingsDialog(); break;
                        case 5: help(); break;
                        default: accountDialog();
                    }
                })
                .show();
    }

    void settingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        box.addView(actionBtn("Sound effects: " + (sfx.on ? "ON" : "OFF"), 0xFF1E88E5, v -> {
            toggleSound();
            ((Button) v).setText("Sound effects: " + (sfx.on ? "ON" : "OFF"));
        }));
        box.addView(actionBtn("Music: " + (sfx.isMusicOn() ? "ON" : "OFF"), 0xFF1E88E5, v -> {
            toggleMusic();
            ((Button) v).setText("Music: " + (sfx.isMusicOn() ? "ON" : "OFF"));
        }));
        box.addView(label("Sound volume", 13, 0xFFE9E0FF, true));
        SeekBar sv = new SeekBar(this);
        sv.setMax(100);
        sv.setProgress(sp.getInt("svol", 80));
        sv.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                sfx.vol = v / 100f;
                sp.edit().putInt("svol", v).apply();
            }

            public void onStartTrackingTouch(SeekBar s) { }

            public void onStopTrackingTouch(SeekBar s) { sfx.click(); }
        });
        box.addView(sv);
        box.addView(label("Music volume", 13, 0xFFE9E0FF, true));
        SeekBar mv = new SeekBar(this);
        mv.setMax(100);
        mv.setProgress(sp.getInt("mvol", 70));
        mv.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                sfx.setMusicVolume(v / 100f);
                sp.edit().putInt("mvol", v).apply();
            }

            public void onStartTrackingTouch(SeekBar s) { }

            public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(mv);
        box.addView(actionBtn("Reset all progress", 0xFFC62828, v -> confirmReset()));
        ScrollView svv = new ScrollView(this);
        svv.addView(box);
        new AlertDialog.Builder(this, R.style.GemDialog).setTitle("Settings").setView(svv).setNegativeButton("Close", null).show();
    }

    void confirmReset() {
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("Reset everything?")
                .setMessage("Levels, stars, coins, boosters, awards and statistics will be wiped. This cannot be undone.")
                .setPositiveButton("Reset", (d, w) -> {
                    String server = sp.getString("server", "");
                    sp.edit().clear().putString("server", server).apply();
                    sfx.on = true;
                    sfx.vol = 0.8f;
                    sfx.mvol = 0.7f;
                    sfx.setMusicVolume(sfx.mvol);
                    sfx.setMusic(true);
                    myAvatar = null;
                    friends = new ArrayList<>();
                    updUi();
                    map.invalidate();
                    scrollToCurrent();
                    toast("Progress reset");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    Button actionBtn(String text, int color, View.OnClickListener l) {
        Button b = smallBtn(text, color, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, dp(6), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    void copyCode() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("friend code", sp.getString("ucode", "")));
        toast("Friend code copied!");
    }

    void accountDialog() {
        final boolean in = loggedIn();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(22), dp(12), dp(22), dp(4));
        box.addView(new AvatarView(this), new LinearLayout.LayoutParams(dp(92), dp(92)));
        TextView nm = new TextView(this);
        nm.setText(in ? sp.getString("uname", "Player") : "Guest player");
        nm.setTextSize(21);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setTextColor(0xFFFFFFFF);
        nm.setGravity(Gravity.CENTER);
        nm.setPadding(0, dp(8), 0, 0);
        box.addView(nm);
        popIn(nm, 140);
        TextView st = new TextView(this);
        st.setTextSize(13);
        st.setTextColor(0xFFE9E0FF);
        st.setGravity(Gravity.CENTER);
        st.setPadding(0, dp(6), 0, dp(8));
        st.setText(in ? "Logged in with Google\nFriend code: " + sp.getString("ucode", "")
                + "\n\n✓ Progress, coins and boosters are backed up"
                : "Not logged in\n\nYour progress is saved on this phone only.\nLogin with Google to back it up, play with friends and keep your coins if you change phone.");
        box.addView(st);
        popIn(st, 260);
        int tStars = totalStars();
        TextView stats = new TextView(this);
        stats.setTextSize(12);
        stats.setTextColor(0xFFB9A8FF);
        stats.setGravity(Gravity.CENTER);
        stats.setPadding(0, dp(4), 0, dp(8));
        stats.setText("Player level " + (1 + tStars / 20) + "   \u2605" + tStars + " stars"
                + "\nGems matched: " + stat("gems") + "   Games played: " + (stat("wins") + stat("losses"))
                + "\nHighest score: " + sp.getInt("hiscore", 0));
        box.addView(stats);
        popIn(stats, 340);
        final AlertDialog[] dlg = new AlertDialog[1];
        if (in) {
            box.addView(actionBtn("Copy friend code", 0xFF1565C0, v -> copyCode()));
            box.addView(actionBtn("Invite friends", 0xFF00897B, v -> invite()));
            box.addView(actionBtn("Logout", 0xFFC62828, v -> {
                dlg[0].dismiss();
                logout();
                toast("Logged out");
            }));
        } else {
            box.addView(actionBtn("Login with Google", 0xFF2E7D32, v -> { dlg[0].dismiss(); loginGoogle(); }));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg[0] = new AlertDialog.Builder(this, R.style.GemDialog).setTitle("Account").setView(sv).setNegativeButton("Close", null).create();
        dlg[0].show();
    }

    void loadMyAvatar() {
        final String url = sp.getString("upic", "");
        if (url.isEmpty()) { myAvatar = null; return; }
        new Thread(() -> {
            Bitmap b = Api.bitmap(url);
            if (b != null) { myAvatar = b; runOnUiThread(this::updUi); }
        }).start();
    }

    void livesDialog() {
        int l = lives();
        long t = sp.getLong("lifeT", System.currentTimeMillis());
        String msg = l >= MAX_LIVES ? "All 5 hearts are full!"
                : "You have " + l + " of 5 hearts.\nNext heart in " + mmss(REGEN - (System.currentTimeMillis() - t))
                + "\n(1 heart comes back every 25 minutes)";
        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.GemDialog).setTitle("Hearts").setMessage(msg).setNegativeButton("Close", null);
        if (l < MAX_LIVES) {
            b.setPositiveButton("Refill - 120 coins", (d, w) -> {
                if (spend(120)) {
                    sp.edit().putInt("lives", MAX_LIVES).putLong("lifeT", 0).apply();
                    updUi();
                    toast("Hearts refilled!");
                }
            });
            if (loggedIn()) b.setNeutralButton("Collect from friends", (d, w) -> collectGifts());
        }
        b.show();
    }

    @Override
    public void onMenu() {
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("Paused")
                .setItems(new String[]{"Resume", "Settings", "Daily missions", "How to play",
                        "Quit level (lose 1 heart)"}, (d, w) -> {
                    if (w == 1) settingsDialog();
                    else if (w == 2) missionsDialog();
                    else if (w == 3) help();
                    else if (w == 4) { loseLife(); toMap(); }
                })
                .show();
    }

    @Override
    public void onMoves5Used() {
        sp.edit().putInt("moves5", Math.max(0, sp.getInt("moves5", 1) - 1))
                .putInt("stat_boosters", sp.getInt("stat_boosters", 0) + 1).apply();
        scheduleInvSync();
    }

    @Override
    public void onBuyBooster(final int type) {
        final String[] names = {"Hammer", "Shuffle", "+5 Moves", "Cross Blaster", "Color Changer"};
        final int[] cost = {60, 50, 70, 90, 90};
        final String[] key = {"hammers", "shuffles", "moves5", "crossBlast", "colorChg"};
        final int[] def = {3, 2, 1, 1, 1};
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("Get " + names[type])
                .setMessage("Buy 1 " + names[type] + " for " + cost[type] + " coins?\nYou have " + coins() + " coins.")
                .setPositiveButton("Buy", (d, w) -> {
                    if (!spend(cost[type])) return;
                    addItem(key[type], def[type], 1);
                    gv.hammers = sp.getInt("hammers", 3);
                    gv.shuffles = sp.getInt("shuffles", 2);
                    gv.moves5 = sp.getInt("moves5", 1);
                    gv.crossBlast = sp.getInt("crossBlast", 1);
                    gv.colorChg = sp.getInt("colorChg", 1);
                    gv.invalidate();
                    toast("Bought " + names[type]);
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    void help() {
        new AlertDialog.Builder(this, R.style.GemDialog)
                .setTitle("How to play")
                .setMessage("Swipe a candy to swap it with a neighbour and match 3 or more of the same shape.\n\n"
                        + "Match 4 in a line: striped candy (clears a whole row or column).\n"
                        + "Match L or T shape: wrapped candy (explodes 3x3).\n"
                        + "Match 5 in a line: color bomb (swap it with a candy to clear all of that color).\n"
                        + "Match 4 or more of one color in a 2x2 square: fish candy (it swims off and clears 3 targets - choco blocks and jelly first).\n"
                        + "Match 6 in a line: LIGHTNING gem (clears 3 rows and 3 columns).\n"
                        + "Match 7 in a line: SUPER GEM (massive 5x5 explosion).\n\n"
                        + "Swap two special candies together for a huge combo: two lightnings make a THUNDERSTORM, "
                        + "two super gems make a SUPERNOVA!\n\n"
                        + "Locked candies can't be swapped - match next to them to break the cage. "
                        + "Treasure chests hide bonus coins: clear them by matching next to them.\n\n"
                        + "Goals: reach the score, clear all jelly, bring cherries to the bottom, "
                        + "or collect the ordered candies - before moves run out. Leftover moves become bonus candies.\n\n"
                        + "Failing or quitting a level costs 1 life. You have 5 lives; 1 comes back every 25 minutes.\n"
                        + "Hammer smashes one candy, Shuffle mixes the board, Cross Blaster fires a row + column blast, "
                        + "Color Changer recolors any candy. 3 stars earn a free hammer. +5 moves can save a failed level. "
                        + "Claim a Daily gift every day to build a streak, complete Daily missions for extra coins and beat the Weekly challenge. "
                        + "If you wait too long, the game shows a hint.\n\n"
                        + "5999 levels in 240 worlds with new twists: choco blocks, block rain, boss levels, mixed goals. Smash blocks by matching next to them.\n\n"
                        + "COINS: earn coins from every win, spend them in the Shop, spin the Lucky wheel every day and unlock Awards for bonus coins. Start boosters (striped candies / color bomb) are in the level screen.\n\n"
                        + "FRIENDS: login with Google, add friends by code. Their pictures show on the map at their level, "
                        + "you see their scores after each level and you can send each other lives.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (gv.getVisibility() == View.VISIBLE) {
            if (gv.over) { toMap(); return; }
            new AlertDialog.Builder(this, R.style.GemDialog)
                    .setMessage("Quit this level? You will lose 1 life.")
                    .setPositiveButton("Quit", (d, w) -> { loseLife(); toMap(); })
                    .setNegativeButton("Keep playing", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Exit Gem Crush?")
                    .setMessage("Do you really want to close the game?")
                    .setPositiveButton("Yes, exit", (d, w) -> finish())
                    .setNegativeButton("No, stay", null)
                    .show();
        }
    }
}
