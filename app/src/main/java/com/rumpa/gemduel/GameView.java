package com.rumpa.gemduel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Candy-Crush style board, 500 levels with combined goals (score, jelly, cherries, orders,
 * choco blocks), twists (boss levels, block rain, free striped candies, breathers),
 * 3D candies, juicy animations.
 */
public class GameView extends View {
    public interface Listener {
        void onResult(boolean won, int stars, int score, String msg);
        void onHammerUsed();
        void onShuffleUsed();
        void onStat(String key);
        void onStatCount(String key, int n);
        void onMoves5Used();
        void onCrossUsed();
        void onColorUsed();
        void onChest();
        void onBuyBooster(int type);
        void onMenu();
    }

    public static class Level {
        public int n, moves, colors, target, s2, s3, ing, blocks, blockHp = 1, rain, giftStripes;
        public boolean jelly, boss;
        public int jellyPattern, jellyLayers = 1, ordCount;
        public int[] ordT = new int[2], ordN = new int[2];
        public int locks, chests;
        public String twist = "";
    }

    static final int N = 8, BOMB = 6, ING = 7, BLOCK = 8, CHEST = 9, MAX_LEVEL = 5999;
    static final int[] DR = {-1, 1, 0, 0}, DC = {0, 0, -1, 1};
    static final String[] CNAME = {"red", "orange", "yellow", "green", "blue", "purple"};

    public static final String[] WORLD = {"Candy Meadow", "Crystal Valley", "Frozen Mountain", "Jelly Jungle",
            "Magic Desert", "Rainbow Forest", "Choco Caves", "Lollipop Lagoon", "Gummy Gardens",
            "Crystal Canyon", "Marshmallow Moors", "Toffee Town", "Sugar Volcano", "Starlight Station",
            "Golden Castle", "Diamond Galaxy", "Cotton Cloud City", "Fantasy Island", "Neon Nights", "Candy Kingdom"};
    static final int[] HUE = {335, 200, 130, 20, 265, 175, 25, 190, 95, 285, 345, 40, 5, 230, 160, 315, 210, 55, 300, 350};

    public static int world(int n) { return (n - 1) / 25; }

    static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X", " XI", " XII"};

    /** World names repeat every 20 worlds with a Roman numeral (Candy Meadow II, ...). */
    public static String worldName(int w) {
        int cycle = w / 20;
        return WORLD[w % 20] + (cycle < ROMAN.length ? ROMAN[cycle] : " " + (cycle + 1));
    }

    public static int[] worldColors(int w) {
        float h = (HUE[w % 20] + (w / 20) * 17) % 360;
        float v1 = (w % 20) == 6 ? 0.50f : 0.66f;
        return new int[]{Color.HSVToColor(new float[]{h, 0.70f, v1}), Color.HSVToColor(new float[]{h, 0.45f, 1f})};
    }

    /** goal bits: 1 score, 2 jelly, 4 cherries, 8 orders, 16 blocks */
    public static Level forLevel(int n) {
        Level L = new Level();
        L.n = n;
        Random R = new Random(n * 7919L + 13);
        L.boss = n % 25 == 0;
        L.colors = n <= 3 ? 4 : n <= 14 ? 5 : 6;
        boolean breather = n > 20 && n % 10 == 5 && !L.boss;
        if (breather) L.colors = Math.max(4, L.colors - 1);

        int mask;
        if (n <= 5) mask = n <= 2 ? 1 : n == 3 ? 2 : n == 4 ? 8 : 4;
        else if (n <= 9) mask = new int[]{1, 2, 4, 8}[(n - 6) % 4];
        else if (n < 40 && !L.boss) mask = new int[]{16, 1, 2, 4, 8}[(n - 10) % 5];
        else {
            int[] singles = {1, 2, 4, 8, 16};
            int[] doubles = {2 | 4, 8 | 16, 2 | 16, 2 | 8, 4 | 16, 4 | 8, 1 | 16};
            int[] triples = {2 | 4 | 16, 2 | 8 | 16, 4 | 8 | 16, 2 | 4 | 8};
            if (L.boss) mask = n >= 100 ? triples[R.nextInt(triples.length)] : doubles[R.nextInt(doubles.length)];
            else {
                int roll = R.nextInt(100);
                int trp = n >= 1000 ? 45 : n >= 300 ? 25 : 0, dbl = n >= 150 ? 55 : 35;
                if (roll < trp) mask = triples[R.nextInt(triples.length)];
                else if (roll < trp + dbl) mask = doubles[R.nextInt(doubles.length)];
                else mask = singles[R.nextInt(singles.length)];
            }
        }

        int goals = Integer.bitCount(mask);
        int moves = Math.max(n >= 3000 ? 15 : n >= 1000 ? 16 : 18, 28 - n / 40) + (goals - 1) * 3 + (L.boss ? 4 : 0) + (breather ? 3 : 0);
        L.moves = moves;
        if ((mask & 1) != 0) L.target = Math.min(moves * 380, 2500 + 60 * n);
        if ((mask & 2) != 0) { L.jelly = true; L.jellyPattern = R.nextInt(5); L.jellyLayers = n >= 1500 ? 3 : n >= 60 ? 2 : 1; }
        if ((mask & 4) != 0) L.ing = Math.min(6, 2 + n / 70);
        if ((mask & 8) != 0) {
            L.ordCount = 2;
            int a = R.nextInt(L.colors), b = (a + 1 + R.nextInt(L.colors - 1)) % L.colors;
            L.ordT[0] = a; L.ordT[1] = b;
            int cnt = Math.min(28, 12 + n / 14);
            if (goals > 1) cnt = cnt * 2 / 3;
            L.ordN[0] = L.ordN[1] = cnt;
        }
        if ((mask & 16) != 0) {
            L.blocks = Math.min(n >= 1000 ? 18 : 14, 4 + n / 35);
            L.blockHp = n >= 250 ? 3 : n >= 100 ? 2 : 1;
            if (n >= 60 && R.nextInt(n >= 1000 ? 2 : 3) == 0) L.rain = n >= 2000 ? 6 : 8;
        }
        if (n >= 120 && R.nextInt(3) == 0) L.locks = Math.min(8, 3 + n / 800);
        if (n >= 40 && R.nextInt(4) == 0) L.chests = Math.min(4, 2 + n / 600);
        if (L.locks > 0) L.moves += 2;
        if (L.chests > 0) L.moves += 1;
        if (n > 7 && n % 7 == 0) L.giftStripes = L.boss ? 3 : 2;
        if (L.boss && L.giftStripes == 0) L.giftStripes = 3;

        List<String> tw = new ArrayList<>();
        if (L.boss) tw.add("Boss level");
        if (breather) tw.add("Breather: one color less");
        if (L.giftStripes > 0) tw.add("Free striped candies");
        if (L.rain > 0) tw.add("Block rain");
        if (L.locks > 0) tw.add("Locked candies");
        if (L.chests > 0) tw.add("Treasure chests");
        if (L.jelly && L.jellyLayers == 2) tw.add("Double jelly");
        if (L.jelly && L.jellyLayers >= 3) tw.add("Triple jelly");
        if (L.blocks > 0 && L.blockHp >= 2) tw.add("Tough blocks");
        L.twist = TextUtils.join("  -  ", tw);

        int base = Math.min(6000 + 300 * n, moves * 450);
        if (L.target > 0) base = Math.max(base, (int) (L.target * 1.35));
        L.s2 = base;
        L.s3 = (int) (base * 1.7);
        return L;
    }

    public static String goalText(Level L) {
        List<String> parts = new ArrayList<>();
        if (L.target > 0) parts.add("Score " + L.target + " points");
        if (L.jelly) parts.add("Clear all the jelly");
        if (L.ing > 0) parts.add("Bring down " + L.ing + " cherries");
        if (L.ordCount > 0) parts.add("Collect " + L.ordN[0] + " " + CNAME[L.ordT[0]] + " and "
                + L.ordN[1] + " " + CNAME[L.ordT[1]] + " candies");
        if (L.blocks > 0) parts.add("Smash " + L.blocks + " choco blocks");
        return TextUtils.join("\n+ ", parts);
    }

    public static String tipFor(int n) {
        switch (n) {
            case 1: return "Swipe a candy to swap it and match 3 or more!";
            case 2: return "Match 4 in a row to make a striped candy!";
            case 3: return "Clear all the jelly under the candies!";
            case 4: return "Collect the candies shown at the top!";
            case 5: return "Bring the cherries down to the bottom!";
            case 7: return "Match an L or T shape for a wrapped candy!";
            case 10: return "Choco blocks! Match next to them to smash them.";
            case 12: return "Match 5 in a row to create a color bomb!";
            case 15: return "Swap two special candies for a mega combo!";
            case 20: return "Make a 2x2 square for a FISH candy!";
            case 25: return "Boss level! Clear every goal to win.";
            case 30: return "Locked candies: match next to them to break the cage!";
            case 40: return "Treasure chests hold bonus coins - clear them!";
            case 50: return "Match 6 for a LIGHTNING gem: clears 3 rows and 3 columns!";
            case 60: return "Match 7 for a SUPER GEM: a massive explosion!";
            default: return null;
        }
    }

    static class Particle {
        float x, y, vx, vy, life, max, size, grav = 1400f;
        int color;
    }

    static class Popup {
        float x, y, size;
        String s;
        long t0;
        int color;
    }

    public Listener lst;
    public Sfx sfx;
    public int hammers = 3, shuffles = 2, moves5 = 1, crossBlast = 1, colorChg = 1, startStripes, startBomb;
    public String tip;
    public boolean over, lastWon;
    public int lastStars, score;

    Level L = forLevel(1);
    int movesLeft, ingGot, ingSpawned, jellyLeft, blocksGot, cascade = 1, animType, starsShown;
    final int[] ordGot = new int[2];
    final int[][] g = new int[N][N], sp = new int[N][N], jelly = new int[N][N], lock = new int[N][N];
    final float[][] offX = new float[N][N], offY = new float[N][N];
    final long[][] born = new long[N][N];
    boolean[][] cleared, forced;
    final List<int[]> beams = new ArrayList<>();
    final List<Particle> parts = new ArrayList<>();
    final List<Popup> pops = new ArrayList<>();
    final int[] pref = {-1, -1, -1, -1}, hint = {-1, -1, -1, -1};
    final Random rnd = new Random(), fx = new Random();
    boolean busy, animating, hammerMode, crossMode, chgMode, bonusMode, bonusDone, hintReady, showEnd;
    long animStart, bannerUntil, lastFrame, lastTouch, tipUntil, endT;
    float animDur = 1, shake;
    String banner;
    int selR = -1, selC = -1, bgLevel = -1;
    float dx0, dy0, cell, ox, oy, bgH;
    final int[] chT = new int[6], chS = new int[6];
    final String[] chX = new String[6];
    int chipN;
    float badgeX, badgeY, hudY0, shownScore, frameDt;
    int lastScoreSeen, lastMovesSeen;
    long scoreBumpT, movesBumpT, frameNow;
    final long[] starT = new long[3], chBump = new long[6];
    final String[] chLast = new String[6];

    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint bg = new Paint();
    final Path starP = new Path();
    final RectF rf = new RectF(), rf2 = new RectF(), hamRect = new RectF(), shufRect = new RectF(),
            gearRect = new RectF(), m5Rect = new RectF(), crossRect = new RectF(), chgRect = new RectF();
    final Handler h = new Handler(Looper.getMainLooper());

    public GameView(Context c) { super(c); }

    // ------------------------------------------------------------ setup
    public void newGame(int level) {
        h.removeCallbacksAndMessages(null);
        L = forLevel(level);
        movesLeft = L.moves;
        lastMovesSeen = L.moves;
        score = 0; ingGot = 0; blocksGot = 0; ordGot[0] = ordGot[1] = 0; starsShown = 0;
        busy = true; over = false; lastWon = false; animating = false; bonusDone = false;
        bonusMode = false; showEnd = false; hintReady = false; crossMode = false; chgMode = false;
        shownScore = 0; lastScoreSeen = 0; scoreBumpT = 0; movesBumpT = 0;
        Arrays.fill(starT, 0); Arrays.fill(chBump, 0); Arrays.fill(chLast, null);
        cleared = null; forced = null; beams.clear(); flights.clear(); parts.clear(); pops.clear();
        hammerMode = false; selR = -1; banner = null; shake = 0;
        Arrays.fill(pref, -1);
        cascade = 1;
        long now = SystemClock.uptimeMillis();
        lastTouch = now;
        tipUntil = tip != null ? now + 6000 : 0;
        for (int r = 0; r < N; r++) {
            Arrays.fill(offX[r], 0); Arrays.fill(offY[r], 0); Arrays.fill(jelly[r], 0); Arrays.fill(born[r], 0);
            Arrays.fill(lock[r], 0);
        }
        setupJelly();
        fillBoard();
        if (L.blocks > 0) placeBlocks();
        placeChests();
        placeLocks();
        startStripes += L.giftStripes;
        placeStartBoosters();
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) offY[r][c] = -(N + 2 + r * 0.4f + (c % 3) * 0.6f);
        anim(800, 1, () -> { busy = false; lastTouch = SystemClock.uptimeMillis(); invalidate(); });
        final String tw = L.boss ? "BOSS LEVEL!" : L.rain > 0 ? "Block rain!" : L.giftStripes > 0 ? "Free striped candies!" : null;
        if (tw != null) h.postDelayed(() -> banner(tw), 900);
    }

    void setupJelly() {
        jellyLeft = 0;
        if (!L.jelly) return;
        int lay = L.jellyLayers;
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                boolean on;
                switch (L.jellyPattern) {
                    case 0: on = r >= 2 && r <= 5; break;
                    case 1: on = (r + c) % 2 == 0 && r >= 1 && r <= 6; break;
                    case 2: on = r == 0 || r == N - 1 || c == 0 || c == N - 1 || r == 1 || r == N - 2; break;
                    case 3: on = c % 3 != 2 && r >= 1; break;
                    default: on = Math.abs(r - 3.5f) + Math.abs(c - 3.5f) <= 5; break;
                }
                if (on) jelly[r][c] = lay;
                jellyLeft += jelly[r][c];
            }
        }
    }

    void placeBlocks() {
        List<int[]> cells = new ArrayList<>();
        for (int r = 1; r < N; r++)
            for (int c = 0; c < N; c++)
                if (g[r][c] < BOMB) cells.add(new int[]{r, c});
        Collections.shuffle(cells, rnd);
        for (int i = 0; i < L.blocks && i < cells.size(); i++) {
            int[] cl = cells.get(i);
            g[cl[0]][cl[1]] = BLOCK;
            sp[cl[0]][cl[1]] = 1 + rnd.nextInt(L.blockHp);
        }
        if (!hasMove()) reshuffle();
    }

    void placeChests() {
        if (L.chests <= 0) return;
        List<int[]> cells = new ArrayList<>();
        for (int r = 1; r < N; r++)
            for (int c = 0; c < N; c++)
                if (g[r][c] < BOMB && lock[r][c] == 0) cells.add(new int[]{r, c});
        Collections.shuffle(cells, rnd);
        for (int i = 0; i < L.chests && i < cells.size(); i++) {
            int[] cl = cells.get(i);
            g[cl[0]][cl[1]] = CHEST;
            sp[cl[0]][cl[1]] = 0;
        }
    }

    void placeLocks() {
        if (L.locks <= 0) return;
        List<int[]> cells = new ArrayList<>();
        for (int r = 1; r < N; r++)
            for (int c = 0; c < N; c++)
                if (g[r][c] < BOMB && lock[r][c] == 0) cells.add(new int[]{r, c});
        Collections.shuffle(cells, rnd);
        for (int i = 0; i < L.locks && i < cells.size(); i++) lock[cells.get(i)[0]][cells.get(i)[1]] = 1;
        if (!hasMove()) reshuffle();
    }

    void placeStartBoosters() {
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++)
                if (g[r][c] < BOMB && sp[r][c] == 0 && lock[r][c] == 0) cells.add(new int[]{r, c});
        Collections.shuffle(cells, rnd);
        int k = 0;
        for (int i = 0; i < startStripes && k < cells.size(); i++, k++) {
            int[] cl = cells.get(k);
            sp[cl[0]][cl[1]] = 1 + rnd.nextInt(2);
        }
        for (int i = 0; i < startBomb && k < cells.size(); i++, k++) {
            int[] cl = cells.get(k);
            g[cl[0]][cl[1]] = BOMB;
            sp[cl[0]][cl[1]] = 0;
        }
        startStripes = 0;
        startBomb = 0;
    }

    void fillBoard() {
        do {
            ingSpawned = 0;
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    int t;
                    do {
                        t = rnd.nextInt(L.colors);
                    } while ((c >= 2 && g[r][c - 1] == t && g[r][c - 2] == t)
                            || (r >= 2 && g[r - 1][c] == t && g[r - 2][c] == t));
                    g[r][c] = t;
                    sp[r][c] = 0;
                }
            }
            if (L.ing > 0) {
                List<Integer> cols = new ArrayList<>();
                for (int c = 0; c < N; c++) cols.add(c);
                Collections.shuffle(cols, rnd);
                int k = Math.min(L.ing, 2);
                for (int i = 0; i < k; i++) g[0][cols.get(i)] = ING;
                ingSpawned = k;
            }
        } while (hasMatch() || !hasMove());
    }

    // ------------------------------------------------------------ matching helpers
    List<int[]> runs() {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < N; r++) {
            int c = 0;
            while (c < N) {
                int t = g[r][c];
                if (t >= 0 && t < BOMB && lock[r][c] == 0) {
                    int len = 1;
                    while (c + len < N && g[r][c + len] == t && lock[r][c + len] == 0) len++;
                    if (len >= 3) out.add(new int[]{r, c, 0, 1, len});
                    c += len;
                } else c++;
            }
        }
        for (int c = 0; c < N; c++) {
            int r = 0;
            while (r < N) {
                int t = g[r][c];
                if (t >= 0 && t < BOMB && lock[r][c] == 0) {
                    int len = 1;
                    while (r + len < N && g[r + len][c] == t && lock[r + len][c] == 0) len++;
                    if (len >= 3) out.add(new int[]{r, c, 1, 0, len});
                    r += len;
                } else r++;
            }
        }
        return out;
    }

    /** 2x2 blocks of one colour (they make a fish candy). Returns top-left cells, never overlapping. */
    List<int[]> squares() {
        List<int[]> out = new ArrayList<>();
        boolean[][] used = new boolean[N][N];
        for (int r = 0; r < N - 1; r++)
            for (int c = 0; c < N - 1; c++) {
                int t = g[r][c];
                if (t < 0 || t >= BOMB || lock[r][c] > 0) continue;
                if (lock[r][c + 1] == 0 && lock[r + 1][c] == 0 && lock[r + 1][c + 1] == 0
                        && g[r][c + 1] == t && g[r + 1][c] == t && g[r + 1][c + 1] == t
                        && !used[r][c] && !used[r][c + 1] && !used[r + 1][c] && !used[r + 1][c + 1]) {
                    out.add(new int[]{r, c});
                    used[r][c] = used[r][c + 1] = used[r + 1][c] = used[r + 1][c + 1] = true;
                }
            }
        return out;
    }

    boolean hasMatch() { return !runs().isEmpty() || !squares().isEmpty(); }

    void sw(int a, int b, int c, int d) {
        int t = g[a][b]; g[a][b] = g[c][d]; g[c][d] = t;
        t = sp[a][b]; sp[a][b] = sp[c][d]; sp[c][d] = t;
    }

    boolean validSwap(int r1, int c1, int r2, int c2) {
        int a = g[r1][c1], b = g[r2][c2];
        if (a == BLOCK || b == BLOCK || a == CHEST || b == CHEST) return false;
        if (lock[r1][c1] > 0 || lock[r2][c2] > 0) return false;
        if (a == BOMB || b == BOMB) return a != ING && b != ING;
        if (sp[r1][c1] != 0 && sp[r2][c2] != 0 && a < BOMB && b < BOMB) return true;
        sw(r1, c1, r2, c2);
        boolean ok = hasMatch();
        sw(r1, c1, r2, c2);
        return ok;
    }

    boolean hasMove() {
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) {
                if (c + 1 < N && validSwap(r, c, r, c + 1)) return true;
                if (r + 1 < N && validSwap(r, c, r + 1, c)) return true;
            }
        return false;
    }

    boolean isCombo(int r1, int c1, int r2, int c2) {
        int a = g[r1][c1], b = g[r2][c2];
        if ((a == BOMB && b != ING && b != BLOCK && b != CHEST) || (b == BOMB && a != ING && a != BLOCK && a != CHEST)) return true;
        return a < BOMB && b < BOMB && sp[r1][c1] != 0 && sp[r2][c2] != 0;
    }

    boolean canTouch() { return !busy && !over; }

    void computeHint() {
        List<int[]> mv = new ArrayList<>();
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) {
                if (c + 1 < N && validSwap(r, c, r, c + 1)) mv.add(new int[]{r, c, r, c + 1});
                if (r + 1 < N && validSwap(r, c, r + 1, c)) mv.add(new int[]{r, c, r + 1, c});
            }
        if (mv.isEmpty()) return;
        System.arraycopy(mv.get(fx.nextInt(mv.size())), 0, hint, 0, 4);
        hintReady = true;
    }

    // ------------------------------------------------------------ animation plumbing
    void swapOff(int r1, int c1, int r2, int c2) {
        offY[r1][c1] = r2 - r1; offX[r1][c1] = c2 - c1;
        offY[r2][c2] = r1 - r2; offX[r2][c2] = c1 - c2;
    }

    /** type 0 = smooth swap, 1 = bouncy drop, 2 = clear (no offsets) */
    void anim(float ms, int type, final Runnable next) {
        animStart = SystemClock.uptimeMillis();
        animDur = ms;
        animType = type;
        animating = true;
        invalidate();
        h.postDelayed(() -> {
            animating = false;
            for (int r = 0; r < N; r++) { Arrays.fill(offX[r], 0); Arrays.fill(offY[r], 0); }
            invalidate();
            next.run();
        }, (long) ms);
    }

    void banner(String s) {
        banner = s;
        bannerUntil = SystemClock.uptimeMillis() + 900;
        invalidate();
    }

    float cx(int c) { return ox + c * cell + cell / 2; }
    float cy(int r) { return oy + r * cell + cell / 2; }

    void burst(int r, int c, int color, int n, float power) {
        for (int i = 0; i < n && parts.size() < 350; i++) {
            Particle q = new Particle();
            q.x = cx(c); q.y = cy(r);
            double a = fx.nextDouble() * Math.PI * 2;
            float v = power * (0.4f + fx.nextFloat());
            q.vx = (float) Math.cos(a) * v;
            q.vy = (float) Math.sin(a) * v - power * 0.35f;
            q.max = q.life = 0.45f + fx.nextFloat() * 0.5f;
            q.size = cell * (0.05f + fx.nextFloat() * 0.07f);
            q.color = color;
            parts.add(q);
        }
    }

    void confetti(int n) {
        float W = getWidth();
        for (int i = 0; i < n && parts.size() < 400; i++) {
            Particle q = new Particle();
            q.x = fx.nextFloat() * W; q.y = -20;
            q.vx = (fx.nextFloat() - 0.5f) * 500; q.vy = 100 + fx.nextFloat() * 500;
            q.max = q.life = 2.2f + fx.nextFloat();
            q.size = W * (0.012f + fx.nextFloat() * 0.014f);
            q.color = Gems.COL[fx.nextInt(6)];
            q.grav = 500f;
            parts.add(q);
        }
    }

    // ------------------------------------------------------------ player actions
    void trySwap(final int r1, final int c1, final int r2, final int c2) {
        if (busy || over || movesLeft <= 0) return;
        if (Math.abs(r1 - r2) + Math.abs(c1 - c2) != 1) return;
        if (g[r1][c1] == BLOCK || g[r2][c2] == BLOCK) {
            shake = Math.max(shake, 4);
            if (sfx != null) sfx.bad();
            return;
        }
        busy = true;
        hammerMode = false;
        hintReady = false;
        sw(r1, c1, r2, c2);
        swapOff(r1, c1, r2, c2);
        if (sfx != null) sfx.swap();
        anim(190, 0, () -> {
            boolean combo = isCombo(r1, c1, r2, c2);
            if (!combo && !hasMatch()) {
                sw(r1, c1, r2, c2);
                swapOff(r1, c1, r2, c2);
                if (sfx != null) sfx.bad();
                shake = Math.max(shake, 5);
                anim(190, 0, () -> { busy = false; lastTouch = SystemClock.uptimeMillis(); invalidate(); });
                return;
            }
            movesLeft--;
            cascade = 1;
            pref[0] = r1; pref[1] = c1; pref[2] = r2; pref[3] = c2;
            if (combo) forced = buildCombo(r1, c1, r2, c2);
            step();
        });
    }

    void useCross(int r, int c) {
        if (crossBlast <= 0 || busy || over) return;
        crossBlast--;
        crossMode = false;
        hintReady = false;
        if (lst != null) lst.onCrossUsed();
        busy = true;
        cascade = 1;
        shake = Math.max(shake, 12);
        if (sfx != null) sfx.boom();
        forced = new boolean[N][N];
        for (int k = 0; k < N; k++) { forced[r][k] = true; forced[k][c] = true; }
        beams.add(new int[]{1, r, 0});
        beams.add(new int[]{2, c, 0});
        banner("CROSS BLAST!");
        step();
    }

    void useColorChg(int r, int c) {
        if (colorChg <= 0 || busy || over) return;
        if (g[r][c] >= BOMB) {
            shake = Math.max(shake, 4);
            if (sfx != null) sfx.bad();
            return;
        }
        colorChg--;
        chgMode = false;
        hintReady = false;
        if (lst != null) lst.onColorUsed();
        g[r][c] = (g[r][c] + 1) % L.colors;
        born[r][c] = SystemClock.uptimeMillis();
        burst(r, c, Gems.COL[g[r][c]], 10, cell * 4f);
        banner("Color change!");
        if (sfx != null) sfx.extra();
        if (hasMatch()) {
            busy = true;
            cascade = 1;
            step();
        } else invalidate();
    }

    void useHammer(int r, int c) {
        if (g[r][c] == ING || hammers <= 0) return;
        hammers--;
        hammerMode = false;
        hintReady = false;
        if (lst != null) lst.onHammerUsed();
        busy = true;
        cascade = 1;
        shake = Math.max(shake, 7);
        if (sfx != null) sfx.boom();
        forced = new boolean[N][N];
        forced[r][c] = true;
        step();
    }

    void useShuffle() {
        if (shuffles <= 0 || busy || over) return;
        shuffles--;
        if (lst != null) lst.onShuffleUsed();
        busy = true;
        hammerMode = false;
        hintReady = false;
        reshuffle();
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) {
                offX[r][c] = (fx.nextFloat() - 0.5f) * 5f;
                offY[r][c] = (fx.nextFloat() - 0.5f) * 5f;
            }
        banner("Shuffle!");
        if (sfx != null) sfx.whoosh();
        anim(560, 1, () -> { busy = false; lastTouch = SystemClock.uptimeMillis(); invalidate(); });
    }

    public void extraMoves(int n) {
        over = false; busy = false; bonusDone = false; showEnd = false; bonusMode = false;
        movesLeft += n;
        lastTouch = SystemClock.uptimeMillis();
        banner("+" + n + " moves!");
        invalidate();
    }

    // ------------------------------------------------------------ special combos
    boolean[][] buildCombo(int r1, int c1, int r2, int c2) {
        boolean[][] m = new boolean[N][N];
        int a = g[r1][c1], b = g[r2][c2];
        if (a == BOMB || b == BOMB) {
            int br = a == BOMB ? r1 : r2, bc = a == BOMB ? c1 : c2;
            int or = a == BOMB ? r2 : r1, oc = a == BOMB ? c2 : c1;
            m[br][bc] = true;
            int ot = g[or][oc], os = sp[or][oc];
            if (ot == BOMB) {
                for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (g[r][c] != ING) m[r][c] = true;
                banner("Rainbow blast!");
                shake = 22;
            } else {
                m[or][oc] = true;
                for (int r = 0; r < N; r++) {
                    for (int c = 0; c < N; c++) {
                        if (g[r][c] != ot) continue;
                        m[r][c] = true;
                        if (os == 1) for (int k = 0; k < N; k++) m[r][k] = true;
                        else if (os == 2) for (int k = 0; k < N; k++) m[k][c] = true;
                        else if (os == 3) mark3(m, r, c);
                        else if (os == 4) fishAttack(m, r, c, 6);
                        else if (os == 5) lines3(m, r, c);
                        else if (os == 6) markArea(m, r, c, 2);
                    }
                }
                banner("Color bomb!");
                shake = 14;
            }
            if (sfx != null) sfx.boom();
        } else {
            int sA = sp[r1][c1], sB = sp[r2][c2];
            sp[r1][c1] = 0; sp[r2][c2] = 0;
            int r = r2, c = c2;
            m[r1][c1] = true; m[r2][c2] = true;
            if (sA == 4 || sB == 4) {
                if (sA != 4) sp[r1][c1] = sA;
                if (sB != 4) sp[r2][c2] = sB;
                int fr = sA == 4 ? r1 : r2, fc = sA == 4 ? c1 : c2;
                fishAttack(m, fr, fc, (sA == 4 && sB == 4) ? 6 : 3);
                banner("Fish frenzy!");
                shake = 10;
                if (sfx != null) sfx.whoosh();
                return m;
            }
            boolean lA = sA == 5, lB = sB == 5, uA = sA == 6, uB = sB == 6;
            if (uA && uB) {
                markArea(m, r2, c2, 3);
                beams.add(new int[]{3, r2, c2});
                banner("SUPERNOVA!");
                shake = 26;
                if (sfx != null) sfx.boom();
                return m;
            }
            if (uA || uB) {
                int ur = uA ? r1 : r2, uc = uA ? c1 : c2, lr = uA ? r2 : r1, lc = uA ? c2 : c1;
                int ps = uA ? sB : sA;
                markArea(m, ur, uc, 2);
                if (ps == 5) lines3(m, lr, lc);
                else if (ps == 1) { for (int k = 0; k < N; k++) m[lr][k] = true; beams.add(new int[]{1, lr, 0}); }
                else if (ps == 2) { for (int k = 0; k < N; k++) m[k][lc] = true; beams.add(new int[]{2, lc, 0}); }
                else if (ps == 3) mark3(m, lr, lc);
                else if (ps == 4) fishAttack(m, lr, lc, 6);
                beams.add(new int[]{3, ur, uc});
                banner("SUPER GEM!");
                shake = 22;
                if (sfx != null) sfx.boom();
                return m;
            }
            if (lA || lB) {
                lines3(m, r1, c1);
                lines3(m, r2, c2);
                banner("THUNDERSTORM!");
                shake = 18;
                if (sfx != null) sfx.boom();
                return m;
            }
            boolean wA = sA == 3, wB = sB == 3;
            if (wA && wB) {
                for (int rr = r - 2; rr <= r + 2; rr++)
                    for (int cc = c - 2; cc <= c + 2; cc++)
                        if (rr >= 0 && rr < N && cc >= 0 && cc < N) m[rr][cc] = true;
                beams.add(new int[]{3, r, c});
                banner("Mega blast!");
                shake = 20;
                if (sfx != null) sfx.boom();
            } else if (wA || wB) {
                for (int d = -1; d <= 1; d++) {
                    if (r + d >= 0 && r + d < N) for (int k = 0; k < N; k++) m[r + d][k] = true;
                    if (c + d >= 0 && c + d < N) for (int k = 0; k < N; k++) m[k][c + d] = true;
                }
                beams.add(new int[]{1, r, 0}); beams.add(new int[]{2, c, 0});
                banner("Big cross!");
                shake = 16;
                if (sfx != null) sfx.boom();
            } else {
                for (int k = 0; k < N; k++) { m[r][k] = true; m[k][c] = true; }
                beams.add(new int[]{1, r, 0}); beams.add(new int[]{2, c, 0});
                banner("Double stripe!");
                shake = 12;
                if (sfx != null) sfx.whoosh();
            }
        }
        return m;
    }

    void mark3(boolean[][] m, int r, int c) {
        for (int rr = r - 1; rr <= r + 1; rr++)
            for (int cc = c - 1; cc <= c + 1; cc++)
                if (rr >= 0 && rr < N && cc >= 0 && cc < N) m[rr][cc] = true;
    }

    void markArea(boolean[][] m, int r, int c, int rad) {
        for (int rr = r - rad; rr <= r + rad; rr++)
            for (int cc = c - rad; cc <= c + rad; cc++)
                if (rr >= 0 && rr < N && cc >= 0 && cc < N) m[rr][cc] = true;
    }

    void lines3(boolean[][] m, int r, int c) {
        for (int k = -1; k <= 1; k++) {
            if (r + k >= 0 && r + k < N) { for (int q = 0; q < N; q++) m[r + k][q] = true; beams.add(new int[]{1, r + k, 0}); }
            if (c + k >= 0 && c + k < N) { for (int q = 0; q < N; q++) m[q][c + k] = true; beams.add(new int[]{2, c + k, 0}); }
        }
    }

    // ------------------------------------------------------------ resolve loop
    int[] pickPos(int[] ru, boolean[][] used) {
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < ru[4]; i++) {
                int r = ru[0] + ru[2] * i, c = ru[1] + ru[3] * i;
                if (used[r][c] || sp[r][c] != 0) continue;
                boolean isPref = (r == pref[0] && c == pref[1]) || (r == pref[2] && c == pref[3]);
                if (pass == 0 && isPref) return new int[]{r, c};
                if (pass == 1 && i == ru[4] / 2) return new int[]{r, c};
                if (pass == 2) return new int[]{r, c};
            }
        }
        return null;
    }

    List<int[]> makeSpawns(List<int[]> rs, List<int[]> sqs) {
        List<int[]> out = new ArrayList<>();
        boolean[][] used = new boolean[N][N];
        boolean[] done = new boolean[rs.size()];
        for (int k = 0; k < rs.size(); k++) {
            int[] ru = rs.get(k);
            if (ru[4] >= 5) {
                int[] ps = pickPos(ru, used);
                if (ps != null) {
                    int spv = ru[4] >= 7 ? 6 : ru[4] == 6 ? 5 : 0;
                    out.add(new int[]{ps[0], ps[1], spv > 0 ? g[ps[0]][ps[1]] : BOMB, spv});
                    used[ps[0]][ps[1]] = true;
                    done[k] = true;
                }
            }
        }
        for (int i = 0; i < rs.size(); i++) {
            int[] hr = rs.get(i);
            if (hr[2] != 0 || done[i]) continue;
            for (int j = 0; j < rs.size(); j++) {
                int[] vr = rs.get(j);
                if (vr[2] != 1 || done[j] || done[i]) continue;
                int r = hr[0], c = vr[1];
                if (c >= hr[1] && c < hr[1] + hr[4] && r >= vr[0] && r < vr[0] + vr[4]
                        && !used[r][c] && sp[r][c] == 0 && g[r][c] == g[hr[0]][hr[1]]) {
                    out.add(new int[]{r, c, g[r][c], 3});
                    used[r][c] = true; done[i] = true; done[j] = true;
                }
            }
        }
        for (int k = 0; k < rs.size(); k++) {
            int[] ru = rs.get(k);
            if (ru[4] == 4 && !done[k]) {
                int[] ps = pickPos(ru, used);
                if (ps != null) {
                    out.add(new int[]{ps[0], ps[1], g[ps[0]][ps[1]], ru[2] == 0 ? 2 : 1});
                    used[ps[0]][ps[1]] = true; done[k] = true;
                }
            }
        }
        for (int[] sq : sqs) {
            int[] cells = {sq[0], sq[1], sq[0], sq[1] + 1, sq[0] + 1, sq[1], sq[0] + 1, sq[1] + 1};
            boolean free = true;
            for (int i = 0; i < 4; i++)
                if (used[cells[2 * i]][cells[2 * i + 1]] || sp[cells[2 * i]][cells[2 * i + 1]] != 0) free = false;
            if (!free) continue;
            int pr = cells[0], pc = cells[1];
            for (int i = 0; i < 4; i++) {
                int rr = cells[2 * i], cc = cells[2 * i + 1];
                if ((rr == pref[0] && cc == pref[1]) || (rr == pref[2] && cc == pref[3])) { pr = rr; pc = cc; break; }
            }
            out.add(new int[]{pr, pc, g[pr][pc], 4});
            used[pr][pc] = true;
        }
        Arrays.fill(pref, -1);
        return out;
    }

    final List<float[]> flights = new ArrayList<>();

    /** The fish swims to targets (choco blocks, jelly, ordered candies first) and clears them. */
    void fishAttack(boolean[][] clr, int r0, int c0, int count) {
        int type = g[r0][c0];
        if (type >= BOMB) type = 0;
        for (int k = 0; k < count; k++) {
            int[] t = fishTarget(clr);
            if (t == null) break;
            clr[t[0]][t[1]] = true;
            flights.add(new float[]{r0, c0, t[0], t[1], type});
        }
    }

    int[] fishTarget(boolean[][] clr) {
        List<int[]> blk = new ArrayList<>(), jel = new ArrayList<>(), ord = new ArrayList<>(), any = new ArrayList<>();
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (clr[r][c] || g[r][c] == ING) continue;
                int[] cell = {r, c};
                if (g[r][c] == BLOCK) blk.add(cell);
                else if (jelly[r][c] > 0) jel.add(cell);
                else if (L.ordCount > 0 && g[r][c] < BOMB && (g[r][c] == L.ordT[0] || g[r][c] == L.ordT[1])) ord.add(cell);
                else any.add(cell);
            }
        }
        List<int[]> pool = !blk.isEmpty() ? blk : !jel.isEmpty() ? jel : !ord.isEmpty() ? ord : any;
        return pool.isEmpty() ? null : pool.get(fx.nextInt(pool.size()));
    }

    void expand(boolean[][] clr) {
        boolean[][] act = new boolean[N][N];
        boolean ch = true;
        while (ch) {
            ch = false;
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    if (!clr[r][c] || act[r][c] || g[r][c] >= BOMB || sp[r][c] == 0) continue;
                    act[r][c] = true; ch = true;
                    int s = sp[r][c];
                    if (s == 1) { for (int k = 0; k < N; k++) clr[r][k] = true; beams.add(new int[]{1, r, 0}); }
                    else if (s == 2) { for (int k = 0; k < N; k++) clr[k][c] = true; beams.add(new int[]{2, c, 0}); }
                    else if (s == 4) { fishAttack(clr, r, c, 3); }
                    else if (s == 5) {
                        for (int k = -1; k <= 1; k++) {
                            if (r + k >= 0 && r + k < N) { for (int q = 0; q < N; q++) clr[r + k][q] = true; beams.add(new int[]{1, r + k, 0}); }
                            if (c + k >= 0 && c + k < N) { for (int q = 0; q < N; q++) clr[q][c + k] = true; beams.add(new int[]{2, c + k, 0}); }
                        }
                    } else if (s == 6) {
                        for (int rr = r - 2; rr <= r + 2; rr++)
                            for (int cc = c - 2; cc <= c + 2; cc++)
                                if (rr >= 0 && rr < N && cc >= 0 && cc < N) clr[rr][cc] = true;
                        beams.add(new int[]{3, r, c});
                    } else { mark3(clr, r, c); beams.add(new int[]{3, r, c}); }
                }
            }
        }
    }

    void step() {
        List<int[]> rs = runs();
        List<int[]> sqs = squares();
        if (rs.isEmpty() && sqs.isEmpty() && forced == null) { settle(); return; }
        boolean[][] clr = new boolean[N][N];
        for (int[] ru : rs)
            for (int i = 0; i < ru[4]; i++) clr[ru[0] + ru[2] * i][ru[1] + ru[3] * i] = true;
        for (int[] sq : sqs) { clr[sq[0]][sq[1]] = true; clr[sq[0]][sq[1] + 1] = true; clr[sq[0] + 1][sq[1]] = true; clr[sq[0] + 1][sq[1] + 1] = true; }
        List<int[]> spawns = makeSpawns(rs, sqs);
        for (int[] s : spawns) clr[s[0]][s[1]] = false;
        if (forced != null) {
            for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (forced[r][c]) clr[r][c] = true;
            forced = null;
        }
        int beamsBefore = beams.size();
        expand(clr);
        for (int[] s : spawns) clr[s[0]][s[1]] = false;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (clr[r][c] && g[r][c] == ING) clr[r][c] = false;

        // locked candies lose their cage instead of being cleared
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (clr[r][c] && lock[r][c] > 0) {
                    lock[r][c] = 0;
                    clr[r][c] = false;
                    score += 50;
                    burst(r, c, 0xFFCFD8DC, 8, cell * 4f);
                    if (sfx != null) sfx.whoosh();
                }
            }
        }

        // choco blocks and treasure chests take a hit when touched or when a neighbouring candy clears
        long now = SystemClock.uptimeMillis();
        boolean[][] hitB = new boolean[N][N];
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (g[r][c] != BLOCK && g[r][c] != CHEST) continue;
                boolean ht = clr[r][c];
                for (int d = 0; d < 4 && !ht; d++) {
                    int nr = r + DR[d], nc = c + DC[d];
                    if (nr >= 0 && nr < N && nc >= 0 && nc < N && clr[nr][nc]
                            && g[nr][nc] != BLOCK && g[nr][nc] != CHEST) ht = true;
                }
                hitB[r][c] = ht;
            }
        }
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (!hitB[r][c]) continue;
                sp[r][c]--;
                if (sp[r][c] > 0) {
                    clr[r][c] = false;
                    born[r][c] = now;
                    burst(r, c, 0xFF8D6E63, 6, cell * 4f);
                    if (sfx != null) sfx.swap();
                } else {
                    clr[r][c] = true;
                }
            }
        }

        int cnt = 0, gain = 0, pts = 60, gemsCnt = 0;
        float sr = 0, sc = 0;
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (!clr[r][c]) continue;
                int t = g[r][c];
                cnt++; sr += r; sc += c;
                if (t == BLOCK) {
                    blocksGot++;
                    gain += 200;
                    if (lst != null) lst.onStat("blocks");
                    burst(r, c, 0xFF8D6E63, 12, cell * 5f);
                    continue;
                }
                if (t == CHEST) {
                    gain += 250;
                    if (lst != null) { lst.onChest(); lst.onStat("chests"); }
                    burst(r, c, 0xFFFFD54F, 16, cell * 6f);
                    Popup cp = new Popup();
                    cp.x = cx(c); cp.y = cy(r);
                    cp.s = "+10 coins!"; cp.t0 = now;
                    cp.color = 0xFFFFD54F; cp.size = cell * 0.5f;
                    pops.add(cp);
                    continue;
                }
                gemsCnt++;
                gain += pts;
                if (t < BOMB && L.ordCount > 0)
                    for (int i = 0; i < 2; i++) if (t == L.ordT[i]) ordGot[i]++;
                if (jelly[r][c] > 0) { jelly[r][c]--; jellyLeft--; gain += 100; }
                burst(r, c, t < BOMB ? Gems.COL[t] : 0xFFFFFFFF, (t < BOMB && sp[r][c] != 0) ? 9 : 4, cell * 5f);
            }
        }
        score += gain;
        if (gemsCnt > 0 && lst != null) lst.onStatCount("gems", gemsCnt);
        if (cnt > 0) {
            Popup pp = new Popup();
            pp.x = cx(Math.round(sc / cnt)); pp.y = cy(Math.round(sr / cnt));
            pp.s = "+" + gain; pp.t0 = now;
            pp.color = cascade > 1 ? 0xFFFFEB3B : 0xFFFFFFFF;
            pp.size = cell * (cnt > 8 ? 0.8f : 0.55f);
            pops.add(pp);
        }
        if (beams.size() > beamsBefore) {
            boolean wrapped = false;
            for (int i = beamsBefore; i < beams.size(); i++) if (beams.get(i)[0] == 3) wrapped = true;
            if (wrapped) { shake = Math.max(shake, 9); if (sfx != null) sfx.boom(); }
            else if (sfx != null) sfx.whoosh();
        }
        for (int[] s : spawns) {
            g[s[0]][s[1]] = s[2];
            sp[s[0]][s[1]] = s[3];
            born[s[0]][s[1]] = now;
            burst(s[0], s[1], 0xFFFFFFFF, 12, cell * 4f);
            if (s[2] == BOMB) banner("Color bomb!");
            else if (s[3] == 6) banner("SUPER GEM!");
            else if (s[3] == 5) banner("Lightning gem!");
            else if (s[3] == 3) banner("Wrapped candy!");
            else if (s[3] == 4) banner("Fish candy!");
            else if (s[3] != 0) banner("Striped candy!");
            if (lst != null)
                lst.onStat(s[2] == BOMB ? "bombs" : s[3] == 6 ? "super" : s[3] == 5 ? "lightning"
                        : s[3] == 3 ? "wrapped" : s[3] == 4 ? "fish" : "striped");
            if (sfx != null) sfx.extra();
        }
        if (cascade == 2) banner("Sweet!");
        else if (cascade == 3) banner("Tasty!");
        else if (cascade == 4) { banner("Delicious!"); shake = Math.max(shake, 8); }
        else if (cascade >= 5) { banner("Divine!"); shake = Math.max(shake, 12); if (lst != null) lst.onStat("divine"); }
        if (sfx != null) { sfx.pop(Math.min(cascade, 4) - 1); sfx.buzz(cascade > 1 || cnt > 5 ? 28 : 12); }

        cleared = clr;
        anim(300, 2, () -> {
            collapse();
            cleared = null;
            cascade++;
            anim(400, 1, this::afterFall);
        });
    }

    void collapse() {
        for (int c = 0; c < N; c++) {
            int bottom = N - 1;
            while (bottom >= 0) {
                if (!cleared[bottom][c] && (lock[bottom][c] > 0 || g[bottom][c] == CHEST)) { bottom--; continue; }
                int top = bottom;
                while (top >= 0 && !(!cleared[top][c] && (lock[top][c] > 0 || g[top][c] == CHEST))) top--;
                int w = bottom;
                for (int r = bottom; r > top; r--) {
                    if (!cleared[r][c]) {
                        if (w != r) { g[w][c] = g[r][c]; sp[w][c] = sp[r][c]; born[w][c] = born[r][c]; offY[w][c] = r - w; }
                        w--;
                    }
                }
                for (int r = w; r > top; r--) {
                    g[r][c] = newCandy();
                    sp[r][c] = g[r][c] == BLOCK ? 1 : 0;
                    born[r][c] = 0;
                    offY[r][c] = -(w - top);
                }
                bottom = top;
            }
        }
        beams.clear();
        flights.clear();
    }

    int newCandy() {
        if (L.ing > 0 && ingSpawned < L.ing && count(ING) < 2 && rnd.nextInt(9) == 0) {
            ingSpawned++;
            return ING;
        }
        if (L.rain > 0 && count(BLOCK) < 5 && rnd.nextInt(L.rain) == 0) return BLOCK;
        return rnd.nextInt(L.colors);
    }

    int count(int type) {
        int k = 0;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (g[r][c] == type) k++;
        return k;
    }

    void afterFall() {
        boolean[][] m = new boolean[N][N];
        boolean any = false;
        for (int c = 0; c < N; c++) {
            if (g[N - 1][c] == ING) {
                m[N - 1][c] = true; ingGot++; any = true;
                burst(N - 1, c, 0xFFD50000, 14, cell * 5f);
                Popup pp = new Popup();
                pp.x = cx(c); pp.y = cy(N - 1); pp.s = "Cherry!"; pp.t0 = SystemClock.uptimeMillis();
                pp.color = 0xFFFF8A80; pp.size = cell * 0.5f;
                pops.add(pp);
            }
        }
        if (any) {
            cleared = m;
            if (sfx != null) sfx.extra();
            anim(300, 2, () -> { collapse(); cleared = null; anim(400, 1, this::afterFall); });
            return;
        }
        step();
    }

    void settle() {
        if (!hasMove()) reshuffle();
        checkEnd();
    }

    void reshuffle() {
        List<int[]> cells = new ArrayList<>(), vals = new ArrayList<>();
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++)
                if (g[r][c] < BOMB && lock[r][c] == 0) { cells.add(new int[]{r, c}); vals.add(new int[]{g[r][c], sp[r][c]}); }
        for (int tries = 0; tries < 80; tries++) {
            Collections.shuffle(vals, rnd);
            for (int i = 0; i < cells.size(); i++) {
                g[cells.get(i)[0]][cells.get(i)[1]] = vals.get(i)[0];
                sp[cells.get(i)[0]][cells.get(i)[1]] = vals.get(i)[1];
            }
            if (!hasMatch() && hasMove()) { banner("Shuffle!"); return; }
        }
        for (int tries = 0; tries < 200; tries++) {
            for (int[] cl : cells) { g[cl[0]][cl[1]] = rnd.nextInt(L.colors); sp[cl[0]][cl[1]] = 0; }
            if (!hasMatch() && hasMove()) { banner("Shuffle!"); return; }
        }
    }

    boolean goalMet() {
        if (L.target > 0 && score < L.target) return false;
        if (L.jelly && jellyLeft > 0) return false;
        if (L.ing > 0 && ingGot < L.ing) return false;
        if (L.ordCount > 0 && (ordGot[0] < L.ordN[0] || ordGot[1] < L.ordN[1])) return false;
        return !(L.blocks > 0 && blocksGot < L.blocks);
    }

    void checkEnd() {
        if (goalMet()) {
            if (!bonusDone && movesLeft > 0) { startBonus(); return; }
            finish(true);
            return;
        }
        if (movesLeft <= 0) { finish(false); return; }
        busy = false;
        lastTouch = SystemClock.uptimeMillis();
        hintReady = false;
        invalidate();
    }

    /** Leftover moves turn into bonus score (+5000 each) and striped candies that fire off. */
    void startBonus() {
        bonusDone = true;
        bonusMode = true;
        final int total = movesLeft;
        final List<int[]> cand = new ArrayList<>();
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++)
                if (g[r][c] < BOMB && sp[r][c] == 0) cand.add(new int[]{r, c});
        Collections.shuffle(cand, rnd);
        final boolean[][] f = new boolean[N][N];
        final int[] i = {0};
        banner("Sweet!");
        if (sfx != null) sfx.extra();
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (i[0] < total) {
                    movesLeft--;
                    movesBumpT = SystemClock.uptimeMillis();
                    score += 5000;
                    Popup pp = new Popup();
                    pp.x = badgeX; pp.y = badgeY + cell * 0.5f;
                    pp.s = "+5000"; pp.t0 = SystemClock.uptimeMillis();
                    pp.color = 0xFFFFEB3B; pp.size = cell * 0.6f;
                    pops.add(pp);
                    if (i[0] < 8 && i[0] < cand.size()) {
                        int[] cl = cand.get(i[0]);
                        sp[cl[0]][cl[1]] = 1 + rnd.nextInt(2);
                        born[cl[0]][cl[1]] = SystemClock.uptimeMillis();
                        f[cl[0]][cl[1]] = true;
                        burst(cl[0], cl[1], 0xFFFFFFFF, 10, cell * 4f);
                    }
                    if (sfx != null) sfx.pop(Math.min(3, i[0] / 2));
                    i[0]++;
                    h.postDelayed(this, 170);
                } else {
                    banner("Moves bonus +" + (total * 5000));
                    anim(600, 2, () -> { forced = f; cascade = 1; step(); });
                }
            }
        };
        h.postDelayed(tick, 500);
    }

    void finish(boolean won) {
        over = true;
        busy = true;
        lastWon = won;
        final int st = won ? (score >= L.s3 ? 3 : score >= L.s2 ? 2 : 1) : 0;
        lastStars = st;
        starsShown = 0;
        final String m = won ? "Level " + L.n + " complete!  " + "★★★".substring(0, st) + "\nScore: " + score
                : "Out of moves!\nScore: " + score;
        final boolean w = won;
        showEnd = true;
        endT = SystemClock.uptimeMillis();
        hammerMode = false;
        if (sfx != null) { if (won) sfx.win(); else sfx.lose(); }
        invalidate();
        h.postDelayed(() -> { if (lst != null) lst.onResult(w, st, score, m); }, won ? 2900 : 1600);
    }

    // ------------------------------------------------------------ input
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!canTouch()) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lastTouch = SystemClock.uptimeMillis();
                hintReady = false;
                float x = e.getX(), y = e.getY();
                if (gearRect.contains(x, y)) {
                    selR = -1;
                    if (lst != null) lst.onMenu();
                    break;
                }
                if (hamRect.contains(x, y)) {
                    selR = -1;
                    if (hammers > 0) hammerMode = !hammerMode;
                    else if (lst != null) lst.onBuyBooster(0);
                    invalidate();
                    break;
                }
                if (shufRect.contains(x, y)) {
                    selR = -1;
                    if (shuffles > 0) useShuffle();
                    else if (lst != null) lst.onBuyBooster(1);
                    break;
                }
                if (m5Rect.contains(x, y)) {
                    selR = -1;
                    if (moves5 > 0) useMoves5();
                    else if (lst != null) lst.onBuyBooster(2);
                    break;
                }
                if (crossRect.contains(x, y)) {
                    selR = -1;
                    if (crossBlast > 0) crossMode = !crossMode;
                    else if (lst != null) lst.onBuyBooster(3);
                    invalidate();
                    break;
                }
                if (chgRect.contains(x, y)) {
                    selR = -1;
                    if (colorChg > 0) chgMode = !chgMode;
                    else if (lst != null) lst.onBuyBooster(4);
                    invalidate();
                    break;
                }
                int c = (int) ((x - ox) / cell), r = (int) ((y - oy) / cell);
                if (x < ox || y < oy || c < 0 || c >= N || r < 0 || r >= N) {
                    selR = -1;
                } else if (hammerMode) {
                    selR = -1;
                    useHammer(r, c);
                } else if (crossMode) {
                    selR = -1;
                    useCross(r, c);
                } else if (chgMode) {
                    selR = -1;
                    useColorChg(r, c);
                } else {
                    selR = r; selC = c; dx0 = x; dy0 = y;
                }
                invalidate();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (selR < 0) break;
                float dx = e.getX() - dx0, dy = e.getY() - dy0;
                if (Math.max(Math.abs(dx), Math.abs(dy)) > cell * 0.35f) {
                    int r1 = selR, c1 = selC, r2 = r1, c2 = c1;
                    if (Math.abs(dx) > Math.abs(dy)) c2 += dx > 0 ? 1 : -1;
                    else r2 += dy > 0 ? 1 : -1;
                    selR = -1;
                    if (r2 >= 0 && r2 < N && c2 >= 0 && c2 < N) trySwap(r1, c1, r2, c2);
                    invalidate();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                selR = -1;
                invalidate();
                break;
        }
        return true;
    }

    // ------------------------------------------------------------ easing + draw helpers
    static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    static float easeInOut(float t) { return t < 0.5f ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t); }

    static float bounce(float t) {
        if (t < 1 / 2.75f) return 7.5625f * t * t;
        if (t < 2 / 2.75f) { t -= 1.5f / 2.75f; return 7.5625f * t * t + 0.75f; }
        if (t < 2.5f / 2.75f) { t -= 2.25f / 2.75f; return 7.5625f * t * t + 0.9375f; }
        t -= 2.625f / 2.75f;
        return 7.5625f * t * t + 0.984375f;
    }

    float phaseT() {
        if (!animating) return 1f;
        float t = (SystemClock.uptimeMillis() - animStart) / animDur;
        return Math.min(1f, Math.max(0f, t));
    }

    /** Glossy rounded panel with drop shadow + top highlight. */
    final java.util.HashMap<Long, Shader> shaderCache = new java.util.HashMap<>();
    final RectF rf3 = new RectF();

    Shader panelShader(int top, int bot, float h) {
        if (shaderCache.size() > 160) shaderCache.clear();
        long key = (((long) top) << 32) ^ (bot & 0xFFFFFFFFL) ^ ((long) (int) h << 8);
        Shader sh = shaderCache.get(key);
        if (sh == null) {
            sh = new LinearGradient(0, 0, 0, h, top, bot, Shader.TileMode.CLAMP);
            shaderCache.put(key, sh);
        }
        return sh;
    }

    /** Glossy shaded sphere gradient centred on (0,0); draw it after translating the canvas. */
    Shader sphereShader(int color, float r) {
        if (shaderCache.size() > 160) shaderCache.clear();
        int q = Math.max(1, Math.round(r / 3f));
        long key = ((long) color << 8) ^ (q * 7919L) ^ 0x5000000000000000L;
        Shader sh = shaderCache.get(key);
        if (sh == null) {
            float rr = q * 3f;
            sh = new RadialGradient(-rr * 0.3f, -rr * 0.4f, rr * 1.6f,
                    new int[]{Gems.lighten(color, 0.55f), color, Gems.darken(color, 0.4f)}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            shaderCache.put(key, sh);
        }
        return sh;
    }

    void panel(Canvas cv, RectF r, int top, int bot, float rad) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        rf2.set(r.left, r.top + rad * 0.22f, r.right, r.bottom + rad * 0.22f);
        cv.drawRoundRect(rf2, rad, rad, p);
        cv.save();
        cv.translate(0, r.top);
        rf3.set(r.left, 0, r.right, r.height());
        p.setColor(0xFFFFFFFF);
        p.setShader(panelShader(top, bot, r.height()));
        cv.drawRoundRect(rf3, rad, rad, p);
        p.setShader(null);
        cv.restore();
        p.setColor(0x38FFFFFF);
        rf2.set(r.left + rad * 0.25f, r.top + rad * 0.12f, r.right - rad * 0.25f, r.top + r.height() * 0.46f);
        cv.drawRoundRect(rf2, rad * 0.8f, rad * 0.8f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setColor(0x66FFFFFF);
        cv.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** Round glossy booster bubble with an icon and a count / plus badge. */
    void bubble(Canvas cv, float cx, float cy, float r, int color, int icon, int count, boolean active) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        cv.drawCircle(cx, cy + r * 0.14f, r, p);
        cv.save();
        cv.translate(cx, cy);
        p.setColor(0xFFFFFFFF);
        p.setShader(sphereShader(color, r));
        cv.drawCircle(0, 0, r, p);
        p.setShader(null);
        cv.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.1f);
        p.setColor(active ? 0xFFFFEB3B : 0xFFFFFFFF);
        cv.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55FFFFFF);
        rf2.set(cx - r * 0.65f, cy - r * 0.9f, cx + r * 0.1f, cy - r * 0.4f);
        cv.drawOval(rf2, p);
        Icons.draw(cv, p, icon, cx, cy, r * 0.58f);
        if (count >= 0) {
            float bx = cx + r * 0.72f, byy = cy + r * 0.72f, brr = r * 0.4f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(count > 0 ? 0xFFE53935 : 0xFF2EBF4A);
            cv.drawCircle(bx, byy, brr, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(brr * 0.2f);
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(bx, byy, brr, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            p.setTextSize(brr * 1.25f);
            cv.drawText(count > 0 ? String.valueOf(count) : "+", bx, byy + brr * 0.42f, p);
            p.setFakeBoldText(false);
        }
    }

    void useMoves5() {
        if (busy || over || moves5 <= 0) return;
        moves5--;
        movesLeft += 5;
        if (lst != null) lst.onMoves5Used();
        banner("+5 moves!");
        if (sfx != null) sfx.extra();
        invalidate();
    }

    void button3d(Canvas cv, RectF r, int color, String label) {
        float rad = r.height() / 2, h10 = r.height() * 0.1f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Gems.darken(color, 0.5f));
        rf2.set(r.left, r.top + h10, r.right, r.bottom);
        cv.drawRoundRect(rf2, rad, rad, p);
        p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(0, r.top, 0, r.bottom, Gems.lighten(color, 0.4f), color, Shader.TileMode.CLAMP));
        rf2.set(r.left, r.top, r.right, r.bottom - h10);
        cv.drawRoundRect(rf2, rad, rad, p);
        p.setShader(null);
        p.setColor(0x44FFFFFF);
        rf2.set(r.left + rad * 0.4f, r.top + h10 * 0.5f, r.right - rad * 0.4f, r.top + r.height() * 0.42f);
        cv.drawRoundRect(rf2, rad * 0.6f, rad * 0.6f, p);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(r.height() * 0.38f);
        p.setShadowLayer(3, 0, 2, 0xFF000000);
        cv.drawText(label, r.centerX(), r.centerY() + r.height() * 0.1f, p);
        p.clearShadowLayer();
        p.setFakeBoldText(false);
    }

    void starShape(Canvas cv, float cx, float cy, float r, int color) {
        starP.reset();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i % 2 == 0) ? r : r * 0.48f;
            float x = cx + (float) Math.cos(ang) * rr, y = cy + (float) Math.sin(ang) * rr;
            if (i == 0) starP.moveTo(x, y); else starP.lineTo(x, y);
        }
        starP.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        cv.save();
        cv.translate(0, r * 0.1f);
        cv.drawPath(starP, p);
        cv.restore();
        p.setColor(0xFFFFFFFF); p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.4f,
                new int[]{Gems.lighten(color, 0.6f), color, Gems.darken(color, 0.35f)}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPath(starP, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, r * 0.06f));
        p.setColor(Gems.darken(color, 0.5f));
        cv.drawPath(starP, p);
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int hh, int ow, int oh) { bgLevel = -1; }

    // ------------------------------------------------------------ main draw
    @Override
    protected void onDraw(Canvas cv) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        frameNow = now;
        frameDt = dt;
        lastFrame = now;
        float W = getWidth(), H = getHeight();

        float margin = W * 0.03f;
        cell = (W - 2 * margin) / N;
        if (cell * N > H * 0.66f) cell = H * 0.66f / N;
        float boardH = cell * N, rem = H - boardH;
        float topH = rem * 0.56f, botH = rem - topH;
        ox = (W - cell * N) / 2;
        oy = topH;

        if (!hintReady && canTouch() && now - lastTouch > 6000 && !over) computeHint();

        if (bgLevel != L.n || bgH != H) {
            int[] wc = worldColors(world(L.n));
            bg.setColor(0xFFFFFFFF); bg.setShader(new LinearGradient(0, 0, 0, H, wc[0], wc[1], Shader.TileMode.CLAMP));
            bgLevel = L.n;
            bgH = H;
        }

        cv.save();
        if (shake > 0) {
            cv.translate((fx.nextFloat() - 0.5f) * shake * 2, (fx.nextFloat() - 0.5f) * shake * 2);
            shake = Math.max(0, shake - dt * 45);
        }
        cv.drawRect(-40, -40, W + 40, H + 40, bg);

        // pattern: soft checker on top, stripes under the board (like the real thing)
        float sq = W / 9f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x16FFFFFF);
        for (int i = 0; i * sq < topH + sq; i++)
            for (int j = 0; j < 10; j++)
                if (((i + j) & 1) == 0) cv.drawRect(j * sq, i * sq, (j + 1) * sq, (i + 1) * sq, p);
        p.setColor(0x18FFFFFF);
        for (int j = 0; j < 12; j += 2) cv.drawRect(j * W / 12f, oy + boardH + 16, (j + 1) * W / 12f, H, p);

        // floating bokeh bubbles
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 14; i++) {
            float ph = ((now / (9000f + i * 700f)) + i * 0.137f) % 1f;
            float bx = W * (0.5f + 0.48f * (float) Math.sin(now / 7000.0 * (0.5 + i * 0.05) + i * 2.1));
            float by = H * (1.05f - ph * 1.15f);
            float br = W * (0.015f + 0.03f * ((i * 37) % 10) / 10f);
            p.setColor(0x22FFFFFF);
            cv.drawCircle(bx, by, br, p);
        }

        int[] wcol = worldColors(world(L.n));
        rf.set(-30, -90, W + 30, topH - 4);
        panel(cv, rf, Gems.darken(wcol[1], 0.25f), Gems.darken(wcol[1], 0.42f), 46);

        float ch = W * 0.5f, y0 = Math.max(0, (topH - ch) / 2);
        cv.save();
        cv.translate(0, y0);
        hudY0 = y0;
        drawHud(cv, W, ch);
        cv.restore();

        // board frame (gold bevel), glass inside
        float bw = cell * N;
        rf.set(ox - 16, oy - 16, ox + bw + 16, oy + boardH + 16);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x77000000);
        rf2.set(rf.left, rf.top + 10, rf.right, rf.bottom + 10);
        cv.drawRoundRect(rf2, 38, 38, p);
        p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xFFFFF3B8, 0xFFE9A83A, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, 38, 38, p);
        p.setShader(null);
        rf.set(ox - 6, oy - 6, ox + bw + 6, oy + boardH + 6);
        p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xAA9FB4FF, 0xAA6F86E6, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rf, 26, 26, p);
        p.setShader(null);

        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                p.setColor(((r + c) & 1) == 0 ? 0x48FFFFFF : 0x26FFFFFF);
                cv.drawRect(ox + c * cell, oy + r * cell, ox + (c + 1) * cell, oy + (r + 1) * cell, p);
                if (jelly[r][c] > 0) {
                    float x0 = ox + c * cell + 3, y0b = oy + r * cell + 3, x1 = ox + (c + 1) * cell - 3, y1 = oy + (r + 1) * cell - 3;
                    rf2.set(x0, y0b, x1, y1);
                    p.setColor(jelly[r][c] == 1 ? 0x99FF80AB : 0xCCFF4081);
                    cv.drawRoundRect(rf2, 14, 14, p);
                    p.setColor(0x55FFFFFF);
                    rf2.set(x0 + 6, y0b + 4, x1 - 6, y0b + (y1 - y0b) * 0.4f);
                    cv.drawRoundRect(rf2, 10, 10, p);
                }
            }
        }

        float t = phaseT();
        float e = animType == 1 ? bounce(t) : animType == 0 ? easeInOut(t) : t;
        rf.set(ox - 6, oy - 6, ox + bw + 6, oy + boardH + 6);
        cv.save();
        cv.clipRect(rf);

        if (hintReady && canTouch() && now - lastTouch > 6000) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
            p.setStyle(Paint.Style.FILL);
            p.setColor(((int) (110 + 100 * pulse) << 24) | 0xFFFFFF);
            cv.drawCircle(cx(hint[1]), cy(hint[0]), cell * (0.42f + 0.08f * pulse), p);
            cv.drawCircle(cx(hint[3]), cy(hint[2]), cell * (0.42f + 0.08f * pulse), p);
        }

        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                float gx = ox + (c + offX[r][c] * (1 - e)) * cell + cell / 2;
                float gy = oy + (r + offY[r][c] * (1 - e)) * cell + cell / 2;
                boolean cl = cleared != null && cleared[r][c];
                boolean special = (g[r][c] < BOMB && sp[r][c] != 0) || g[r][c] == BOMB;
                float scl = 1f;
                if (cl) {
                    scl = t < 0.25f ? 1f + 0.35f * (t / 0.25f) : Math.max(0f, 1.35f * (1f - (t - 0.25f) / 0.75f));
                } else {
                    long age = now - born[r][c];
                    if (born[r][c] != 0 && age < 450) {
                        float k = 1f - age / 450f;
                        scl = 1f + 0.7f * k * k;
                    } else if (special) {
                        scl = 1f + 0.07f * (float) Math.sin(now / 170.0 + r * 1.3 + c);
                    }
                }
                if (!cl && special) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0x33FFFFFF);
                    cv.drawCircle(gx, gy, cell * 0.5f * (1f + 0.08f * (float) Math.sin(now / 200.0 + c)), p);
                }
                Gems.draw(cv, p, g[r][c], sp[r][c], gx, gy, cell * 0.38f * scl, cl && t > 0.15f);
                if (lock[r][c] > 0 && !cl) {
                    float in = cell * 0.10f;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(cell * 0.09f);
                    p.setColor(0xFFB0BEC5);
                    rf2.set(gx - cell * 0.5f + in, gy - cell * 0.5f + in, gx + cell * 0.5f - in, gy + cell * 0.5f - in);
                    cv.drawRoundRect(rf2, cell * 0.16f, cell * 0.16f, p);
                    p.setStrokeWidth(cell * 0.06f);
                    p.setColor(0xFF78909C);
                    float b1 = rf2.left + rf2.width() / 3f, b2 = rf2.left + rf2.width() * 2f / 3f;
                    cv.drawLine(b1, rf2.top, b1, rf2.bottom, p);
                    cv.drawLine(b2, rf2.top, b2, rf2.bottom, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFF546E7A);
                    cv.drawCircle(gx, gy, cell * 0.11f, p);
                    p.setColor(0xFFFFFFFF);
                    cv.drawCircle(gx, gy, cell * 0.045f, p);
                }
            }
        }

        if (cleared != null && !beams.isEmpty()) {
            float k = (float) Math.sin(Math.PI * t);
            p.setStyle(Paint.Style.FILL);
            for (int[] b : beams) {
                int a = (int) (200 * (1 - t));
                p.setColor((a << 24) | 0xFFFFFF);
                if (b[0] == 1) {
                    float cyy = oy + (b[1] + 0.5f) * cell, hh = cell * (0.15f + 0.45f * k);
                    rf2.set(ox, cyy - hh, ox + N * cell, cyy + hh);
                    cv.drawRect(rf2, p);
                } else if (b[0] == 2) {
                    float cxx = ox + (b[1] + 0.5f) * cell, ww = cell * (0.15f + 0.45f * k);
                    rf2.set(cxx - ww, oy, cxx + ww, oy + N * cell);
                    cv.drawRect(rf2, p);
                } else {
                    cv.drawCircle(cx(b[2]), cy(b[1]), cell * (0.6f + 1.8f * t), p);
                }
            }
        }
        if (cleared != null && !flights.isEmpty()) {
            float tt = easeInOut(Math.min(1f, t * 1.15f));
            for (float[] fl : flights) {
                float x1 = cx((int) fl[1]), y1 = cy((int) fl[0]), x2 = cx((int) fl[3]), y2 = cy((int) fl[2]);
                float fx0 = x1 + (x2 - x1) * tt, fy0 = y1 + (y2 - y1) * tt - (float) Math.sin(Math.PI * tt) * cell * 0.9f;
                float ang = (float) Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
                cv.save();
                cv.rotate(ang, fx0, fy0);
                Gems.draw(cv, p, (int) fl[4], 4, fx0, fy0, cell * 0.30f, false);
                cv.restore();
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x88FFFFFF);
                cv.drawCircle(fx0 - (float) Math.cos(Math.toRadians(ang)) * cell * 0.3f, fy0 - (float) Math.sin(Math.toRadians(ang)) * cell * 0.3f, cell * 0.07f, p);
            }
        }
        cv.restore();

        p.setStyle(Paint.Style.FILL);
        for (Iterator<Particle> it = parts.iterator(); it.hasNext(); ) {
            Particle q = it.next();
            q.life -= dt;
            if (q.life <= 0) { it.remove(); continue; }
            q.vy += q.grav * dt;
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            int al = (int) (255 * Math.min(1f, q.life / q.max * 1.6f));
            p.setColor((al << 24) | (q.color & 0xFFFFFF));
            cv.drawCircle(q.x, q.y, q.size, p);
        }

        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        for (Iterator<Popup> it = pops.iterator(); it.hasNext(); ) {
            Popup pp = it.next();
            float age = (now - pp.t0) / 900f;
            if (age >= 1f) { it.remove(); continue; }
            p.setTextSize(pp.size * (1f + 0.3f * (1f - age)));
            p.setColor(((int) (255 * (1f - age * age)) << 24) | (pp.color & 0xFFFFFF));
            p.setShadowLayer(6, 0, 0, 0xFF000000);
            cv.drawText(pp.s, pp.x, pp.y - age * cell * 0.9f, p);
            p.clearShadowLayer();
        }
        p.setFakeBoldText(false);

        if (selR >= 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            p.setColor(0xFFFFFFFF);
            rf2.set(ox + selC * cell + 3, oy + selR * cell + 3, ox + (selC + 1) * cell - 3, oy + (selR + 1) * cell - 3);
            cv.drawRoundRect(rf2, 14, 14, p);
            p.setStyle(Paint.Style.FILL);
        }

        if (banner != null && now < bannerUntil) {
            float age = 1f - (bannerUntil - now) / 900f;
            float sc = age < 0.2f ? easeOutBack(age / 0.2f) : 1f;
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            float size = cell * 0.95f;
            p.setTextSize(size);
            float tw = p.measureText(banner);
            if (tw > W * 0.92f) size = size * W * 0.92f / tw;
            p.setTextSize(size * sc);
            p.setColor(((int) (255 * Math.min(1f, (1f - age) * 3f)) << 24) | 0xFFEB3B);
            p.setShadowLayer(12, 0, 0, 0xFF000000);
            cv.drawText(banner, W / 2, oy + cell * N / 2, p);
            p.clearShadowLayer();
            p.setFakeBoldText(false);
        }

        float yb = oy + boardH;
        if (tip != null && now < tipUntil) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(W * 0.04f);
            float tw = p.measureText(tip) + W * 0.08f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x99000000);
            rf2.set(W / 2 - tw / 2, yb + botH * 0.04f, W / 2 + tw / 2, yb + botH * 0.04f + W * 0.075f);
            cv.drawRoundRect(rf2, W * 0.04f, W * 0.04f, p);
            p.setColor(0xFFFFFFFF);
            cv.drawText(tip, W / 2, yb + botH * 0.04f + W * 0.052f, p);
        }
        float barH = Math.min(botH * 0.66f, W * 0.2f);
        float barY = yb + (botH - barH) / 2 + botH * 0.05f;
        rf.set(W * 0.05f, barY, W * 0.95f, barY + barH);
        panel(cv, rf, 0xFFEAFBFF, 0xFF8FD3F0, barH * 0.5f);
        float br = barH * 0.34f, by2 = barY + barH / 2;
        float[] bxs = {W * 0.085f, W * 0.245f, W * 0.405f, W * 0.595f, W * 0.755f, W * 0.915f};
        gearRect.set(bxs[0] - br, by2 - br, bxs[0] + br, by2 + br);
        hamRect.set(bxs[1] - br, by2 - br, bxs[1] + br, by2 + br);
        shufRect.set(bxs[2] - br, by2 - br, bxs[2] + br, by2 + br);
        m5Rect.set(bxs[3] - br, by2 - br, bxs[3] + br, by2 + br);
        crossRect.set(bxs[4] - br, by2 - br, bxs[4] + br, by2 + br);
        chgRect.set(bxs[5] - br, by2 - br, bxs[5] + br, by2 + br);
        bubble(cv, bxs[0], by2, br, 0xFFFF5FA2, Icons.GEAR, -1, false);
        bubble(cv, bxs[1], by2, br, hammerMode ? 0xFFFFB300 : 0xFF2F8CFF, Icons.HAMMER, hammers, hammerMode);
        bubble(cv, bxs[2], by2, br, 0xFF2F8CFF, Icons.SHUFFLE, shuffles, false);
        bubble(cv, bxs[3], by2, br, 0xFFFF8A00, Icons.MOVES, moves5, false);
        bubble(cv, bxs[4], by2, br, crossMode ? 0xFFFFB300 : 0xFF26A69A, Icons.CROSS, crossBlast, crossMode);
        bubble(cv, bxs[5], by2, br, chgMode ? 0xFFFFB300 : 0xFFAB47BC, Icons.PALETTE, colorChg, chgMode);
        if (hammerMode || crossMode || chgMode) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            p.setTextSize(W * 0.04f);
            p.setColor(0xFFFFFFFF);
            p.setShadowLayer(6, 0, 0, 0xFF000000);
            cv.drawText(hammerMode ? "Tap a candy to smash it!"
                    : crossMode ? "Tap a tile to fire a cross blast!"
                    : "Tap a candy to change its color!", W / 2, barY - W * 0.02f, p);
            p.clearShadowLayer();
            p.setFakeBoldText(false);
        }
        cv.restore();

        if (showEnd) drawEnd(cv, W, H, now);
        boolean fast = animating || !parts.isEmpty() || !pops.isEmpty() || shake > 0 || showEnd
                || (banner != null && now < bannerUntil) || hintReady;
        if (fast) postInvalidateOnAnimation(); else postInvalidateDelayed(45);
    }

    void drawEnd(Canvas cv, float W, float H, long now) {
        long age = now - endT;
        float a = Math.min(1f, age / 300f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(((int) (170 * a) << 24));
        cv.drawRect(0, 0, W, H, p);

        float tt = Math.max(0f, Math.min(1f, (age - 100) / 500f));
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(W * 0.095f * easeOutBack(tt));
        p.setColor(lastWon ? 0xFFFFD54F : 0xFFFF8A80);
        p.setShadowLayer(14, 0, 4, 0xFF000000);
        cv.drawText(lastWon ? "Level Complete!" : "Out of moves!", W / 2, H * 0.30f, p);
        p.clearShadowLayer();

        if (lastWon) {
            if (age < 1800) confetti(4);
            for (int i = 0; i < 3; i++) {
                float st = (age - (550 + i * 400)) / 450f;
                if (st < 0) continue;
                float s = easeOutBack(Math.min(1f, st));
                float x = W / 2 + (i - 1) * W * 0.26f, y = H * 0.43f - (i == 1 ? W * 0.05f : 0);
                boolean got = i < lastStars;
                if (got && i >= starsShown) { starsShown = i + 1; if (sfx != null) sfx.star(i); }
                cv.save();
                cv.rotate((1f - Math.min(1f, st)) * 40f, x, y);
                starShape(cv, x, y, W * 0.12f * s, got ? 0xFFFFC107 : 0xFF6D6D8A);
                cv.restore();
            }
        }
        float sa = Math.max(0f, Math.min(1f, (age - (lastWon ? 1700 : 600)) / 400f));
        p.setColor(((int) (255 * sa) << 24) | 0xFFFFFF);
        p.setTextSize(W * 0.07f);
        float cu = Math.max(0f, Math.min(1f, (age - (lastWon ? 1700 : 600)) / 1000f));
        cv.drawText("Score  " + Math.round(score * cu), W / 2, H * 0.57f, p);
        p.setFakeBoldText(false);
    }

    // ------------------------------------------------------------ HUD
    void addChip(int type, int spv, String text) {
        if (chipN >= 6) return;
        chT[chipN] = type; chS[chipN] = spv; chX[chipN] = text;
        chipN++;
    }

    void drawChip(Canvas cv, float x, float y, float w, float h, int type, int spv, String text, float bump) {
        rf.set(x, y, x + w, y + h);
        panel(cv, rf, 0xFF7E5BC4, 0xFF3F2A78, h * 0.45f);
        float icx = x + h * 0.55f, icy = y + h / 2, ic = h * 0.3f;
        if (type == -1) starShape(cv, icx, icy, ic * 1.15f, 0xFFFFC107);
        else if (type == -2) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(0, icy - ic, 0, icy + ic, 0xFFFFB6D0, 0xFFE91E63, Shader.TileMode.CLAMP));
            rf2.set(icx - ic, icy - ic, icx + ic, icy + ic);
            cv.drawRoundRect(rf2, ic * 0.4f, ic * 0.4f, p);
            p.setShader(null);
        } else Gems.draw(cv, p, type, spv, icx, icy, ic * (1f + 0.25f * bump), false);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.LEFT);
        p.setFakeBoldText(true);
        float tsz = h * 0.42f * (1f + 0.3f * bump);
        p.setTextSize(tsz);
        float avail = w - h * 1.1f, tw = p.measureText(text);
        if (tw > avail) p.setTextSize(tsz * avail / tw);
        cv.drawText(text, x + h * 1.0f, y + h * 0.65f, p);
        p.setFakeBoldText(false);
    }

    void drawHud(Canvas cv, float W, float ch) {
        float ts = W * 0.05f;
        if (score != lastScoreSeen) { if (score > lastScoreSeen) scoreBumpT = frameNow; lastScoreSeen = score; }
        if (movesLeft != lastMovesSeen) { movesBumpT = frameNow; lastMovesSeen = movesLeft; }
        shownScore += (score - shownScore) * Math.min(1f, frameDt * 7f);
        if (Math.abs(score - shownScore) < 1f) shownScore = score;
        float sBump = Math.max(0f, 1f - (frameNow - scoreBumpT) / 380f);
        float mBump = Math.max(0f, 1f - (frameNow - movesBumpT) / 300f);
        p.setStyle(Paint.Style.FILL);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);

        rf.set(W * 0.04f, ch * 0.02f, W * 0.30f, ch * 0.34f);
        panel(cv, rf, 0xFF9C6BE6, 0xFF4527A0, ch * 0.08f);
        p.setColor(0xFFFFE082);
        p.setTextSize(ts * 0.62f);
        cv.drawText("LEVEL", rf.centerX(), rf.top + rf.height() * 0.36f, p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(ts * 1.7f);
        cv.drawText(String.valueOf(L.n), rf.centerX(), rf.top + rf.height() * 0.86f, p);

        rf.set(W * 0.33f, ch * 0.02f, W * 0.69f, ch * 0.34f);
        panel(cv, rf, 0xFF4FB3FF, 0xFF1565C0, ch * 0.08f);
        p.setColor(0xFFE3F2FD);
        p.setTextSize(ts * 0.62f);
        cv.drawText("SCORE", rf.centerX(), rf.top + rf.height() * 0.36f, p);
        p.setColor(sBump > 0.05f ? 0xFFFFF59D : 0xFFFFFFFF);
        p.setTextSize(ts * 1.5f * (1f + 0.3f * sBump));
        cv.drawText(String.valueOf(Math.round(shownScore)), rf.centerX(), rf.top + rf.height() * 0.86f, p);

        float mx = W * 0.845f, my = ch * 0.18f, mr = W * 0.085f;
        badgeX = mx;
        badgeY = my + hudY0;
        p.setColor(0x66000000);
        cv.drawCircle(mx, my + mr * 0.12f, mr, p);
        p.setColor(0xFFFFFFFF); p.setShader(new RadialGradient(mx - mr * 0.3f, my - mr * 0.4f, mr * 1.5f,
                new int[]{0xFFFFCC80, movesLeft <= 5 ? 0xFFE53935 : 0xFFFB8C00, 0xFF8E2E0A}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.drawCircle(mx, my, mr, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        p.setColor(0xFFFFE082);
        cv.drawCircle(mx, my, mr, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(ts * 1.6f * (1f + 0.35f * mBump) * (movesLeft <= 5 ? 1f + 0.08f * (float) Math.sin(frameNow / 110.0) : 1f));
        cv.drawText(String.valueOf(movesLeft), mx, my + mr * 0.25f, p);
        p.setTextSize(ts * 0.5f);
        p.setColor(0xFFFFF3E0);
        cv.drawText("MOVES", mx, my - mr * 0.45f, p);

        float bx = W * 0.06f, bw = W * 0.88f, byy = ch * 0.47f, bh = ch * 0.07f;
        p.setColor(0x77000000);
        rf.set(bx, byy, bx + bw, byy + bh);
        cv.drawRoundRect(rf, bh / 2, bh / 2, p);
        float fw = bw * Math.min(1f, score / (float) L.s3);
        if (fw > bh * 0.6f) {
            p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(0, byy, 0, byy + bh, 0xFFFFF59D, 0xFFFF8F00, Shader.TileMode.CLAMP));
            rf.set(bx, byy, bx + Math.max(fw, bh), byy + bh);
            cv.drawRoundRect(rf, bh / 2, bh / 2, p);
            p.setShader(null);
        }
        p.setColor(0x44FFFFFF);
        rf.set(bx + bh * 0.3f, byy + bh * 0.12f, bx + bw - bh * 0.3f, byy + bh * 0.45f);
        cv.drawRoundRect(rf, bh / 4, bh / 4, p);
        float f1 = L.target > 0 ? Math.min(0.9f, L.target / (float) L.s3) : 0.03f;
        float[] fr = {f1, L.s2 / (float) L.s3, 1f};
        int[] need = {L.target > 0 ? L.target : 0, L.s2, L.s3};
        for (int i = 0; i < 3; i++) {
            boolean reach = score >= need[i] && (i > 0 || L.target > 0 || goalMet());
            if (reach && starT[i] == 0) starT[i] = frameNow;
            float k = reach ? easeOutBack(Math.min(1f, (frameNow - starT[i]) / 500f)) : 0f;
            cv.save();
            cv.rotate(reach ? (1f - Math.min(1f, (frameNow - starT[i]) / 500f)) * 50f : 0f, bx + bw * fr[i], byy - ts * 0.2f);
            starShape(cv, bx + bw * fr[i], byy - ts * 0.2f, ts * 0.62f * (reach ? 0.85f + 0.3f * k : 1f), reach ? 0xFFFFC107 : 0xFF8888A8);
            cv.restore();
        }

        chipN = 0;
        if (L.target > 0) addChip(-1, 0, String.valueOf(L.target));
        if (L.jelly) addChip(-2, 0, Math.max(0, jellyLeft) + " left");
        if (L.ing > 0) addChip(ING, 0, ingGot + "/" + L.ing);
        for (int i = 0; i < L.ordCount; i++) addChip(L.ordT[i], 0, Math.min(ordGot[i], L.ordN[i]) + "/" + L.ordN[i]);
        if (L.blocks > 0) addChip(BLOCK, Math.min(3, L.blockHp), Math.min(blocksGot, L.blocks) + "/" + L.blocks);
        int per = chipN <= 3 ? Math.max(1, chipN) : (chipN + 1) / 2;
        float chh = ch * 0.19f, gap = W * 0.02f;
        float cw = Math.min(W * 0.29f, (W * 0.92f - gap * (per - 1)) / per);
        for (int i = 0; i < chipN; i++) {
            int row = i / per, col = i % per;
            int inRow = Math.min(per, chipN - row * per);
            float rowW = inRow * cw + (inRow - 1) * gap;
            float x = (W - rowW) / 2 + col * (cw + gap);
            float y = ch * 0.60f + row * (chh + ch * 0.03f);
            if (!chX[i].equals(chLast[i])) { chBump[i] = frameNow; chLast[i] = chX[i]; }
            drawChip(cv, x, y, cw, chh, chT[i], chS[i], chX[i], Math.max(0f, 1f - (frameNow - chBump[i]) / 420f));
        }
        p.setFakeBoldText(false);
    }
}
