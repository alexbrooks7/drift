import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;

/**
 * Generates the Android TV launcher banner and the square app icon.
 *
 * The previous banner was near-black (#150F24) with a small dim wordmark, so
 * on a launcher whose background is also black it read as an empty tile —
 * nothing identifiable at the size the launcher actually draws it. This one
 * leads with the chibi character on a violet field that separates from black,
 * which is also what makes the app recognizable next to neighbours like Luna
 * or Downloader.
 *
 * Everything is rendered at 4x and downsampled, because Java2D's antialiasing
 * alone isn't enough to keep curves and small text clean at 320x180.
 *
 * Usage:  java tools/GenerateBanner.java <res-dir>
 */
public class GenerateBanner {

    static final int SS = 4;  // supersample factor

    public static void main(String[] args) throws Exception {
        Path res = Paths.get(args.length > 0 ? args[0] : "app/src/main/res");

        // Banner: 320x180 is the Android TV / Fire TV spec for the launcher tile.
        Path bannerDir = res.resolve("drawable-xhdpi");
        Files.createDirectories(bannerDir);
        BufferedImage banner = render(320, 180, true);
        ImageIO.write(banner, "png", bannerDir.resolve("banner.png").toFile());
        System.out.println("  banner.png        320x180   -> " + bannerDir);

        // Square icon for android:icon. Feeding a 16:9 banner into a square
        // icon slot letterboxes or crops it wherever an icon is drawn.
        Path iconDir = res.resolve("mipmap-xhdpi");
        Files.createDirectories(iconDir);
        BufferedImage icon = render(320, 320, false);
        ImageIO.write(icon, "png", iconDir.resolve("ic_launcher.png").toFile());
        System.out.println("  ic_launcher.png   320x320   -> " + iconDir);
    }

    static BufferedImage render(int w, int h, boolean withWordmark) {
        BufferedImage big = new BufferedImage(w * SS, h * SS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        draw(g, w * SS, h * SS, withWordmark);
        g.dispose();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D go = out.createGraphics();
        go.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        go.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        go.drawImage(big, 0, 0, w, h, null);
        go.dispose();
        return out;
    }

    static void draw(Graphics2D g, int W, int H, boolean withWordmark) {
        // Violet field. Deliberately well clear of black — this is the whole
        // reason the old banner vanished on the launcher.
        g.setPaint(new GradientPaint(0, 0, hex(0x5B3FBF), W, H, hex(0x2A1B4D)));
        g.fillRect(0, 0, W, H);

        // Crescent moon, tucked into the corner. It reads as an accent, not a
        // second subject — an earlier pass had it large with a wide halo and it
        // came out looking like a planet competing with the character.
        double mx = W * 0.88, my = H * 0.17, mr = Math.min(W, H) * 0.085;
        for (int i = 3; i >= 1; i--) {
            g.setColor(withAlpha(hex(0xD9C9FF), 9 * i));
            double rr = mr * (1 + i * 0.34);
            g.fill(new Ellipse2D.Double(mx - rr, my - rr, rr * 2, rr * 2));
        }
        Area crescent = new Area(new Ellipse2D.Double(mx - mr, my - mr, mr * 2, mr * 2));
        crescent.subtract(new Area(new Ellipse2D.Double(
            mx - mr * 1.42, my - mr * 1.18, mr * 2, mr * 2)));
        g.setColor(hex(0xF3ECFF));
        g.fill(crescent);

        // A few stars for texture.
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < 26; i++) {
            double x = rnd.nextDouble() * W, y = rnd.nextDouble() * H * 0.62;
            double s = (0.6 + rnd.nextDouble() * 1.1) * (W / 320.0);
            g.setColor(withAlpha(Color.WHITE, 70 + rnd.nextInt(120)));
            g.fill(new Ellipse2D.Double(x, y, s, s));
        }

        // Figure, centered. The wordmark version sits a little higher to leave
        // room for the text without shrinking him.
        double cx = W * 0.5;
        double feet = withWordmark ? H * 0.755 : H * 0.87;
        double scale = withWordmark ? H * 0.0043 : H * 0.0044;
        seatedFigure(g, cx, feet, scale);

        if (withWordmark) {
            g.setColor(hex(0xF5F3FF));
            g.setFont(new Font("Segoe UI", Font.BOLD, (int) (H * 0.145)));
            String s = "Drift";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (H * 0.945));
        }
    }

    /** Seated chibi: shadow, legs tucked, torso, resting arms, head. */
    static void seatedFigure(Graphics2D g, double cx, double feet, double u) {
        Palette p = palette(hex(0x3A2A20), hex(0x3E8C8C));

        // Contact shadow so he's sitting on something, not floating.
        g.setColor(withAlpha(hex(0x140E24), 110));
        g.fill(new Ellipse2D.Double(cx - 66 * u, feet - 14 * u, 132 * u, 26 * u));

        // Legs tucked forward, drawn before the torso so the hem overlaps them.
        g.setColor(p.skin);
        g.setStroke(new BasicStroke((float) (17 * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(cx - 26 * u, feet - 40 * u, cx - 46 * u, feet - 6 * u, cx - 12 * u, feet - 4 * u));
        g.draw(new QuadCurve2D.Double(cx + 26 * u, feet - 40 * u, cx + 46 * u, feet - 6 * u, cx + 12 * u, feet - 4 * u));

        // Torso.
        dress(g, cx, feet - 86 * u, 62 * u, 104 * u, feet - 18 * u, p);

        // Arms resting in the lap.
        arm(g, cx - 30 * u, feet - 80 * u, cx - 46 * u, feet - 46 * u, cx - 14 * u, feet - 34 * u, 15 * u, p, true);
        arm(g, cx + 30 * u, feet - 80 * u, cx + 46 * u, feet - 46 * u, cx + 14 * u, feet - 34 * u, 15 * u, p, true);

        // Head last, so hair sits over the collar.
        head(g, cx, feet - 122 * u, 40 * u, p);
    }

    // ── Chibi drawing, ported from tools/GenerateAnimeArt.java ───────────

    static final class Palette {
        final Color skin, blush, hair, hairShade, outfit, outfitShade, sleeve;
        Palette(Color skin, Color blush, Color hair, Color hairShade,
                Color outfit, Color outfitShade, Color sleeve) {
            this.skin = skin; this.blush = blush; this.hair = hair; this.hairShade = hairShade;
            this.outfit = outfit; this.outfitShade = outfitShade; this.sleeve = sleeve;
        }
    }

    static Palette palette(Color hair, Color outfit) {
        return new Palette(
            new Color(0xF5DECB), new Color(0xE0A8A0),
            hair, hair.darker(),
            outfit, outfit.darker(),
            lerp(outfit, Color.WHITE, 0.3f)
        );
    }

    static void head(Graphics2D g, double cx, double cy, double r, Palette p) {
        g.setColor(p.hair);
        GeneralPath back = new GeneralPath();
        back.moveTo(cx - r * 1.05, cy - r * 0.2);
        back.curveTo(cx - r * 1.18, cy + r * 0.55, cx - r * 0.62, cy + r * 1.2, cx - r * 0.4, cy + r * 1.28);
        back.quadTo(cx, cy + r * 1.4, cx + r * 0.4, cy + r * 1.28);
        back.curveTo(cx + r * 0.62, cy + r * 1.2, cx + r * 1.18, cy + r * 0.55, cx + r * 1.05, cy - r * 0.2);
        back.curveTo(cx + r * 0.9, cy - r * 1.05, cx - r * 0.9, cy - r * 1.05, cx - r * 1.05, cy - r * 0.2);
        back.closePath();
        g.fill(back);

        g.setColor(p.skin);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

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

        g.setColor(p.hair);
        g.fill(new Ellipse2D.Double(cx - r * 1.12, cy - r * 0.3, r * 0.3, r * 0.9));
        g.fill(new Ellipse2D.Double(cx + r * 0.82, cy - r * 0.3, r * 0.3, r * 0.9));

        g.setColor(new Color(0x2A2038));
        g.setStroke(new BasicStroke((float) (r * 0.09), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(arcCurve(cx - r * 0.34, cy + r * 0.06, r * 0.22));
        g.draw(arcCurve(cx + r * 0.34, cy + r * 0.06, r * 0.22));

        g.setColor(withAlpha(p.blush, 130));
        g.fill(new Ellipse2D.Double(cx - r * 0.62, cy + r * 0.18, r * 0.34, r * 0.2));
        g.fill(new Ellipse2D.Double(cx + r * 0.28, cy + r * 0.18, r * 0.34, r * 0.2));

        g.setColor(new Color(0x2A2038));
        g.setStroke(new BasicStroke((float) (r * 0.07), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        GeneralPath mouth = new GeneralPath();
        mouth.moveTo(cx - r * 0.1, cy + r * 0.42);
        mouth.quadTo(cx, cy + r * 0.5, cx + r * 0.1, cy + r * 0.42);
        g.draw(mouth);
    }

    static QuadCurve2D arcCurve(double cx, double cy, double r) {
        return new QuadCurve2D.Double(cx - r, cy, cx, cy + r * 0.7, cx + r, cy);
    }

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
        GeneralPath hem = new GeneralPath();
        hem.moveTo(cx - hemW / 2, hemY - 16);
        hem.quadTo(cx, hemY + 6, cx + hemW / 2, hemY - 16);
        hem.quadTo(cx, hemY + 14, cx - hemW / 2, hemY - 16);
        hem.closePath();
        g.fill(hem);
    }

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

    static Color hex(int rgb) { return new Color(rgb); }

    static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static Color lerp(Color a, Color b, float t) {
        return new Color(
            Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
            Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }
}
