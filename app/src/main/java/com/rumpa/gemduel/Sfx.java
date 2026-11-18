package com.rumpa.gemduel;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;

import java.util.Random;

/**
 * Synthesised bell-like sound effects + a soft looping background tune (no audio files).
 * Everything is generated at start-up with additive synthesis, so it sounds like candy chimes
 * instead of plain beeps.
 */
public class Sfx {
    static final int SR = 22050;
    public boolean on = true;
    public float vol = 1f, mvol = 1f;
    volatile boolean musicOn = true, wantMusic = false, generating = false;
    volatile AudioTrack music;
    final Vibrator vib;
    final Handler h = new Handler(Looper.getMainLooper());
    final short[][] pop = new short[4][];
    final short[][] star = new short[3][];
    volatile short[] swap, bad, win, lose, extra, boom, whoosh, coin, click;
    volatile int active = 0;

    static final double[] PENT = {523.25, 587.33, 659.25, 783.99, 880.00, 1046.50, 1174.66, 1318.51};

    public Sfx(Vibrator v) {
        vib = v;
        new Thread(this::synth).start();
    }

    /** Sounds are synthesised on a background thread so the app starts instantly. */
    void synth() {
        double[] popNotes = {523.25, 659.25, 783.99, 1046.50};
        for (int k = 0; k < 4; k++) {
            float[] b = new float[(int) (SR * 0.3)];
            click(b, 0.07);
            bell(b, 0, popNotes[k], 0.28, 0.55, 10);
            bell(b, 0, popNotes[k] * 2, 0.16, 0.12, 22);
            pop[k] = out(b);
        }
        double[] starNotes = {783.99, 987.77, 1174.66};
        for (int k = 0; k < 3; k++) {
            float[] b = new float[(int) (SR * 0.9)];
            bell(b, 0, starNotes[k], 0.85, 0.5, 3.2);
            bell(b, 0, starNotes[k] * 2, 0.5, 0.2, 6);
            bell(b, 0.05, starNotes[k] * 1.5, 0.6, 0.14, 5);
            star[k] = out(b);
        }
        float[] b = new float[(int) (SR * 0.12)];
        click(b, 0.25);
        bell(b, 0, 880, 0.1, 0.22, 45);
        swap = out(b);

        b = new float[(int) (SR * 0.4)];
        bell(b, 0, 196.0, 0.32, 0.6, 9);
        bell(b, 0.12, 164.8, 0.3, 0.6, 9);
        bad = out(b);

        b = new float[(int) (SR * 0.6)];
        bell(b, 0, 1318.5, 0.5, 0.35, 7);
        bell(b, 0.07, 1568.0, 0.5, 0.35, 7);
        bell(b, 0.14, 2093.0, 0.45, 0.35, 7);
        extra = out(b);

        b = new float[(int) (SR * 0.55)];
        Random rn = new Random(5);
        double ph = 0, lp = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / (double) SR;
            double f = 42 + 120 * Math.exp(-t * 9);
            ph += 2 * Math.PI * f / SR;
            lp += ((rn.nextDouble() * 2 - 1) - lp) * 0.08;
            b[i] = (float) ((Math.sin(ph) * 0.9 + lp * 1.2 * Math.exp(-t * 14)) * Math.exp(-t * 5.5));
        }
        boom = out(b);

        b = new float[(int) (SR * 0.4)];
        double y = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / (double) b.length;
            y += ((rn.nextDouble() * 2 - 1) - y) * (0.03 + 0.42 * Math.sin(Math.PI * t));
            b[i] = (float) (y * Math.sin(Math.PI * t) * 1.5);
        }
        whoosh = out(b);

        b = new float[(int) (SR * 0.6)];
        bell(b, 0, 1318.5, 0.5, 0.4, 6);
        bell(b, 0.08, 1760.0, 0.5, 0.4, 6);
        coin = out(b);

        b = new float[(int) (SR * 0.08)];
        click(b, 0.3);
        bell(b, 0, 1000, 0.06, 0.25, 60);
        click = out(b);

        b = new float[(int) (SR * 2.3)];
        double[] mel = {523.25, 659.25, 783.99, 1046.50};
        for (int i = 0; i < 4; i++) {
            bell(b, i * 0.13, mel[i], 0.9, 0.45, 4);
            bell(b, i * 0.13, mel[i] * 2, 0.5, 0.12, 8);
        }
        double[] chord = {523.25, 659.25, 783.99, 1046.50};
        for (double f : chord) bell(b, 0.58, f, 1.6, 0.28, 2.4);
        bell(b, 0.85, 1318.5, 1.3, 0.2, 3);
        bell(b, 1.0, 1568.0, 1.2, 0.16, 3);
        win = out(b);

        b = new float[(int) (SR * 1.8)];
        double[] sad = {392.0, 329.63, 261.63};
        for (int i = 0; i < 3; i++) bell(b, i * 0.28, sad[i], 0.9, 0.5, 3.5);
        bell(b, 0.8, 130.81, 0.95, 0.35, 2.2);
        lose = out(b);

        // build the tune in the background
        generating = true;
        new Thread(() -> {
            final short[] d = buildMusic();
            h.post(() -> {
                try {
                    AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, SR, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, d.length * 2, AudioTrack.MODE_STATIC);
                    t.write(d, 0, d.length);
                    t.setLoopPoints(0, d.length, -1);
                    t.setVolume(0.30f * mvol);
                    music = t;
                    generating = false;
                    if (wantMusic && musicOn) t.play();
                } catch (Exception e) {
                    generating = false;
                }
            });
        }).start();
    }

    // ---------------------------------------------------------------- synthesis helpers
    static void bell(float[] b, double startSec, double f, double dur, double amp, double decay) {
        int s0 = (int) (startSec * SR), n = (int) (dur * SR);
        for (int i = 0; i < n; i++) {
            int idx = s0 + i;
            if (idx >= b.length) break;
            double t = i / (double) SR;
            double env = Math.min(1.0, t / 0.004) * Math.exp(-t * decay);
            double w = Math.sin(2 * Math.PI * f * t)
                    + 0.35 * Math.sin(4 * Math.PI * f * t) * Math.exp(-t * 8)
                    + 0.12 * Math.sin(6 * Math.PI * f * t) * Math.exp(-t * 14);
            b[idx] += (float) (w * env * amp);
        }
    }

    /** Same as bell() but wraps around the buffer end so a looped tune has no seam. */
    static void bellWrap(float[] b, double startSec, double f, double dur, double amp, double decay) {
        int s0 = (int) (startSec * SR), n = (int) (dur * SR);
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double env = Math.min(1.0, t / 0.006) * Math.exp(-t * decay);
            double w = Math.sin(2 * Math.PI * f * t) + 0.3 * Math.sin(4 * Math.PI * f * t) * Math.exp(-t * 6);
            b[(s0 + i) % b.length] += (float) (w * env * amp);
        }
    }

    static void pad(float[] b, double startSec, double dur, double f, double amp) {
        int s0 = (int) (startSec * SR), n = (int) (dur * SR);
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double env = Math.min(1.0, t / 0.5) * Math.min(1.0, (dur - t) / 0.6);
            double w = Math.sin(2 * Math.PI * f * t) + 0.25 * Math.sin(4 * Math.PI * f * t) + 0.12 * Math.sin(2 * Math.PI * f * 1.004 * t);
            b[(s0 + i) % b.length] += (float) (w * env * amp);
        }
    }

    static void click(float[] b, double amp) {
        Random r = new Random(9);
        int n = Math.min(b.length, (int) (SR * 0.008));
        for (int i = 0; i < n; i++) b[i] += (float) ((r.nextDouble() * 2 - 1) * amp * (1 - i / (double) n));
    }

    static short[] out(float[] b) {
        short[] d = new short[b.length];
        for (int i = 0; i < b.length; i++) {
            double x = Math.tanh(b[i] * 1.2) * 28000;
            double fade = Math.min(1.0, (b.length - i) / 200.0);
            d[i] = (short) (x * fade);
        }
        return d;
    }

    /** ~21 s gentle pentatonic loop: pad chords, soft bass, bell arpeggio, sparse lead. */
    static short[] buildMusic() {
        double beat = 60.0 / 92.0;
        int bars = 8;
        float[] b = new float[(int) (bars * 4 * beat * SR)];
        double[][] chords = {{261.63, 329.63, 392.00}, {220.00, 261.63, 329.63}, {174.61, 220.00, 261.63}, {196.00, 246.94, 293.66}};
        double[] bass = {130.81, 110.00, 87.31, 98.00};
        int[] arp = {0, 1, 2, 1, 2, 1, 0, 1};
        Random r = new Random(11);
        for (int bar = 0; bar < bars; bar++) {
            int ci = bar % 4;
            double t0 = bar * 4 * beat;
            for (double f : chords[ci]) pad(b, t0, 4 * beat, f, 0.05);
            bellWrap(b, t0, bass[ci], beat * 1.6, 0.20, 3);
            bellWrap(b, t0 + 2 * beat, bass[ci], beat * 1.6, 0.15, 3);
            for (int e = 0; e < 8; e++) {
                double f = chords[ci][arp[e]] * 2;
                bellWrap(b, t0 + e * beat / 2, f, 0.55, 0.10 + (e % 2 == 0 ? 0.03 : 0), 5);
            }
            if (bar >= 4) {
                bellWrap(b, t0 + beat, PENT[r.nextInt(PENT.length)], 1.1, 0.08, 3);
                bellWrap(b, t0 + 3 * beat, PENT[r.nextInt(PENT.length)], 1.1, 0.08, 3);
            }
        }
        short[] d = new short[b.length];
        for (int i = 0; i < b.length; i++) d[i] = (short) (Math.tanh(b[i] * 1.1) * 26000);
        return d;
    }

    // ---------------------------------------------------------------- playback
    void play(short[] d) {
        if (!on || d == null || active >= 6) return;
        active++;
        try {
            final AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, SR,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    d.length * 2, AudioTrack.MODE_STATIC);
            t.write(d, 0, d.length);
            t.setVolume(vol);
            t.play();
            h.postDelayed(() -> {
                try { t.release(); } catch (Exception ignored) { }
                active = Math.max(0, active - 1);
            }, d.length * 1000L / SR + 300);
        } catch (Throwable e) {
            active = Math.max(0, active - 1);
        }
    }

    public void startMusic() {
        wantMusic = true;
        AudioTrack t = music;
        if (musicOn && t != null) {
            try { t.play(); } catch (Exception ignored) { }
        }
    }

    public void pauseMusic() {
        wantMusic = false;
        try { if (music != null) music.pause(); } catch (Exception ignored) { }
    }

    public void setMusic(boolean enable) {
        musicOn = enable;
        if (!enable) {
            try { if (music != null) music.pause(); } catch (Exception ignored) { }
        } else if (wantMusic) {
            startMusic();
        }
    }

    public boolean isMusicOn() { return musicOn; }

    public void setMusicVolume(float v) {
        mvol = v;
        try { if (music != null) music.setVolume(0.30f * mvol); } catch (Exception ignored) { }
    }

    public void pop(int k) { play(pop[Math.max(0, Math.min(3, k))]); }
    public void star(int k) { play(star[Math.max(0, Math.min(2, k))]); buzz(25); }
    public void swap() { play(swap); }
    public void bad() { play(bad); buzz(30); }
    public void win() { play(win); buzz(150); }
    public void lose() { play(lose); buzz(250); }
    public void extra() { play(extra); }
    public void boom() { play(boom); buzz(60); }
    public void whoosh() { play(whoosh); }
    public void coin() { play(coin); }
    public void click() { play(click); }

    public void buzz(long ms) {
        if (!on || vib == null) return;
        try { vib.vibrate(ms); } catch (Exception ignored) { }
    }
}
