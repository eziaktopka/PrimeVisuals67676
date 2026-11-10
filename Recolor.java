package primevisuals;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Built-in colour variants generated from the vanilla textures. */
public class Recolor {
    public static final String[] NAMES = {
            "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink",
            "Black & White", "Dark", "Neon", "Inverted"
    };
    private static final float[] HUES = {0.00f, 0.07f, 0.15f, 0.33f, 0.50f, 0.62f, 0.77f, 0.90f};
    private static final int BW = 8, DARK = 9, NEON = 10, INVERT = 11;

    public static byte[] apply(byte[] png, int mode) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
            if (src == null) return null;
            int w = src.getWidth();
            int h = src.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out.setRGB(x, y, tint(src.getRGB(x, y), mode));
                }
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bo);
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    static int tint(int argb, int mode) {
        int a = argb >>> 24;
        if (a == 0) return argb;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        if (mode == INVERT) return (a << 24) | ((255 - r) << 16) | ((255 - g) << 8) | (255 - b);
        if (mode == BW) {
            int l = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            return (a << 24) | (l << 16) | (l << 8) | l;
        }
        float[] hsv = new float[3];
        Color.RGBtoHSB(r, g, b, hsv);
        float hh = hsv[0], s = hsv[1], v = hsv[2];
        if (mode < HUES.length) {
            hh = HUES[mode];
            if (v > 0.12f) s = Math.max(s, 0.5f);
        } else if (mode == DARK) {
            v *= 0.5f;
        } else if (mode == NEON) {
            if (s > 0.1f) s = Math.min(1f, s * 1.5f + 0.25f);
            v = Math.min(1f, v * 1.2f + 0.08f);
        }
        return (a << 24) | (Color.HSBtoRGB(hh, s, v) & 0xFFFFFF);
    }
}
