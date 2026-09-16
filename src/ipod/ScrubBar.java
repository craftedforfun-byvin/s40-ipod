package ipod;

import javax.microedition.lcdui.Graphics;

/** The pill-shaped progress / volume bar. */
public final class ScrubBar {

    private ScrubBar() {}

    public static void draw(Graphics g, int x, int y, int w, int h, long value, long max, boolean knob) {
        int r = h;
        g.setColor(Theme.SCRUB_BG);
        g.fillRoundRect(x, y, w, h, r, r);
        g.setColor(Theme.SCRUB_SHADE);
        g.drawLine(x + 3, y + 1, x + w - 4, y + 1);
        g.setColor(Theme.SCRUB_EDGE);
        g.drawRoundRect(x, y, w - 1, h - 1, r, r);

        int fill = 0;
        if (max > 0) {
            fill = (int) ((long) (w - 2) * value / max);
            if (fill < 0) fill = 0;
            if (fill > w - 2) fill = w - 2;
        }
        if (fill > 2) {
            int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
            g.clipRect(x + 1, y + 1, fill, h - 2);
            Gfx.vgrad(g, x + 1, y + 1, w - 2, h - 2, Theme.SCRUB_F_TOP, Theme.SCRUB_F_BOT);
            g.setClip(cx, cy, cw, ch);
        }

        if (knob) {
            int kx = x + 1 + fill;
            int kr = h + 4;
            int ky = y + h / 2 - kr / 2;
            g.setColor(Theme.KNOB_FILL);
            g.fillArc(kx - kr / 2, ky, kr, kr, 0, 360);
            g.setColor(Theme.KNOB_EDGE);
            g.drawArc(kx - kr / 2, ky, kr, kr, 0, 360);
        }
    }
}
