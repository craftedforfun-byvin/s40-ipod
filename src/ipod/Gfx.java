package ipod;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** Small drawing helpers: gradients, chevrons, battery, art frames, text fitting. */
public final class Gfx {

    private Gfx() {}

    /** Vertical gradient. */
    public static void vgrad(Graphics g, int x, int y, int w, int h, int top, int bottom) {
        if (h <= 0 || w <= 0) return;
        int r1 = (top >> 16) & 0xff, g1 = (top >> 8) & 0xff, b1 = top & 0xff;
        int r2 = (bottom >> 16) & 0xff, g2 = (bottom >> 8) & 0xff, b2 = bottom & 0xff;
        int last = h - 1;
        if (last == 0) last = 1;
        for (int i = 0; i < h; i++) {
            int r = r1 + (r2 - r1) * i / last;
            int gg = g1 + (g2 - g1) * i / last;
            int b = b1 + (b2 - b1) * i / last;
            g.setColor((r << 16) | (gg << 8) | b);
            g.drawLine(x, y + i, x + w - 1, y + i);
        }
    }

    /** The glossy blue selection bar. */
    public static void selectionBar(Graphics g, int x, int y, int w, int h) {
        int half = h / 2;
        vgrad(g, x, y, w, half, Theme.SEL_TOP, Theme.SEL_MID);
        vgrad(g, x, y + half, w, h - half, Theme.SEL_MID, Theme.SEL_BOTTOM);
        g.setColor(Theme.SEL_HILITE);
        g.drawLine(x, y, x + w - 1, y);
        g.setColor(Theme.SEL_EDGE);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
    }

    /** Right-pointing submenu chevron. */
    public static void chevron(Graphics g, int x, int cy, int colour) {
        g.setColor(colour);
        for (int i = 0; i < 4; i++) {
            g.drawLine(x + i, cy - 4 + i, x + i, cy + 4 - i);
        }
    }

    public static void battery(Graphics g, int x, int y, int pct) {
        int w = 18, h = 9;
        g.setColor(Theme.BATT_EDGE);
        g.drawRect(x, y, w, h);
        g.fillRect(x + w + 1, y + 3, 2, h - 5);
        int inner = w - 3;
        int fill = inner * pct / 100;
        if (fill < 1 && pct > 0) fill = 1;
        g.setColor(pct <= 20 ? 0xd03030 : Theme.BATT_FILL);
        g.fillRect(x + 2, y + 2, fill, h - 3);
    }

    /** Little play / pause glyph used in the status bar. */
    public static void transport(Graphics g, int x, int y, boolean playing, int colour) {
        g.setColor(colour);
        if (playing) {
            for (int i = 0; i < 5; i++) g.drawLine(x + i, y + i, x + i, y + 8 - i);
        } else {
            g.fillRect(x, y, 2, 9);
            g.fillRect(x + 4, y, 2, 9);
        }
    }

    /** Album art with the thin iPod border; draws a placeholder when img is null. */
    public static void art(Graphics g, Image img, int x, int y, int size) {
        if (img != null) {
            g.drawImage(img, x, y, Graphics.TOP | Graphics.LEFT);
        } else {
            vgrad(g, x, y, size, size, Theme.ART_EMPTY_A, Theme.ART_EMPTY_B);
            note(g, x + size / 2, y + size / 2, size / 3, 0xffffff);
        }
        g.setColor(Theme.ART_EDGE);
        g.drawRect(x, y, size - 1, size - 1);
    }

    /** A crotchet, drawn by hand so we need no image resources. */
    public static void note(Graphics g, int cx, int cy, int s, int colour) {
        if (s < 6) s = 6;
        g.setColor(colour);
        int stem = s;
        int hx = cx - s / 3, hy = cy + stem / 2;
        g.fillArc(hx - s / 3, hy - s / 5, s * 2 / 3, s / 2, 0, 360);
        g.fillRect(hx + s / 3 - 1, cy - stem / 2, 2, stem);
        g.fillRect(hx + s / 3 - 1, cy - stem / 2, s / 2, 2);
    }

    /** Truncate with an ellipsis so it fits w pixels. */
    public static String fit(String s, Font f, int w) {
        if (s == null) return "";
        if (f.stringWidth(s) <= w) return s;
        int dots = f.stringWidth("...");
        int n = s.length();
        while (n > 0 && f.substringWidth(s, 0, n) + dots > w) n--;
        return Str.cat(s.substring(0, n), "...");
    }

    /** mm:ss from milliseconds. */
    public static String time(long ms) {
        if (ms < 0) ms = 0;
        int total = (int) (ms / 1000L);
        int m = total / 60;
        int s = total % 60;
        StringBuffer sb = new StringBuffer();
        sb.append(m);
        sb.append(':');
        if (s < 10) sb.append('0');
        sb.append(s);
        return sb.toString();
    }

    /**
     * Pixel dimensions of an encoded image, read from its header without
     * decoding it - packed as (width &lt;&lt; 16) | height, or 0 if unrecognised.
     *
     * This exists because decoding is the single biggest allocation the app
     * makes: Image.createImage inflates to the source resolution, so a 600x600
     * embedded cover becomes about 1.4 MB before we get the chance to scale it
     * down. On a 2 MB heap that is the whole budget. Checking first lets us
     * decline politely instead of dying.
     */
    public static int imageDimensions(byte[] b) {
        if (b == null || b.length < 24) return 0;

        // PNG: 8 byte signature, then IHDR with width and height
        if ((b[0] & 0xff) == 0x89 && b[1] == 80 && b[2] == 78 && b[3] == 71) {
            return pack(be32(b, 16), be32(b, 20));
        }
        // GIF: logical screen descriptor, little endian
        if (b[0] == 71 && b[1] == 73 && b[2] == 70) {
            return pack((b[6] & 0xff) | ((b[7] & 0xff) << 8),
                        (b[8] & 0xff) | ((b[9] & 0xff) << 8));
        }
        // JPEG: walk the markers to the start-of-frame
        if ((b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8) {
            int i = 2;
            while (i + 9 < b.length) {
                if ((b[i] & 0xff) != 0xff) { i++; continue; }
                int marker = b[i + 1] & 0xff;
                if (marker == 0xd8 || marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
                    i += 2;
                    continue;
                }
                if (marker == 0xda || marker == 0xd9) break;      // image data starts
                int len = ((b[i + 2] & 0xff) << 8) | (b[i + 3] & 0xff);
                boolean sof = marker >= 0xc0 && marker <= 0xcf
                        && marker != 0xc4 && marker != 0xc8 && marker != 0xcc;
                if (sof) {
                    int h = ((b[i + 5] & 0xff) << 8) | (b[i + 6] & 0xff);
                    int w = ((b[i + 7] & 0xff) << 8) | (b[i + 8] & 0xff);
                    return pack(w, h);
                }
                if (len <= 0) break;
                i += 2 + len;
            }
        }
        return 0;
    }

    private static int pack(int w, int h) {
        if (w <= 0 || h <= 0 || w > 0xffff || h > 0xffff) return 0;
        return (w << 16) | h;
    }

    private static int be32(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16)
             | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    /** Nearest-neighbour scale; box-averages when shrinking a lot so art stays readable. */
    public static Image scale(Image src, int dw, int dh) {
        int sw = src.getWidth(), sh = src.getHeight();
        if (sw == dw && sh == dh) return src;
        int[] in = new int[sw * sh];
        src.getRGB(in, 0, sw, 0, 0, sw, sh);
        int[] out = new int[dw * dh];
        boolean box = (sw >= dw * 2) && (sh >= dh * 2);
        for (int y = 0; y < dh; y++) {
            int sy = y * sh / dh;
            int row = y * dw;
            for (int x = 0; x < dw; x++) {
                int sx = x * sw / dw;
                if (box) {
                    int x2 = sx + 1 < sw ? sx + 1 : sx;
                    int y2 = sy + 1 < sh ? sy + 1 : sy;
                    int a = in[sy * sw + sx], b = in[sy * sw + x2];
                    int c = in[y2 * sw + sx], d = in[y2 * sw + x2];
                    int r = (((a >> 16) & 0xff) + ((b >> 16) & 0xff) + ((c >> 16) & 0xff) + ((d >> 16) & 0xff)) >> 2;
                    int gg = (((a >> 8) & 0xff) + ((b >> 8) & 0xff) + ((c >> 8) & 0xff) + ((d >> 8) & 0xff)) >> 2;
                    int bb = ((a & 0xff) + (b & 0xff) + (c & 0xff) + (d & 0xff)) >> 2;
                    out[row + x] = 0xff000000 | (r << 16) | (gg << 8) | bb;
                } else {
                    out[row + x] = in[sy * sw + sx] | 0xff000000;
                }
            }
        }
        return Image.createRGBImage(out, dw, dh, false);
    }

    /**
     * Mirrored, fading copy of an image - the Cover Flow reflection. Any
     * transparency in the source is preserved, so this works on the
     * perspective-projected covers as well as on plain square ones.
     */
    public static Image reflection(Image src, int height) {
        int w = src.getWidth(), h = src.getHeight();
        if (height > h) height = h;
        int[] in = new int[w * h];
        src.getRGB(in, 0, w, 0, 0, w, h);
        int[] out = new int[w * height];
        for (int y = 0; y < height; y++) {
            int sy = h - 1 - y;
            int ramp = 110 - (110 * y / height);
            if (ramp < 0) ramp = 0;
            int row = y * w, srow = sy * w;
            for (int x = 0; x < w; x++) {
                int px = in[srow + x];
                int a = ((px >>> 24) * ramp) / 255;
                out[row + x] = (a << 24) | (px & 0x00ffffff);
            }
        }
        return Image.createRGBImage(out, w, height, true);
    }

    /**
     * Projects a cover as if it were rotated about its vertical axis, which is
     * what makes Cover Flow look like Cover Flow. Each source column lands at
     * its own screen x and its own height, so the square becomes a trapezoid
     * that is taller on the near edge; the far edge is shaded to sell the
     * depth. The result is narrower than the source and has transparent
     * corners, so it overlaps its neighbours cleanly.
     *
     * Only CLDC 1.1 maths is used here - sin, cos and PI are all it provides.
     *
     * @param angleDeg      how far the cover is turned away, 0 = facing us
     * @param nearEdgeRight which edge comes toward the viewer. A cover that
     *                      angles inward from the right of centre has its right
     *                      (outer) edge nearest, so this is true for the covers
     *                      on the right and false for those on the left.
     */
    public static Image perspective(Image src, int angleDeg, boolean nearEdgeRight) {
        int sw = src.getWidth(), sh = src.getHeight();
        if (sw < 2 || sh < 2) return src;

        int[] in = new int[sw * sh];
        src.getRGB(in, 0, sw, 0, 0, sw, sh);

        double th = angleDeg * Math.PI / 180.0;
        double cos = Math.cos(th), sin = Math.sin(th);
        double focal = sw * 2.2;                 // camera distance, in cover widths

        double[] xs = new double[sw];            // screen x of each source column
        double[] ss = new double[sw];            // and its vertical scale
        double sMin = 0, sMax = 0;
        for (int u = 0; u < sw; u++) {
            double t = (double) u / (sw - 1) - 0.5;
            double z = (nearEdgeRight ? -t : t) * sw * sin;
            double s = focal / (focal + z);
            ss[u] = s;
            if (u == 0) { sMin = sMax = s; }
            else if (s < sMin) sMin = s;
            else if (s > sMax) sMax = s;
        }

        // Normalise so the nearest column is exactly the source height. Without
        // this the near edge scales past 1.0 and gets clipped by the output
        // bitmap - and a turned cover would stand taller than the flat one in
        // the centre, which is backwards.
        for (int u = 0; u < sw; u++) {
            ss[u] /= sMax;
            double t = (double) u / (sw - 1) - 0.5;
            xs[u] = t * sw * cos * ss[u];
        }
        sMin /= sMax;
        sMax = 1.0;

        double left = xs[0], right = xs[sw - 1];
        if (right < left) { double t = left; left = right; right = t; }
        int dw = (int) (right - left) + 1;
        if (dw < 2) dw = 2;
        int dh = sh;
        int[] out = new int[dw * dh];            // zero alpha = transparent

        double span = (sMax - sMin);
        if (span < 0.0001) span = 0.0001;

        for (int u = 0; u < sw; u++) {
            int x0 = (int) (xs[u] - left);
            int x1 = (u + 1 < sw) ? (int) (xs[u + 1] - left) : x0 + 1;
            if (x1 <= x0) x1 = x0 + 1;
            if (x0 < 0) x0 = 0;
            if (x1 > dw) x1 = dw;

            int hCol = (int) (sh * ss[u]);
            if (hCol > dh) hCol = dh;
            if (hCol < 1) hCol = 1;
            int top = (dh - hCol) / 2;

            // near columns stay bright, far columns fall away to about 55%
            int bright = 55 + (int) (45.0 * (ss[u] - sMin) / span);

            for (int x = x0; x < x1; x++) {
                for (int y = 0; y < hCol; y++) {
                    int sy = y * sh / hCol;
                    int px = in[sy * sw + u];
                    int r = (((px >> 16) & 0xff) * bright) / 100;
                    int g = (((px >> 8) & 0xff) * bright) / 100;
                    int b = ((px & 0xff) * bright) / 100;
                    out[(top + y) * dw + x] = 0xff000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
        return Image.createRGBImage(out, dw, dh, true);
    }
}
