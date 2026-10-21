import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.util.zip.ZipFile;

/**
 * The store page's images, in Minecraft's font, on the showcase's captures - Portable Beacons'
 * {@code tools/Banners.java}, so the mods' pages share one look.
 *
 * <pre>
 *   ./gradlew runClient -Pshowcase                              (captures into run/store-screenshots)
 *   java tools/Banners.java run/store-screenshots docs/store-art
 * </pre>
 *
 * No game or loader version on any image: the same art serves every port.
 * The font comes from the game resources ModDevGradle already extracted: it is not committed.
 * CurseForge's description editor refuses any image wider than 850 px. JPG, as the pages serve them.
 */
public class Banners {

    static final int W = 850;
    static final Color INK = new Color(14, 16, 20);
    static final Color ACCENT = new Color(95, 227, 210);
    static final Color SUB = new Color(190, 255, 246);

    static BufferedImage font;
    static final int[] widths = new int[256];

    public static void main(String[] args) throws Exception {
        File shots = new File(args[0]), out = new File(args[1]);
        out.mkdirs();
        loadFont();

        banner(shots, out);
        header(shots, out, "header_touch", "01_wall", 0.45, "Touch to link", "Place a drawer against the network: it joins");
        header(shots, out, "header_strict", "04_cut", 0.45, "STRICT mode", "Break the chain, and the far side is cut off");
        header(shots, out, "header_trims", "05_trims", 0.5, "Trims", "One per wood, a framed one - they carry the network");
        header(shots, out, "header_control", "06_dryrun", 0.5, "You stay in control", "Three modes, a dry run, access checks");
        header(shots, out, "header_compat", "02_wall_angle", 0.4, "Plays well with others", "Addon drawers, Storage Drawers' trims, Create");

        photo(shots, out, "wall", "01_wall", 0.5);
        photo(shots, out, "wall_angle", "02_wall_angle", 0.4);
        photo(shots, out, "autolink", "03_autolink", 0.5);
        photo(shots, out, "cut", "04_cut", 0.5);
        photo(shots, out, "trims", "05_trims", 0.5);
        photo(shots, out, "dryrun", "06_dryrun", 0.5);
    }

    /** The top banner: the title large, the logo beside it, on the wall. */
    static void banner(File shots, File out) throws Exception {
        int h = 280;
        BufferedImage b = blur(cover(ImageIO.read(new File(shots, "01_wall.png")), W, h, 0.45));
        Graphics2D g = b.createGraphics();
        g.setColor(alpha(INK, 160));
        g.fillRect(0, 0, W, h);
        g.setPaint(new GradientPaint(0, 0, alpha(INK, 235), W * 0.8f, 0, alpha(INK, 50)));
        g.fillRect(0, 0, W, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(ImageIO.read(new File("logo/logo.png")), W - 24 - 240, 20, 240, 240, null);
        int x = 34;
        text(g, "Contiguous", x, 40, 6, Color.WHITE);
        text(g, "Storage", x, 96, 6, Color.WHITE);
        text(g, "Drawers that link by touching.", x + 2, 180, 2, new Color(236, 236, 240));
        text(g, "Like Storage Drawers, for Functional Storage.", x + 2, 212, 2, SUB);
        g.dispose();
        jpg(b, new File(out, "banner.jpg"));
    }

    /** A section header: a blurred slice of a capture behind a title. */
    static void header(File shots, File out, String name, String shot, double fy, String title, String subtitle) throws Exception {
        int h = 100;
        BufferedImage img = blur(cover(ImageIO.read(new File(shots, shot + ".png")), W, h, fy));
        Graphics2D g = img.createGraphics();
        g.setColor(alpha(INK, 150));
        g.fillRect(0, 0, W, h);
        g.setPaint(new GradientPaint(0, 0, alpha(INK, 210), W * 0.75f, 0, alpha(INK, 30)));
        g.fillRect(0, 0, W, h);
        g.setColor(ACCENT);
        g.fillRect(0, 0, 6, h);
        text(g, title, 28, 20, 4, Color.WHITE);
        text(g, subtitle, 30, 64, 2, SUB);
        g.dispose();
        jpg(img, new File(out, name + ".jpg"));
    }

    /** A capture at the page's width, 16:9. */
    static void photo(File shots, File out, String name, String shot, double fy) throws Exception {
        jpg(cover(ImageIO.read(new File(shots, shot + ".png")), W, W * 9 / 16, fy), new File(out, name + ".jpg"));
    }

    static void jpg(BufferedImage img, File file) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.92f);
        file.delete();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    // Minecraft's font: 8-pixel cells, one pixel between letters, a shadow at a quarter of the colour.

    static void loadFont() throws Exception {
        String entry = "assets/minecraft/textures/font/ascii.png";
        File[] jars = new File("build/moddev/artifacts").listFiles((d, n) -> n.endsWith(".jar") && !n.endsWith("-sources.jar"));
        if (jars != null) {
            for (File jar : jars) {
                try (ZipFile zip = new ZipFile(jar)) {
                    if (zip.getEntry(entry) != null) {
                        font = ImageIO.read(zip.getInputStream(zip.getEntry(entry)));
                        break;
                    }
                }
            }
        }
        if (font == null) throw new IllegalStateException("no game resources under build/moddev/artifacts: build once first");
        for (int c = 0; c < 256; c++) {
            int gx = (c % 16) * 8, gy = (c / 16) * 8, w = 0;
            for (int x = 7; x >= 0 && w == 0; x--)
                for (int y = 0; y < 8; y++) if ((font.getRGB(gx + x, gy + y) >>> 24) > 0) { w = x + 1; break; }
            widths[c] = c == ' ' ? 4 : w;
        }
    }

    static void text(Graphics2D g, String s, int x, int y, int scale, Color c) {
        glyphs(g, s, x + scale, y + scale, scale, new Color(c.getRed() / 4, c.getGreen() / 4, c.getBlue() / 4, c.getAlpha()));
        glyphs(g, s, x, y, scale, c);
    }

    static void glyphs(Graphics2D g, String s, int x, int y, int scale, Color c) {
        g.setColor(c);
        for (char ch : s.toCharArray()) {
            int gx = (ch % 16) * 8, gy = (ch / 16) * 8;
            for (int yy = 0; yy < 8; yy++)
                for (int xx = 0; xx < 8; xx++)
                    if ((font.getRGB(gx + xx, gy + yy) >>> 24) > 0) g.fillRect(x + xx * scale, y + yy * scale, scale, scale);
            x += (widths[ch] + 1) * scale;
        }
    }

    // Images

    static BufferedImage cover(BufferedImage src, int w, int h, double fy) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double s = Math.max(w / (double) src.getWidth(), h / (double) src.getHeight());
        int sw = (int) (src.getWidth() * s), sh = (int) (src.getHeight() * s);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, (w - sw) / 2, (int) (-(sh - h) * fy), sw, sh, null);
        g.dispose();
        return o;
    }

    static BufferedImage blur(BufferedImage src) {
        int r = 5, n = (2 * r + 1) * (2 * r + 1);
        float[] k = new float[n];
        java.util.Arrays.fill(k, 1f / n);
        BufferedImage padded = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        new ConvolveOp(new Kernel(2 * r + 1, 2 * r + 1, k), ConvolveOp.EDGE_NO_OP, null).filter(src, padded);
        return padded;
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
