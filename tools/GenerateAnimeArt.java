import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Anime-inspired chibi tile art for Drift, hand-authored in Java2D (no SVG/
 * image-gen dependency — same rendering pipeline as GenerateAssets.java's
 * procedural art, just drawing figures instead of glow gradients).
 *
 * Style: flat color, simple cel-shading via one darker overlay shape per
 * form, chibi proportions (big head, small body), night palette matching
 * Drift's house colors. Each scene reuses the same head/dress/arm-curve
 * construction so the ten tiles read as one consistent set.
 *
 *   java tools/GenerateAnimeArt.java [outDir]   (default: app/src/main/assets/images)
 */
public final class GenerateAnimeArt {

    static final int W = 1280, H = 720;

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args.length > 0 ? args[0] : "app/src/main/assets/images");
        Files.createDirectories(dir);

        scene(dir, "ocean_waves", GenerateAnimeArt::oceanScene);
        scene(dir, "rain", GenerateAnimeArt::rainScene);
        scene(dir, "stream", GenerateAnimeArt::streamScene);
        scene(dir, "wind", GenerateAnimeArt::windScene);
        scene(dir, "thunder", GenerateAnimeArt::thunderScene);
        scene(dir, "fireplace", GenerateAnimeArt::fireplaceScene);
        scene(dir, "fan", GenerateAnimeArt::fanScene);
        scene(dir, "white_noise", GenerateAnimeArt::whiteNoiseScene);
        scene(dir, "pink_noise", GenerateAnimeArt::pinkNoiseScene);
        scene(dir, "brown_noise", GenerateAnimeArt::brownNoiseScene);

        System.out.println("done -> " + dir.toAbsolutePath());
    }

    interface SceneFn { void draw(Graphics2D g, Random r); }

    static void scene(Path dir, String name, SceneFn fn) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        fn.draw(g, new Random(name.hashCode()));
        g.dispose();
        writeJpeg(img, dir.resolve(name + ".jpg").toFile(), 0.90f);
        System.out.println("  " + name);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Shared scene-building blocks
    // ─────────────────────────────────────────────────────────────────────

    static void sky(Graphics2D g, Color top, Color mid, Color bottom) {
        int h1 = (int) (H * 0.5), h2 = H;
        for (int y = 0; y < h2; y++) {
            Color c = y < h1
                ? lerp(top, mid, y / (float) h1)
                : lerp(mid, bottom, (y - h1) / (float) (h2 - h1));
            g.setColor(c);
            g.fillRect(0, y, W, 1);
        }
    }

    static Color lerp(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
            Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }

    static void moon(Graphics2D g, double x, double y, double r, Color glow, Color body, Color shadow) {
        g.setColor(withAlpha(glow, 70));
        g.fill(new Ellipse2D.Double(x - r * 2.2, y - r * 2.2, r * 4.4, r * 4.4));
        g.setColor(body);
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        g.setColor(shadow);
        g.fill(new Ellipse2D.Double(x - r * 0.55, y - r * 1.05, r * 1.9, r * 1.9));
    }

    /** Faint five-pointed sparkle stars scattered in the upper sky. */
    static void stars(Graphics2D g, Random r, int count, Color color, double maxY) {
        g.setColor(color);
        for (int i = 0; i < count; i++) {
            double x = r.nextDouble() * W, y = r.nextDouble() * maxY;
            double s = 1.2 + r.nextDouble() * 1.8;
            g.setColor(withAlpha(color, 60 + r.nextInt(120)));
            g.fill(new Ellipse2D.Double(x - s / 2, y - s / 2, s, s));
        }
    }

    /** Rolling silhouette hills across the bottom of the frame. */
    static void ground(Graphics2D g, double baseY, Color color) {
        GeneralPath p = new GeneralPath();
        p.moveTo(0, baseY + 20);
        p.curveTo(W * 0.18, baseY - 24, W * 0.32, baseY + 18, W * 0.5, baseY - 4);
        p.curveTo(W * 0.68, baseY - 26, W * 0.82, baseY + 12, W, baseY - 8);
        p.lineTo(W, H);
        p.lineTo(0, H);
        p.closePath();
        g.setColor(color);
        g.fill(p);
    }

    static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    // ── Chibi figure ─────────────────────────────────────────────────────

    static final class Palette {
        final Color skin, blush, hair, hairShade, outfit, outfitShade, sleeve;
        Palette(Color skin, Color blush, Color hair, Color hairShade, Color outfit, Color outfitShade, Color sleeve) {
            this.skin = skin; this.blush = blush; this.hair = hair; this.hairShade = hairShade;
            this.outfit = outfit; this.outfitShade = outfitShade; this.sleeve = sleeve;
        }
    }

    static Palette moonlitGirl(Color hair, Color outfit) {
        return new Palette(
            new Color(0xF5DECB), new Color(0xE0A8A0),
            hair, hairShade(hair),
            outfit, outfitShade(outfit),
            // Sleeves need to read as a separate shape from the dress body —
            // same hue, lifted toward white — or an arm holding a prop just
            // disappears into the torso fill and leaves a hand floating in air.
            lerp(outfit, Color.WHITE, 0.3f)
        );
    }

    static Color hairShade(Color c) { return c.darker(); }
    static Color outfitShade(Color c) { return c.darker(); }

    /** Head, hair, and a simple sleepy/content face. cx,cy = head center. */
    static void head(Graphics2D g, double cx, double cy, double r, Palette p, boolean longHair) {
        // Hair mass behind the head.
        g.setColor(p.hair);
        GeneralPath back = new GeneralPath();
        if (longHair) {
            // Shoulder-length drape with a rounded hem, not a pointed one —
            // a V-taper reads as a beard once it overlaps a seated figure's
            // dress collar, and it used to reach the hem and swallow the body.
            back.moveTo(cx - r * 1.05, cy - r * 0.2);
            back.curveTo(cx - r * 1.18, cy + r * 0.55, cx - r * 0.62, cy + r * 1.2, cx - r * 0.4, cy + r * 1.28);
            back.quadTo(cx, cy + r * 1.4, cx + r * 0.4, cy + r * 1.28);
            back.curveTo(cx + r * 0.62, cy + r * 1.2, cx + r * 1.18, cy + r * 0.55, cx + r * 1.05, cy - r * 0.2);
            back.curveTo(cx + r * 0.9, cy - r * 1.05, cx - r * 0.9, cy - r * 1.05, cx - r * 1.05, cy - r * 0.2);
            back.closePath();
        } else {
            back.append(new Ellipse2D.Double(cx - r * 1.08, cy - r * 1.05, r * 2.16, r * 2.05), false);
        }
        g.fill(back);

        // Face.
        g.setColor(p.skin);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

        // Bangs.
        g.setColor(p.hair);
        GeneralPath bangs = new GeneralPath();
        bangs.moveTo(cx - r * 0.98, cy - r * 0.32);
        bangs.curveTo(cx - r * 0.92, cy - r * 1.02, cx - r * 0.2, cy - r * 1.1, cx, cy - r * 1.08);
        bangs.curveTo(cx + r * 0.2, cy - r * 1.1, cx + r * 0.92, cy - r * 1.02, cx + r * 0.98, cy - r * 0.32);
        bangs.curveTo(cx + r * 0.7, cy - r * 0.62, cx + r * 0.3, cy - r * 0.5, cx, cy - r * 0.58);
        bangs.curveTo(cx - r * 0.3, cy - r * 0.5, cx - r * 0.7, cy - r * 0.62, cx - r * 0.98, cy - r * 0.32);
        bangs.closePath();
        g.fill(bangs);
        g.setColor(p.hairShade);
        g.fill(new Ellipse2D.Double(cx - r * 0.15, cy - r * 1.08, r * 0.3, r * 0.5));

        // Side hair strands.
        g.setColor(p.hair);
        g.fill(new Ellipse2D.Double(cx - r * 1.12, cy - r * 0.3, r * 0.3, r * 0.9));
        g.fill(new Ellipse2D.Double(cx + r * 0.82, cy - r * 0.3, r * 0.3, r * 0.9));

        // Eyes: closed, content little curves (a sleep app's chibi should look at peace).
        g.setColor(new Color(0x2A2038));
        g.setStroke(new BasicStroke((float) (r * 0.08), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(arcCurve(cx - r * 0.34, cy + r * 0.06, r * 0.22));
        g.draw(arcCurve(cx + r * 0.34, cy + r * 0.06, r * 0.22));

        // Blush.
        g.setColor(withAlpha(p.blush, 130));
        g.fill(new Ellipse2D.Double(cx - r * 0.62, cy + r * 0.18, r * 0.34, r * 0.2));
        g.fill(new Ellipse2D.Double(cx + r * 0.28, cy + r * 0.18, r * 0.34, r * 0.2));

        // Small content smile.
        g.setColor(new Color(0x2A2038));
        g.setStroke(new BasicStroke((float) (r * 0.06), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        GeneralPath mouth = new GeneralPath();
        mouth.moveTo(cx - r * 0.1, cy + r * 0.42);
        mouth.quadTo(cx, cy + r * 0.5, cx + r * 0.1, cy + r * 0.42);
        g.draw(mouth);
    }

    static QuadCurve2D arcCurve(double cx, double cy, double r) {
        return new QuadCurve2D.Double(cx - r, cy, cx, cy + r * 0.7, cx + r, cy);
    }

    /** A simple hoodie/dress torso, hem-shaded, from shoulders down to hem. */
    static void dress(Graphics2D g, double cx, double topY, double shoulderW, double hemW, double hemY, Palette p) {
        GeneralPath d = new GeneralPath();
        d.moveTo(cx - shoulderW / 2, topY);
        d.curveTo(cx - hemW / 2, topY + (hemY - topY) * 0.4, cx - hemW / 2, hemY - 10, cx - hemW / 2, hemY);
        d.quadTo(cx, hemY + 14, cx + hemW / 2, hemY);
        d.curveTo(cx + hemW / 2, hemY - 10, cx + hemW / 2, topY + (hemY - topY) * 0.4, cx + shoulderW / 2, topY);
        d.quadTo(cx, topY - shoulderW * 0.12, cx - shoulderW / 2, topY);
        d.closePath();
        g.setColor(p.outfit);
        g.fill(d);
        g.setColor(withAlpha(p.outfitShade, 140));
        Path2D hem = new GeneralPath();
        hem.moveTo(cx - hemW / 2, hemY - 16);
        hem.quadTo(cx, hemY + 6, cx + hemW / 2, hemY - 16);
        hem.quadTo(cx, hemY + 14, cx - hemW / 2, hemY - 16);
        hem.closePath();
        g.fill(hem);
    }

    /** A sleeve as a thick rounded stroke curve, with a small hand circle at the end. */
    static void arm(Graphics2D g, double x1, double y1, double cx2, double cy2, double x3, double y3,
                    double width, Palette p, boolean drawHand) {
        g.setColor(p.sleeve);
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(x1, y1, cx2, cy2, x3, y3));
        if (drawHand) {
            g.setColor(p.skin);
            double hr = width * 0.42;
            g.fill(new Ellipse2D.Double(x3 - hr, y3 - hr, hr * 2, hr * 2));
        }
    }

    static void writeJpeg(BufferedImage img, File f, float quality) throws Exception {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam prm = w.getDefaultWriteParam();
        prm.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        prm.setCompressionQuality(quality);
        try (ImageOutputStream os = ImageIO.createImageOutputStream(f)) {
            w.setOutput(os);
            w.write(null, new IIOImage(img, null, null), prm);
        } finally {
            w.dispose();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Scenes
    // ─────────────────────────────────────────────────────────────────────

    /** Standing at the shoreline, moon path on the water, gulls overhead. */
    static void oceanScene(Graphics2D g, Random r) {
        sky(g, hex(0x0a1526), hex(0x123048), hex(0x1c4a5c));
        moon(g, W * 0.78, H * 0.2, 44, hex(0x8fd6d6), hex(0xf5f3ff), hex(0x123048));
        stars(g, r, 35, hex(0xf5f3ff), H * 0.32);

        // Sea as a horizon band only, with a big sand foreground below it —
        // filling water all the way to the character's feet made her read as
        // chest-deep in open water instead of standing on the beach.
        double horizon = H * 0.4, sandLine = H * 0.6;
        g.setColor(hex(0x0d2436));
        g.fillRect(0, (int) horizon, W, (int) (sandLine - horizon + 40));
        // Moon path shimmer down the middle of the water.
        g.setColor(withAlpha(hex(0xcfeaea), 55));
        for (int i = 0; i < 8; i++) {
            double y = horizon + 8 + i * ((sandLine - horizon) / 8);
            double wobble = Math.sin(i * 1.3) * 16;
            g.fill(new Ellipse2D.Double(W * 0.78 - 34 + wobble, y, 68 - i * 2.2, 4));
        }
        // Wave-line texture across the water band, behind the figure.
        g.setColor(withAlpha(hex(0xbfe3e0), 70));
        g.setStroke(new BasicStroke(2.4f));
        for (int i = 0; i < 5; i++) {
            double wy = horizon + 14 + i * ((sandLine - horizon - 14) / 5);
            GeneralPath wave = new GeneralPath();
            wave.moveTo(0, wy);
            for (int x = 0; x <= W; x += 70) wave.quadTo(x + 35, wy + (i % 2 == 0 ? -8 : 8), x + 70, wy);
            g.draw(wave);
        }
        // Sand foreground she's actually standing on.
        ground(g, sandLine, hex(0x2a2115));
        // Gulls.
        g.setColor(withAlpha(hex(0xf5f3ff), 150));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[] pos : new double[][]{{W * 0.25, H * 0.18}, {W * 0.32, H * 0.14}, {W * 0.2, H * 0.24}}) {
            GeneralPath gull = new GeneralPath();
            gull.moveTo(pos[0] - 12, pos[1]);
            gull.quadTo(pos[0] - 4, pos[1] - 8, pos[0], pos[1]);
            gull.quadTo(pos[0] + 4, pos[1] - 8, pos[0] + 12, pos[1]);
            g.draw(gull);
        }

        Palette p = moonlitGirl(hex(0x2c2440), hex(0x2f6f6f));
        double cx = W * 0.42, feet = H * 0.84;
        arm(g, cx - 60, feet - 210, cx - 90, feet - 150, cx - 70, feet - 90, 26, p, true);
        arm(g, cx + 60, feet - 210, cx + 30, feet - 130, cx + 20, feet - 60, 26, p, false);
        dress(g, cx, feet - 230, 150, 190, feet - 30, p);
        head(g, cx, feet - 260, 62, p, true);
    }

    /** The approved sample: umbrella in the rain. */
    static void rainScene(Graphics2D g, Random r) {
        sky(g, hex(0x0d0a1a), hex(0x1c1638), hex(0x2a2154));
        moon(g, W * 0.84, H * 0.14, 34, hex(0xa79bd9), hex(0xf5f3ff), hex(0x1c1638));
        rainStreaks(g, r, 140, hex(0x8f84c8), 0.35);
        ground(g, H * 0.86, hex(0x171029));
        puddleGlow(g, W * 0.5, H * 0.94, 170, hex(0x4c3f8a));

        Palette p = moonlitGirl(hex(0x2a2154), hex(0x8b5cf6));
        double cx = W * 0.5, feet = H * 0.86;
        // Umbrella — sized well past head width so it reads as a canopy held
        // overhead, not a hair ornament; the pole grip sits partway down so
        // the raised arm looks like it's holding a handle, not a hair pin.
        double canopyY = feet - 470, canopyHalf = 210;
        g.setColor(hex(0x3a3260));
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(cx, canopyY + 18, cx, feet - 20));
        g.setColor(hex(0x8b5cf6));
        GeneralPath canopy = new GeneralPath();
        canopy.moveTo(cx - canopyHalf, canopyY + 60);
        canopy.quadTo(cx - canopyHalf * 0.5, canopyY - 16, cx, canopyY);
        canopy.quadTo(cx + canopyHalf * 0.5, canopyY - 16, cx + canopyHalf, canopyY + 60);
        canopy.quadTo(cx + canopyHalf * 0.6, canopyY + 40, cx + canopyHalf * 0.35, canopyY + 58);
        canopy.quadTo(cx + canopyHalf * 0.15, canopyY + 36, cx, canopyY + 56);
        canopy.quadTo(cx - canopyHalf * 0.15, canopyY + 36, cx - canopyHalf * 0.35, canopyY + 58);
        canopy.quadTo(cx - canopyHalf * 0.6, canopyY + 40, cx - canopyHalf, canopyY + 60);
        canopy.closePath();
        g.fill(canopy);
        g.setColor(withAlpha(hex(0xa78bfa), 170));
        GeneralPath rim = new GeneralPath();
        rim.moveTo(cx - canopyHalf, canopyY + 60);
        rim.quadTo(cx, canopyY - 40, cx + canopyHalf, canopyY + 60);
        rim.quadTo(cx, canopyY + 10, cx - canopyHalf, canopyY + 60);
        rim.closePath();
        g.fill(rim);

        arm(g, cx - 60, feet - 200, cx - 40, feet - 300, cx - 4, feet - 360, 26, p, true);
        arm(g, cx + 60, feet - 200, cx + 90, feet - 220, cx + 78, feet - 250, 24, p, false);
        dress(g, cx, feet - 220, 150, 190, feet - 30, p);
        head(g, cx, feet - 250, 62, p, false);
    }

    /** Sitting on a rock at the water's edge, feet in the stream. */
    static void streamScene(Graphics2D g, Random r) {
        sky(g, hex(0x0a1a16), hex(0x123024), hex(0x1c4a34));
        moon(g, W * 0.2, H * 0.18, 40, hex(0x9fd8b0), hex(0xf5f3ff), hex(0x123024));
        stars(g, r, 30, hex(0xf5f3ff), H * 0.35);
        ground(g, H * 0.7, hex(0x0f2b20));
        // Water band.
        g.setColor(hex(0x163d2c));
        g.fillRect(0, (int) (H * 0.78), W, (int) (H * 0.22));
        g.setColor(withAlpha(hex(0x8fd8b8), 70));
        for (int i = 0; i < 14; i++) {
            double y = H * 0.8 + r.nextDouble() * H * 0.16;
            g.fill(new Ellipse2D.Double(r.nextDouble() * W, y, 40 + r.nextDouble() * 60, 3));
        }
        // Reeds.
        g.setColor(hex(0x1e5038));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double x : new double[]{W * 0.08, W * 0.12, W * 0.86, W * 0.9}) {
            GeneralPath reed = new GeneralPath();
            reed.moveTo(x, H * 0.86);
            reed.quadTo(x + 14, H * 0.7, x - 6, H * 0.58);
            g.draw(reed);
            g.fill(new Ellipse2D.Double(x - 12, H * 0.55, 14, 26));
        }
        // Rock — needs real contrast against the water fill or she reads as
        // floating with nothing under her.
        g.setColor(hex(0x3a5548));
        g.fill(new Ellipse2D.Double(W * 0.42, H * 0.72, 220, 70));
        g.setColor(withAlpha(hex(0x527060), 160));
        g.fill(new Ellipse2D.Double(W * 0.44, H * 0.72, 180, 30));

        Palette p = moonlitGirl(hex(0x30241c), hex(0x3e8c8c));
        double cx = W * 0.5, hipY = H * 0.72;
        // Legs draped toward the water.
        g.setColor(p.skin);
        g.setStroke(new BasicStroke(22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(cx - 20, hipY, cx + 10, hipY + 70, cx + 46, hipY + 118));
        g.draw(new QuadCurve2D.Double(cx + 6, hipY, cx + 34, hipY + 74, cx + 66, hipY + 116));
        dress(g, cx, hipY - 150, 148, 172, hipY + 10, p);
        arm(g, cx - 58, hipY - 130, cx - 90, hipY - 90, cx - 78, hipY - 40, 24, p, true);
        arm(g, cx + 58, hipY - 130, cx + 40, hipY - 60, cx + 24, hipY - 10, 24, p, true);
        head(g, cx, hipY - 172, 60, p, true);
        // Ripples where her feet meet the water.
        g.setColor(withAlpha(hex(0xbdeedd), 110));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(cx + 20, hipY + 128, 70, 14));
        g.draw(new Ellipse2D.Double(cx + 4, hipY + 134, 100, 16));
    }

    /** Standing in the wind, scarf and leaves streaming sideways. */
    static void windScene(Graphics2D g, Random r) {
        sky(g, hex(0x0d1620), hex(0x1a2a38), hex(0x24404e));
        moon(g, W * 0.18, H * 0.2, 38, hex(0x9cc7d6), hex(0xf5f3ff), hex(0x1a2a38));
        // Wind-lines.
        g.setColor(withAlpha(hex(0xbcd8e2), 90));
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 9; i++) {
            double y = 60 + r.nextDouble() * H * 0.55;
            double len = 90 + r.nextDouble() * 140;
            double x0 = r.nextDouble() * W * 0.7;
            GeneralPath line = new GeneralPath();
            line.moveTo(x0, y);
            line.quadTo(x0 + len * 0.5, y - 14, x0 + len, y);
            g.draw(line);
        }
        ground(g, H * 0.82, hex(0x152530));
        // Drifting leaves.
        g.setColor(hex(0xb5763a));
        for (int i = 0; i < 7; i++) {
            double x = r.nextDouble() * W, y = H * 0.3 + r.nextDouble() * H * 0.5;
            java.awt.geom.AffineTransform old = g.getTransform();
            g.translate(x, y);
            g.rotate(r.nextDouble() * Math.PI);
            g.fill(new Ellipse2D.Double(-7, -4, 14, 8));
            g.setTransform(old);
        }

        Palette p = moonlitGirl(hex(0x342a1e), hex(0x6e93a0));
        double cx = W * 0.52, feet = H * 0.82;
        // Scarf streaming to one side.
        g.setColor(hex(0xcfe6ee));
        GeneralPath scarf = new GeneralPath();
        scarf.moveTo(cx + 20, feet - 210);
        scarf.curveTo(cx + 90, feet - 190, cx + 170, feet - 160, cx + 260, feet - 190);
        scarf.curveTo(cx + 170, feet - 140, cx + 90, feet - 150, cx + 30, feet - 170);
        scarf.closePath();
        g.fill(scarf);
        g.setColor(withAlpha(hex(0xa9c8d4), 160));
        g.fill(new Ellipse2D.Double(cx + 230, feet - 205, 40, 22));

        dress(g, cx, feet - 220, 148, 182, feet - 20, p);
        arm(g, cx - 58, feet - 195, cx - 96, feet - 150, cx - 90, feet - 90, 24, p, true);
        arm(g, cx + 40, feet - 200, cx + 30, feet - 220, cx + 18, feet - 220, 22, p, false);
        head(g, cx, feet - 250, 60, p, false);
    }

    /** Wrapped in a blanket by a window, distant lightning. */
    static void thunderScene(Graphics2D g, Random r) {
        sky(g, hex(0x0a0a1c), hex(0x171235), hex(0x241a4a));
        // Distant storm cloud with a lightning bolt.
        g.setColor(withAlpha(hex(0x352a5c), 200));
        g.fill(new Ellipse2D.Double(W * 0.68, H * 0.1, 220, 70));
        g.fill(new Ellipse2D.Double(W * 0.6, H * 0.14, 160, 60));
        g.fill(new Ellipse2D.Double(W * 0.78, H * 0.13, 150, 60));
        g.setColor(hex(0xf5f3ff));
        GeneralPath bolt = new GeneralPath();
        bolt.moveTo(W * 0.74, H * 0.2);
        bolt.lineTo(W * 0.71, H * 0.3);
        bolt.lineTo(W * 0.745, H * 0.3);
        bolt.lineTo(W * 0.715, H * 0.4);
        bolt.lineTo(W * 0.77, H * 0.27);
        bolt.lineTo(W * 0.745, H * 0.27);
        bolt.closePath();
        g.fill(bolt);
        g.setColor(withAlpha(hex(0xf5f3ff), 40));
        g.fill(new Ellipse2D.Double(W * 0.6, H * 0.08, 260, 200));
        stars(g, r, 25, hex(0xf5f3ff), H * 0.35);
        ground(g, H * 0.86, hex(0x140f2c));

        // Window frame vignette in the near corner.
        g.setColor(withAlpha(hex(0x0a0a1c), 120));
        g.fillRect(0, 0, (int) (W * 0.14), H);
        g.setColor(hex(0x0a0a1c));
        g.setStroke(new BasicStroke(10f));
        g.drawLine((int) (W * 0.13), 0, (int) (W * 0.13), H);

        Palette p = moonlitGirl(hex(0x241a3a), hex(0x6a5bb0));
        double cx = W * 0.54, baseY = H * 0.86;
        // Blanket wrap (wide rounded shape).
        g.setColor(hex(0x4c3f8a));
        g.fill(new Ellipse2D.Double(cx - 130, baseY - 190, 260, 220));
        g.setColor(withAlpha(hex(0x3a3260), 170));
        g.fill(new Ellipse2D.Double(cx - 90, baseY - 40, 200, 60));
        // Knees peeking.
        g.setColor(p.skin);
        g.fill(new Ellipse2D.Double(cx - 48, baseY - 150, 44, 40));
        g.fill(new Ellipse2D.Double(cx + 6, baseY - 150, 44, 40));
        head(g, cx, baseY - 210, 58, p, true);
        // Hands peeking from the blanket, holding it closed.
        g.setColor(p.skin);
        g.fill(new Ellipse2D.Double(cx - 20, baseY - 100, 24, 24));
        g.fill(new Ellipse2D.Double(cx + 4, baseY - 100, 24, 24));
    }

    /** Cross-legged by the fireplace, mug in hand, cat curled beside her. */
    static void fireplaceScene(Graphics2D g, Random r) {
        sky(g, hex(0x120c1e), hex(0x1c1428), hex(0x241a2c));
        stars(g, r, 16, hex(0xf5f3ff), H * 0.25);
        ground(g, H * 0.86, hex(0x150f1c));

        // Fireplace mantel.
        double fx = W * 0.72, fy = H * 0.5, fw = 300, fh = 300;
        g.setColor(hex(0x2a2038));
        g.fill(new Rectangle2D.Double(fx - fw / 2, fy, fw, fh));
        g.setColor(hex(0x1c1628));
        g.fill(new Rectangle2D.Double(fx - fw * 0.34, fy + 30, fw * 0.68, fh - 40));
        // Flames, with a soft layered glow confined to the hearth opening — a
        // single flat-alpha ellipse used to read as a hard-edged brown egg
        // instead of firelight, so this stacks translucent rings for a
        // gradient-like falloff without an actual (unsupported) gradient fill.
        for (int i = 3; i >= 1; i--) {
            g.setColor(withAlpha(hex(0xff8f3c), 26 * i));
            double rw = fw * 0.16 * i, rh = 40.0 * i;
            g.fill(new Ellipse2D.Double(fx - rw / 2, fy + fh - 50 - rh / 2, rw, rh));
        }
        flame(g, fx - 40, fy + fh - 60, 34, hex(0xffb057), r);
        flame(g, fx, fy + fh - 50, 46, hex(0xff9a3c), r);
        flame(g, fx + 42, fy + fh - 58, 32, hex(0xffc46b), r);
        // Embers.
        g.setColor(withAlpha(hex(0xffb057), 150));
        for (int i = 0; i < 10; i++) {
            double x = fx - 90 + r.nextDouble() * 180, y = fy + fh - 90 - r.nextDouble() * 140;
            double s = 2 + r.nextDouble() * 3;
            g.fill(new Ellipse2D.Double(x, y, s, s));
        }

        Palette p = moonlitGirl(hex(0x30201a), hex(0xc2662b));
        double cx = W * 0.34, hipY = H * 0.82;
        // Cross-legged silhouette (wide low hem).
        dress(g, cx, hipY - 150, 150, 230, hipY + 10, p);
        // Cat curled on the ground beside her — a warm mid-tone so it doesn't
        // vanish against the near-black background the way a shadow tone did.
        double catX = cx + 150, catY = hipY - 6;
        Color catColor = hex(0x6b4a3a);
        g.setColor(catColor);
        g.fill(new Ellipse2D.Double(catX, catY - 40, 92, 46));
        g.fill(new Ellipse2D.Double(catX + 66, catY - 62, 26, 28));
        GeneralPath ears = new GeneralPath();
        ears.moveTo(catX + 68, catY - 60); ears.lineTo(catX + 74, catY - 72); ears.lineTo(catX + 80, catY - 58);
        ears.moveTo(catX + 84, catY - 58); ears.lineTo(catX + 90, catY - 72); ears.lineTo(catX + 96, catY - 60);
        g.fill(ears);
        g.setColor(catColor);
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(catX + 90, catY - 10, catX + 130, catY - 30, catX + 110, catY - 60));
        g.setColor(withAlpha(Color.BLACK, 90));
        g.fill(new Ellipse2D.Double(catX + 72, catY - 50, 5, 5));
        g.fill(new Ellipse2D.Double(catX + 84, catY - 50, 5, 5));

        // Mug held up near her raised hand.
        double mugX = cx - 82, mugY = hipY - 210;
        arm(g, cx - 58, hipY - 130, cx - 78, hipY - 180, mugX + 12, mugY + 20, 22, p, false);
        g.setColor(hex(0xead9c8));
        g.fill(new Rectangle2D.Double(mugX, mugY, 34, 30));
        g.setColor(withAlpha(hex(0xffb057), 200));
        g.fill(new Ellipse2D.Double(mugX + 2, mugY - 4, 30, 8));
        arm(g, cx + 58, hipY - 130, cx + 40, hipY - 90, cx + 20, hipY - 50, 22, p, true);
        head(g, cx, hipY - 172, 60, p, true);
    }

    static void flame(Graphics2D g, double cx, double baseY, double size, Color color, Random r) {
        GeneralPath f = new GeneralPath();
        f.moveTo(cx, baseY);
        f.curveTo(cx - size * 0.6, baseY - size * 0.5, cx - size * 0.3, baseY - size * 1.3, cx, baseY - size * 1.8);
        f.curveTo(cx + size * 0.3, baseY - size * 1.3, cx + size * 0.6, baseY - size * 0.5, cx, baseY);
        g.setColor(color);
        g.fill(f);
        g.setColor(withAlpha(Color.WHITE, 130));
        g.fill(new Ellipse2D.Double(cx - size * 0.12, baseY - size * 0.9, size * 0.24, size * 0.5));
    }

    /** Lying on a cushion near a desk fan, ribbon fluttering in the breeze. */
    static void fanScene(Graphics2D g, Random r) {
        sky(g, hex(0x12101f), hex(0x1e1a33), hex(0x262040));
        stars(g, r, 20, hex(0xf5f3ff), H * 0.3);
        ground(g, H * 0.88, hex(0x181430));

        // Desk fan.
        double fx = W * 0.76, fy = H * 0.5;
        g.setColor(hex(0x6e6a99));
        g.fill(new Rectangle2D.Double(fx - 8, fy + 70, 16, 90));
        g.fill(new Ellipse2D.Double(fx - 90, fy + 150, 180, 26));
        g.setColor(hex(0x3a3260));
        g.fill(new Ellipse2D.Double(fx - 95, fy - 95, 190, 190));
        g.setColor(hex(0x1e1a33));
        g.fill(new Ellipse2D.Double(fx - 78, fy - 78, 156, 156));
        g.setColor(hex(0x8b8ac0));
        for (int i = 0; i < 3; i++) {
            double ang = i * 120 * Math.PI / 180;
            java.awt.geom.AffineTransform old = g.getTransform();
            g.translate(fx, fy);
            g.rotate(ang);
            g.fill(new Ellipse2D.Double(-10, -68, 56, 34));
            g.setTransform(old);
        }
        g.setColor(hex(0x2a2440));
        g.fill(new Ellipse2D.Double(fx - 16, fy - 16, 32, 32));
        // Motion blur lines from the fan.
        g.setColor(withAlpha(hex(0xbcb8e0), 60));
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 5; i++) {
            double y = fy - 40 + i * 20;
            g.draw(new java.awt.geom.Line2D.Double(fx - 130 - i * 20, y, fx - 100, y));
        }

        Palette p = moonlitGirl(hex(0x241f38), hex(0x6e7a99));
        double cx = W * 0.32, midY = H * 0.72;
        // Cushion.
        g.setColor(hex(0x2a2440));
        g.fill(new Ellipse2D.Double(cx - 160, midY + 40, 340, 70));
        // Reclined body (horizontal hem).
        GeneralPath body = new GeneralPath();
        body.moveTo(cx - 120, midY);
        body.curveTo(cx - 40, midY - 60, cx + 60, midY - 60, cx + 130, midY - 10);
        body.curveTo(cx + 100, midY + 40, cx - 60, midY + 46, cx - 120, midY);
        body.closePath();
        g.setColor(p.outfit);
        g.fill(body);
        g.setColor(withAlpha(p.outfitShade, 140));
        g.fill(new Ellipse2D.Double(cx - 30, midY, 140, 30));
        // Ribbon fluttering.
        g.setColor(hex(0xd8d4f0));
        GeneralPath ribbon = new GeneralPath();
        ribbon.moveTo(cx + 118, midY - 20);
        ribbon.curveTo(cx + 180, midY - 40, cx + 220, midY - 4, cx + 270, midY - 30);
        ribbon.curveTo(cx + 220, midY + 6, cx + 180, midY - 6, cx + 130, midY);
        ribbon.closePath();
        g.fill(ribbon);
        head(g, cx - 128, midY - 24, 56, p, true);
        arm(g, cx - 90, midY - 30, cx - 60, midY + 10, cx - 20, midY + 6, 20, p, true);
    }

    /** Headphones on, TV static hiss behind — cool neutral palette. */
    static void whiteNoiseScene(Graphics2D g, Random r) {
        sky(g, hex(0x101018), hex(0x1c1c28), hex(0x24242e));
        staticDots(g, r, hex(0xc8c8d8), 260);
        ground(g, H * 0.84, hex(0x16161e));

        // Old TV silhouette with antenna, softly lit.
        double tx = W * 0.74, ty = H * 0.42;
        g.setColor(hex(0x24242e));
        g.fill(new java.awt.geom.RoundRectangle2D.Double(tx - 110, ty - 70, 220, 170, 20, 20));
        g.setColor(withAlpha(hex(0xdcdce8), 60));
        g.fill(new Ellipse2D.Double(tx - 80, ty - 40, 160, 110));
        g.setColor(hex(0x1c1c28));
        g.setStroke(new BasicStroke(4f));
        g.draw(new java.awt.geom.Line2D.Double(tx - 30, ty - 70, tx - 60, ty - 130));
        g.draw(new java.awt.geom.Line2D.Double(tx + 30, ty - 70, tx + 60, ty - 130));

        Palette p = moonlitGirl(hex(0x28283a), hex(0x6e7a99));
        double cx = W * 0.36, hipY = H * 0.8;
        dress(g, cx, hipY - 150, 150, 220, hipY + 10, p);
        arm(g, cx - 58, hipY - 130, cx - 90, hipY - 90, cx - 78, hipY - 40, 22, p, true);
        arm(g, cx + 58, hipY - 130, cx + 40, hipY - 90, cx + 20, hipY - 40, 22, p, true);
        head(g, cx, hipY - 172, 60, p, false);
        // Headphones.
        g.setColor(hex(0xdcdce8));
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Arc2D.Double(cx - 60, hipY - 250, 120, 110, 20, 140, java.awt.geom.Arc2D.OPEN));
        g.fill(new Ellipse2D.Double(cx - 66, hipY - 182, 22, 34));
        g.fill(new Ellipse2D.Double(cx + 44, hipY - 182, 22, 34));
    }

    /** Same pose language as white noise, warmed toward magenta/pink with bokeh. */
    static void pinkNoiseScene(Graphics2D g, Random r) {
        sky(g, hex(0x160a1c), hex(0x2a1030), hex(0x3a1440));
        bokeh(g, r, hex(0xd946ef), 26);
        ground(g, H * 0.84, hex(0x1e0c26));

        Palette p = moonlitGirl(hex(0x2e1030), hex(0xa445c4));
        double cx = W * 0.5, hipY = H * 0.8;
        dress(g, cx, hipY - 150, 150, 220, hipY + 10, p);
        arm(g, cx - 58, hipY - 130, cx - 90, hipY - 90, cx - 78, hipY - 40, 22, p, true);
        arm(g, cx + 58, hipY - 130, cx + 40, hipY - 90, cx + 20, hipY - 40, 22, p, true);
        head(g, cx, hipY - 172, 60, p, false);
        g.setColor(hex(0xf0d6f5));
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Arc2D.Double(cx - 60, hipY - 250, 120, 110, 20, 140, java.awt.geom.Arc2D.OPEN));
        g.fill(new Ellipse2D.Double(cx - 66, hipY - 182, 22, 34));
        g.fill(new Ellipse2D.Double(cx + 44, hipY - 182, 22, 34));
    }

    /** Reading a book under a blanket, warm amber dust motes drifting. */
    static void brownNoiseScene(Graphics2D g, Random r) {
        sky(g, hex(0x160f08), hex(0x2a1c10), hex(0x3a2814));
        g.setColor(withAlpha(hex(0xd99a55), 40));
        for (int i = 0; i < 30; i++) {
            double x = r.nextDouble() * W, y = r.nextDouble() * H * 0.7, s = 1 + r.nextDouble() * 2.5;
            g.fill(new Ellipse2D.Double(x, y, s, s));
        }
        ground(g, H * 0.86, hex(0x1c130b));

        Palette p = moonlitGirl(hex(0x2c1c10), hex(0xa5652e));
        double cx = W * 0.5, baseY = H * 0.86;
        g.setColor(hex(0x8a5a2e));
        g.fill(new Ellipse2D.Double(cx - 140, baseY - 200, 280, 230));
        g.setColor(withAlpha(hex(0x6e4522), 160));
        g.fill(new Ellipse2D.Double(cx - 100, baseY - 40, 220, 60));
        head(g, cx, baseY - 224, 58, p, true);
        // Open book.
        g.setColor(hex(0xead9c0));
        GeneralPath book = new GeneralPath();
        book.moveTo(cx - 66, baseY - 128);
        book.quadTo(cx, baseY - 150, cx + 66, baseY - 128);
        book.lineTo(cx + 62, baseY - 96);
        book.quadTo(cx, baseY - 116, cx - 62, baseY - 96);
        book.closePath();
        g.fill(book);
        g.setColor(withAlpha(hex(0x8a5a2e), 120));
        g.draw(new java.awt.geom.Line2D.Double(cx, baseY - 148, cx, baseY - 98));
        // Hands peeking from the blanket, holding the book.
        g.setColor(p.skin);
        g.fill(new Ellipse2D.Double(cx - 82, baseY - 118, 24, 22));
        g.fill(new Ellipse2D.Double(cx + 58, baseY - 118, 24, 22));
    }

    // ── Shared texture helpers ──────────────────────────────────────────

    static void rainStreaks(Graphics2D g, Random r, int count, Color color, double alpha) {
        g.setColor(withAlpha(color, (int) (255 * alpha)));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < count; i++) {
            double x = r.nextDouble() * W, y = r.nextDouble() * H * 0.75;
            double len = 18 + r.nextDouble() * 26;
            g.draw(new java.awt.geom.Line2D.Double(x, y, x - len * 0.3, y + len));
        }
    }

    static void puddleGlow(Graphics2D g, double cx, double cy, double rx, Color color) {
        g.setColor(withAlpha(color, 90));
        g.fill(new Ellipse2D.Double(cx - rx / 2, cy - 10, rx, 22));
    }

    static void staticDots(Graphics2D g, Random r, Color color, int count) {
        for (int i = 0; i < count; i++) {
            double x = r.nextDouble() * W, y = r.nextDouble() * H;
            g.setColor(withAlpha(color, 20 + r.nextInt(90)));
            double s = 1 + r.nextDouble() * 2;
            g.fill(new Rectangle2D.Double(x, y, s, s));
        }
    }

    static void bokeh(Graphics2D g, Random r, Color color, int count) {
        for (int i = 0; i < count; i++) {
            double x = r.nextDouble() * W, y = r.nextDouble() * H;
            double s = 8 + r.nextDouble() * 46;
            g.setColor(withAlpha(color, 12 + r.nextInt(28)));
            g.fill(new Ellipse2D.Double(x, y, s, s));
        }
    }

    static Color hex(int rgb) { return new Color(rgb); }
}
