import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Generates Drift's bundled sleep sounds and tile artwork from scratch.
 *
 * Everything here is synthesized, so the output carries no third-party
 * licensing — the same property the original four sounds were built for. Seeds
 * are fixed, so re-running this reproduces byte-identical output.
 *
 *   java tools/GenerateAssets.java [outDir]     (default: app/src/main/assets)
 *
 * Audio is written as 16-bit mono WAV; convert to OGG with:
 *   ffmpeg -i x.wav -c:a libvorbis -q:a 4 x.ogg
 *
 * Loops are made seamless by generating (length + crossfade) samples and
 * equal-power blending the overrun back over the head, so the sample after the
 * last one is literally the sample that followed it in the source stream.
 */
public final class GenerateAssets {

    static final int SR = 44100;
    static final double XFADE_SEC = 2.0;

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : "app/src/main/assets");
        Path sounds = root.resolve("sounds");
        Path images = root.resolve("images");
        Files.createDirectories(sounds);
        Files.createDirectories(images);

        // ── Audio ────────────────────────────────────────────────────────────
        render(sounds, "rain", 30, 101, GenerateAssets::rain);
        render(sounds, "fan", 20, 102, GenerateAssets::fan);
        render(sounds, "fireplace", 40, 103, GenerateAssets::fireplace);
        render(sounds, "wind", 40, 104, GenerateAssets::wind);
        render(sounds, "thunder", 60, 105, GenerateAssets::thunder);
        render(sounds, "stream", 30, 106, GenerateAssets::stream);
        // Re-rendered longer than the original 30s: the swell cycle was short
        // enough to hear repeating once you were lying still listening to it.
        render(sounds, "ocean_waves", 90, 107, GenerateAssets::ocean);

        // ── Artwork ──────────────────────────────────────────────────────────
        // Cool nocturnal glow-vignettes, matching the existing tiles. Each is a
        // radial halo in the sound's hue over near-black, plus banding texture.
        art(images, "rain", 201, 0x4C7BA6, 0.55, Texture.STREAKS_V);
        art(images, "fan", 202, 0x6E7A99, 0.40, Texture.GRAIN);
        art(images, "fireplace", 203, 0xC2662B, 0.60, Texture.EMBER);
        art(images, "wind", 204, 0x6E93A0, 0.45, Texture.STREAKS_H);
        art(images, "thunder", 205, 0x7A6BC4, 0.50, Texture.STREAKS_V);
        art(images, "stream", 206, 0x3E8C8C, 0.55, Texture.STREAKS_H);

        System.out.println("done -> " + root.toAbsolutePath());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio plumbing
    // ─────────────────────────────────────────────────────────────────────────

    /** Per-sample generator. Not a BiFunction, to keep 14M samples unboxed. */
    interface Voice { double sample(int i, Ctx c); }

    /** Scratch state a voice carries between samples (filters, envelopes). */
    static final class State {
        double lp1, lp2, lp3, hp, band, low, env, phase;
        double[] mem = new double[8];
    }

    static void render(Path dir, String name, int seconds, long seed, Voice voice)
            throws Exception {
        int len = seconds * SR;
        int xf = (int) (XFADE_SEC * SR);
        Ctx ctx = new Ctx(new Random(seed), new State());

        double[] raw = new double[len + xf];
        for (int i = 0; i < raw.length; i++) raw[i] = voice.sample(i, ctx);

        double[] out = seamless(raw, len, xf);
        normalize(out, 0.88);
        writeWav(out, dir.resolve(name + ".wav").toFile());
        System.out.printf("  %-12s %3ds  %.1f MB (wav)%n", name, seconds, out.length * 2 / 1e6);
    }

    static final class Ctx {
        final Random r; final State s;
        Ctx(Random r, State s) { this.r = r; this.s = s; }
    }

    /**
     * Equal-power blend of the overrun back onto the head. Uncorrelated noise
     * sums in power, not amplitude, so sin/cos weighting keeps the loop seam
     * from dipping in level the way a linear fade would.
     */
    static double[] seamless(double[] raw, int len, int xf) {
        double[] out = new double[len];
        System.arraycopy(raw, 0, out, 0, len);
        for (int i = 0; i < xf; i++) {
            double f = (double) i / xf;
            double a = Math.sin(f * Math.PI / 2);
            double b = Math.cos(f * Math.PI / 2);
            out[i] = raw[i] * a + raw[len + i] * b;
        }
        return out;
    }

    static void normalize(double[] x, double peak) {
        double max = 0;
        for (double v : x) max = Math.max(max, Math.abs(v));
        if (max < 1e-9) return;
        double g = peak / max;
        for (int i = 0; i < x.length; i++) x[i] *= g;
    }

    static void writeWav(double[] x, File f) throws Exception {
        ByteBuffer bb = ByteBuffer.allocate(x.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (double v : x) {
            int s = (int) Math.round(Math.max(-1, Math.min(1, v)) * 32767);
            bb.putShort((short) s);
        }
        AudioFormat fmt = new AudioFormat(SR, 16, 1, true, false);
        try (AudioInputStream in = new AudioInputStream(
                new ByteArrayInputStream(bb.array()), fmt, x.length)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, f);
        }
    }

    // ── Building blocks ──────────────────────────────────────────────────────

    /** One-pole low-pass. Coefficient from cutoff in Hz. */
    static double lp(double x, double prev, double hz) {
        double a = 1 - Math.exp(-2 * Math.PI * hz / SR);
        return prev + a * (x - prev);
    }

    /** Poisson event trigger: true on average `perSec` times per second. */
    static boolean fires(Random r, double perSec) {
        return r.nextDouble() < perSec / SR;
    }

    static double gauss(Random r) { return r.nextGaussian() * 0.3; }

    // ─────────────────────────────────────────────────────────────────────────
    // Voices
    // ─────────────────────────────────────────────────────────────────────────

    /** Hiss bed plus stochastic droplet patter, with slow gusting. */
    static double rain(int i, Ctx c) {
        State s = c.s;
        double n = gauss(c.r);
        // Bright bed: white minus its own low end leaves the hiss.
        s.lp1 = lp(n, s.lp1, 500);
        double bed = (n - s.lp1) * 0.55;
        s.lp2 = lp(n, s.lp2, 120);
        bed += s.lp2 * 0.5;                       // a little body underneath

        // Droplets: short band-limited ticks at random pitches.
        if (fires(c.r, 260)) {
            s.mem[0] = 1.0;                        // droplet envelope
            s.mem[1] = 900 + c.r.nextDouble() * 3200;  // droplet pitch
            s.mem[2] = 0.5 + c.r.nextDouble() * 0.5;   // droplet gain
        }
        double drop = 0;
        if (s.mem[0] > 1e-4) {
            drop = Math.sin(s.phase) * s.mem[0] * s.mem[2] * 0.28;
            s.phase += 2 * Math.PI * s.mem[1] / SR;
            s.mem[0] *= Math.exp(-1.0 / (SR * 0.010));  // ~10 ms decay
        }

        double gust = 0.85 + 0.15 * Math.sin(2 * Math.PI * i / (SR * 11.0));
        return (bed + drop) * gust;
    }

    /** Broadband air with a faint motor hum — the classic box-fan sound. */
    static double fan(int i, Ctx c) {
        State s = c.s;
        double n = gauss(c.r);
        s.lp1 = lp(n, s.lp1, 900);
        s.lp2 = lp(s.lp1, s.lp2, 900);            // 2-pole: rolls off the fizz
        double air = s.lp2 * 1.9;

        // Mains hum and its octave, well under the air noise.
        double hum = Math.sin(2 * Math.PI * 60.0 * i / SR) * 0.05
                   + Math.sin(2 * Math.PI * 120.0 * i / SR) * 0.025;

        double wobble = 1 + 0.04 * Math.sin(2 * Math.PI * i / (SR * 3.3));
        return (air + hum) * wobble;
    }

    /** Low ember bed with sharp, sparse crackles and the occasional pop. */
    static double fireplace(int i, Ctx c) {
        State s = c.s;
        double n = gauss(c.r);
        s.lp1 = lp(n, s.lp1, 200);
        s.lp2 = lp(s.lp1, s.lp2, 200);
        double bed = s.lp2 * 2.2;

        if (fires(c.r, 34)) {                      // crackle
            s.mem[0] = 0.35 + c.r.nextDouble() * 0.45;
            s.mem[1] = 0.0015 + c.r.nextDouble() * 0.004;   // very short
        }
        if (fires(c.r, 1.1)) {                     // bigger pop
            s.mem[0] = 0.9 + c.r.nextDouble() * 0.5;
            s.mem[1] = 0.010 + c.r.nextDouble() * 0.020;
        }
        double crackle = 0;
        if (s.mem[0] > 1e-4) {
            s.hp = lp(gauss(c.r), s.hp, 3000);
            crackle = (gauss(c.r) - s.hp) * s.mem[0] * 0.9;
            s.mem[0] *= Math.exp(-1.0 / (SR * Math.max(s.mem[1], 1e-4)));
        }

        double breathe = 0.9 + 0.1 * Math.sin(2 * Math.PI * i / (SR * 7.0));
        return (bed + crackle) * breathe;
    }

    /** Resonant band of noise whose centre frequency drifts — gusts and whistle. */
    static double wind(int i, Ctx c) {
        State s = c.s;
        double n = gauss(c.r);

        // Slow, incommensurate LFOs so the gusting never lines up obviously.
        double t = (double) i / SR;
        double drift = 0.5 + 0.5 * Math.sin(2 * Math.PI * t / 17.0)
                     * Math.sin(2 * Math.PI * t / 6.5);
        double fc = 180 + drift * 900;

        // Chamberlin state-variable filter, band-pass tap.
        double f = 2 * Math.sin(Math.PI * fc / SR);
        double q = 0.22;
        s.low += f * s.band;
        double high = n - s.low - q * s.band;
        s.band += f * high;
        double bp = s.band * 3.4;

        s.lp1 = lp(n, s.lp1, 150);
        double body = s.lp1 * 0.9;

        double swell = 0.45 + 0.55 * (0.5 + 0.5 * Math.sin(2 * Math.PI * t / 13.0));
        return (bp + body) * swell;
    }

    /** Mostly quiet: a low rumble bed with sparse distant thunder rolls. */
    static double thunder(int i, Ctx c) {
        State s = c.s;
        double n = gauss(c.r);
        s.lp1 = lp(n, s.lp1, 70);
        s.lp2 = lp(s.lp1, s.lp2, 70);
        double bed = s.lp2 * 0.9;                  // constant low room rumble

        // A roll every ~12s on average, with a long ragged decay.
        if (fires(c.r, 1.0 / 12.0)) {
            s.env = 0.8 + c.r.nextDouble() * 0.6;
            s.mem[1] = 2.5 + c.r.nextDouble() * 3.0;     // decay seconds
        }
        double roll = 0;
        if (s.env > 1e-4) {
            s.lp3 = lp(gauss(c.r), s.lp3, 110);
            // Amplitude ripple gives the rumbling, non-uniform tail.
            double ripple = 0.7 + 0.3 * Math.sin(2 * Math.PI * i / (SR * 0.35));
            roll = s.lp3 * s.env * 6.0 * ripple;
            s.env *= Math.exp(-1.0 / (SR * s.mem[1]));
        }
        return bed + roll;
    }

    /** Mid-bright rushing water plus pitched bubbles. */
    static double stream(int i, Ctx c) {
        State s = c.s;
        double n = gauss(c.r);
        s.lp1 = lp(n, s.lp1, 1800);
        s.hp = lp(n, s.hp, 350);
        double rush = (s.lp1 - s.hp) * 2.3;         // band between 350 and 1800

        if (fires(c.r, 90)) {                       // bubble
            s.mem[0] = 0.4 + c.r.nextDouble() * 0.6;
            s.mem[1] = 400 + c.r.nextDouble() * 1400;
            s.mem[2] = 1200 + c.r.nextDouble() * 2600;   // upward chirp target
        }
        double bubble = 0;
        if (s.mem[0] > 1e-4) {
            s.mem[1] += (s.mem[2] - s.mem[1]) * 0.0006;  // bubbles glide up
            bubble = Math.sin(s.phase) * s.mem[0] * 0.20;
            s.phase += 2 * Math.PI * s.mem[1] / SR;
            s.mem[0] *= Math.exp(-1.0 / (SR * 0.035));
        }

        double vary = 0.92 + 0.08 * Math.sin(2 * Math.PI * i / (SR * 9.0));
        return (rush + bubble) * vary;
    }

    /** Swells of filtered noise, with crest hiss riding on top. */
    static double ocean(int i, Ctx c) {
        State s = c.s;
        double n = gauss(c.r);
        double t = (double) i / SR;

        // Several incommensurate swell periods so no single rhythm dominates.
        double swell = 0.5 + 0.5 * Math.sin(2 * Math.PI * t / 9.0);
        swell = swell * (0.6 + 0.4 * (0.5 + 0.5 * Math.sin(2 * Math.PI * t / 21.0)));
        swell = swell * (0.7 + 0.3 * (0.5 + 0.5 * Math.sin(2 * Math.PI * t / 37.0)));
        double shaped = Math.pow(swell, 1.6);       // sharper crest, longer trough

        s.lp1 = lp(n, s.lp1, 300);
        s.lp2 = lp(s.lp1, s.lp2, 300);
        double body = s.lp2 * 2.4;

        s.lp3 = lp(n, s.lp3, 1200);
        double crest = (n - s.lp3) * 0.5 * Math.pow(shaped, 2.2);  // hiss at the break

        return body * (0.35 + 0.65 * shaped) + crest;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Artwork
    // ─────────────────────────────────────────────────────────────────────────

    enum Texture { GRAIN, STREAKS_H, STREAKS_V, EMBER }

    static final int W = 1280, H = 720;

    static void art(Path dir, String name, long seed, int glowRgb, double intensity,
                    Texture tex) throws Exception {
        Random r = new Random(seed);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);

        double gr = ((glowRgb >> 16) & 0xFF) / 255.0;
        double gg = ((glowRgb >> 8) & 0xFF) / 255.0;
        double gb = (glowRgb & 0xFF) / 255.0;

        // Glow sits above centre so the tile's bottom caption gradient stays dark.
        double cx = W * 0.5, cy = H * 0.42;
        double maxR = Math.hypot(W * 0.5, H * 0.5);

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double d = Math.hypot(x - cx, y - cy) / maxR;
                double halo = Math.exp(-Math.pow(d * 2.15, 2)) * intensity;

                double band = switch (tex) {
                    // Horizontal water lines, denser toward the bottom.
                    case STREAKS_H -> 0.06 * Math.sin(y * 0.09 + Math.sin(x * 0.004) * 2.0)
                            * (0.3 + 0.7 * (y / (double) H));
                    // Falling streaks, faint and slightly slanted.
                    case STREAKS_V -> 0.05 * Math.sin((x + y * 0.35) * 0.55)
                            * (0.4 + 0.6 * (y / (double) H));
                    // Ember flicker concentrated low and central.
                    case EMBER -> 0.10 * Math.exp(-Math.pow((y - H * 0.72) / (H * 0.22), 2))
                            * Math.sin(x * 0.02 + y * 0.01);
                    case GRAIN -> 0.0;
                };

                double vign = 1 - 0.55 * Math.pow(d, 1.7);
                double grain = (r.nextDouble() - 0.5) * 0.018;   // hides JPEG banding

                // Void (#150F24) base, warmed toward the glow colour.
                double rr = (0x15 / 255.0 + (gr * (halo + band)) + grain) * vign;
                double gg2 = (0x0F / 255.0 + (gg * (halo + band)) + grain) * vign;
                double bb = (0x24 / 255.0 + (gb * (halo + band)) + grain) * vign;

                img.setRGB(x, y, (clamp8(rr) << 16) | (clamp8(gg2) << 8) | clamp8(bb));
            }
        }
        writeJpeg(img, dir.resolve(name + ".jpg").toFile(), 0.86f);
        System.out.printf("  %-12s art%n", name);
    }

    static int clamp8(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    static void writeJpeg(BufferedImage img, File f, float quality) throws Exception {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(quality);
        try (ImageOutputStream os = ImageIO.createImageOutputStream(f)) {
            w.setOutput(os);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
    }
}
