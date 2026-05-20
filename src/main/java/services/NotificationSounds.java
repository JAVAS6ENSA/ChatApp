package services;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

import java.io.ByteArrayInputStream;


public class NotificationSounds {

    private static final int SAMPLE_RATE = 22050;
    private static final AudioFormat FORMAT =
            new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private static volatile boolean enabled = true;

    // Cached one-shot clips
    private static Clip sentClip;
    private static Clip recvClip;

    // Cached looped clips
    private static Clip incomingRingClip;
    private static Clip outgoingDialClip;

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled()        { return enabled; }

    /** Short, soft blip the sender hears when their message goes out. */
    public static void messageSent() {
        if (!enabled) return;
        Clip c = sentClip != null ? sentClip : (sentClip = buildOneShot(
                tone(880, 70, 0.35, 12, 28)));
        playOneShot(c);
    }

    /** Slightly lower ding for incoming messages. */
    public static void messageReceived() {
        if (!enabled) return;
        Clip c = recvClip != null ? recvClip : (recvClip = buildOneShot(
                concat(tone(660, 80, 0.45, 12, 28),
                       tone(990, 110, 0.45, 12, 50))));
        playOneShot(c);
    }

    /** Telephone-style two-tone ringer that loops while the call is incoming. */
    public static void startIncomingRing() {
        if (!enabled) return;
        Clip c = incomingRingClip != null ? incomingRingClip
                : (incomingRingClip = buildLoop(buildRingPattern()));
        if (c == null) return;
        c.setFramePosition(0);
        c.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public static void stopIncomingRing() {
        if (incomingRingClip != null) {
            try { incomingRingClip.stop(); } catch (Exception ignored) {}
        }
    }

    /** Caller-side dial-out beep that loops while we're waiting for an answer. */
    public static void startOutgoingDial() {
        if (!enabled) return;
        Clip c = outgoingDialClip != null ? outgoingDialClip
                : (outgoingDialClip = buildLoop(buildDialPattern()));
        if (c == null) return;
        c.setFramePosition(0);
        c.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public static void stopOutgoingDial() {
        if (outgoingDialClip != null) {
            try { outgoingDialClip.stop(); } catch (Exception ignored) {}
        }
    }

    /** Stop everything (called when leaving the chat screen). */
    public static void stopAll() {
        stopIncomingRing();
        stopOutgoingDial();
    }

    // ─── synthesis helpers ───────────────────────────────────────

    /** PCM bytes for a single sine tone, with a smooth fade-in/out envelope. */
    private static byte[] tone(double freq, int durationMs, double amplitude,
                                int fadeInMs, int fadeOutMs) {
        int total = SAMPLE_RATE * durationMs / 1000;
        int fadeIn = SAMPLE_RATE * fadeInMs / 1000;
        int fadeOut = SAMPLE_RATE * fadeOutMs / 1000;
        double w = 2 * Math.PI * freq / SAMPLE_RATE;
        byte[] data = new byte[total * 2];
        for (int i = 0; i < total; i++) {
            double env = 1.0;
            if (i < fadeIn) env = i / (double) fadeIn;
            int tailStart = total - fadeOut;
            if (i >= tailStart) env = Math.max(0, (total - i) / (double) fadeOut);
            short s = (short) (Math.sin(w * i) * 32760 * amplitude * env);
            data[2 * i]     = (byte) (s & 0xff);
            data[2 * i + 1] = (byte) ((s >> 8) & 0xff);
        }
        return data;
    }

    private static byte[] silence(int durationMs) {
        return new byte[SAMPLE_RATE * 2 * durationMs / 1000];
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, pos, p.length); pos += p.length; }
        return out;
    }

    private static byte[] buildRingPattern() {
        // brrring-brrring — two short bursts then a long silence so the loop
        // matches the cadence of a classic landline ringer.
        byte[] burst = concat(
                tone(440, 180, 0.55, 16, 50),
                silence(60),
                tone(480, 180, 0.55, 16, 50));
        return concat(burst, silence(700), burst, silence(1400));
    }

    private static byte[] buildDialPattern() {
        // Western dial tone: 350 + 440 Hz mixed. We approximate by mixing
        // two tones sample-by-sample.
        int durationMs = 1200;
        int gapMs = 350;
        byte[] mixed = mixTone(350, 440, durationMs, 0.32);
        return concat(mixed, silence(gapMs));
    }

    private static byte[] mixTone(double f1, double f2, int durationMs, double amplitude) {
        int total = SAMPLE_RATE * durationMs / 1000;
        double w1 = 2 * Math.PI * f1 / SAMPLE_RATE;
        double w2 = 2 * Math.PI * f2 / SAMPLE_RATE;
        byte[] data = new byte[total * 2];
        int fade = SAMPLE_RATE / 50;
        for (int i = 0; i < total; i++) {
            double env = 1.0;
            if (i < fade)              env = i / (double) fade;
            else if (i > total - fade) env = Math.max(0, (total - i) / (double) fade);
            double s = (Math.sin(w1 * i) + Math.sin(w2 * i)) * 0.5 * amplitude * env;
            short v = (short) (s * 32760);
            data[2 * i]     = (byte) (v & 0xff);
            data[2 * i + 1] = (byte) ((v >> 8) & 0xff);
        }
        return data;
    }

    private static Clip buildOneShot(byte[] pcm) {
        try {
            Clip clip = AudioSystem.getClip();
            AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(pcm), FORMAT, pcm.length / 2);
            clip.open(ais);
            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) clip.setFramePosition(0);
            });
            return clip;
        } catch (Exception e) {
            System.err.println("[NotificationSounds] cannot prepare one-shot clip: " + e.getMessage());
            return null;
        }
    }

    private static Clip buildLoop(byte[] pcm) {
        try {
            Clip clip = AudioSystem.getClip();
            AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(pcm), FORMAT, pcm.length / 2);
            clip.open(ais);
            return clip;
        } catch (Exception e) {
            System.err.println("[NotificationSounds] cannot prepare loop clip: " + e.getMessage());
            return null;
        }
    }

    private static void playOneShot(Clip clip) {
        if (clip == null) return;
        try {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        } catch (Exception ignored) {}
    }
}
