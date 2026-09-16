package ipod;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** Track info, big cover, scrub bar - the screen the iPod lands on. */
public final class NowPlaying extends UIScreen {

    private static final int HUD_NONE = 0, HUD_VOLUME = 1, HUD_TOAST = 2;

    private int hud = HUD_NONE;
    private long hudUntil;
    private String toast;

    private int marquee;
    private int marqueePause = 14;
    private boolean seeking;
    private int titleWidth = 200;   // set by the last paint, used by the marquee

    public NowPlaying() {
        title = "Now Playing";
    }

    public void onShow() {
        marquee = 0;
        marqueePause = 14;
    }

    /* ---- painting ---------------------------------------------------------- */

    public void paint(Graphics g, int w, int h) {
        g.setColor(Theme.LIST_BG);
        g.fillRect(0, 0, w, h);

        Engine e = shell.engine;
        Track t = e.current();
        if (t == null) {
            g.setFont(Theme.F_BODY);
            g.setColor(Theme.NP_SUB);
            g.drawString("Nothing playing", w / 2, h / 2 - 8, Graphics.TOP | Graphics.HCENTER);
            return;
        }

        int barY = h - 24;
        // A landscape screen has width to spare and no height: put the cover
        // beside the text instead of above it.
        if (w >= h) landscape(g, w, h, barY, t, e);
        else portrait(g, w, h, barY, t, e);

        if (hud == HUD_VOLUME) paintVolume(g, w, barY);
        else paintScrub(g, w, barY, t);
        if (hud == HUD_TOAST) paintToast(g, w, h);
    }

    /** Cover on the left, text stacked to the right of it. */
    private void landscape(Graphics g, int w, int h, int barY, Track t, Engine e) {
        int artSize = barY - 14;
        if (artSize > h * 72 / 100) artSize = h * 72 / 100;
        if (artSize > 150) artSize = 150;
        int ax = 8;
        int ay = (barY - artSize) / 2;
        if (artSize > 24) Gfx.art(g, shell.art.forTrack(t, artSize), ax, ay, artSize);

        int tx = ax + artSize + 12;
        int tw = w - tx - 8;
        if (tw < 60) return;
        int y = ay + 2;

        g.setFont(Theme.F_TINY);
        g.setColor(Theme.NP_COUNT);
        g.drawString(Str.pair(e.queuePos() + 1, " of ", e.queueSize()), tx, y, Graphics.TOP | Graphics.LEFT);
        modeGlyphs(g, tx + tw, y + 3);
        y += Theme.F_TINY.getHeight() + 6;

        y = title(g, t, tx, y, tw);
        y += 2;
        g.setFont(Theme.F_TINY);
        g.setColor(Theme.NP_SUB);
        g.drawString(Gfx.fit(t.displayArtist(), Theme.F_TINY, tw), tx, y, Graphics.TOP | Graphics.LEFT);
        y += Theme.F_TINY.getHeight() + 2;
        g.setColor(0x8a8a8a);
        g.drawString(Gfx.fit(t.displayAlbum(), Theme.F_TINY, tw), tx, y, Graphics.TOP | Graphics.LEFT);
    }

    /** The tall arrangement: text on top, big cover, scrub bar under it. */
    private void portrait(Graphics g, int w, int h, int barY, Track t, Engine e) {
        int y = 4;
        g.setFont(Theme.F_TINY);
        g.setColor(Theme.NP_COUNT);
        g.drawString(Str.pair(e.queuePos() + 1, " of ", e.queueSize()), 6, y, Graphics.TOP | Graphics.LEFT);
        modeGlyphs(g, w - 4, y + 3);
        y += Theme.F_TINY.getHeight() + 6;

        y = title(g, t, 6, y, w - 12);
        y += 2;
        g.setFont(Theme.F_TINY);
        g.setColor(Theme.NP_SUB);
        g.drawString(Gfx.fit(t.displayArtist(), Theme.F_TINY, w - 12), 6, y, Graphics.TOP | Graphics.LEFT);
        y += Theme.F_TINY.getHeight() + 1;
        g.setColor(0x8a8a8a);
        g.drawString(Gfx.fit(t.displayAlbum(), Theme.F_TINY, w - 12), 6, y, Graphics.TOP | Graphics.LEFT);
        y += Theme.F_TINY.getHeight() + 8;

        int artMax = barY - 22 - y;
        int artSize = w - 96;
        if (artSize > artMax) artSize = artMax;
        if (artSize > 152) artSize = 152;
        if (artSize > 24) {
            Gfx.art(g, shell.art.forTrack(t, artSize), (w - artSize) / 2, y, artSize);
        }
    }

    /** Title line, sliding along when it is too wide to fit. */
    private int title(Graphics g, Track t, int x, int y, int width) {
        titleWidth = width;
        Font tf = Theme.F_BODYB;
        g.setFont(tf);
        g.setColor(Theme.NP_TITLE);
        String name = t.displayTitle();
        if (tf.stringWidth(name) > width) {
            int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
            g.clipRect(x, y, width, tf.getHeight());
            g.drawString(name, x - marquee, y, Graphics.TOP | Graphics.LEFT);
            g.setClip(cx, cy, cw, ch);
        } else {
            g.drawString(name, x, y, Graphics.TOP | Graphics.LEFT);
        }
        return y + tf.getHeight();
    }

    /** Shuffle and repeat indicators, right-aligned to rightX. */
    private void modeGlyphs(Graphics g, int rightX, int y) {
        int x = rightX - 14;
        if (shell.settings.repeat != Settings.REPEAT_OFF) {
            repeatGlyph(g, x, y, Theme.NP_COUNT, shell.settings.repeat == Settings.REPEAT_ONE);
            x -= 20;
        }
        if (shell.settings.shuffle != Settings.SHUFFLE_OFF) {
            shuffleGlyph(g, x - 2, y, Theme.NP_COUNT);
        }
    }

    private void paintScrub(Graphics g, int w, int barY, Track t) {
        Engine e = shell.engine;
        long pos = e.position();
        long dur = e.duration();
        int bx = 36, bw = w - 72;
        ScrubBar.draw(g, bx, barY, bw, 9, pos, dur, seeking || dur > 0);
        g.setFont(Theme.F_TINY);
        g.setColor(Theme.NP_COUNT);
        int ty = barY + (9 - Theme.F_TINY.getHeight()) / 2;
        g.drawString(Gfx.time(pos), 4, ty, Graphics.TOP | Graphics.LEFT);
        String right = dur > 0 ? Str.cat("-", Gfx.time(dur - pos)) : "--:--";
        g.drawString(right, w - 4, ty, Graphics.TOP | Graphics.RIGHT);
    }

    private void paintVolume(Graphics g, int w, int barY) {
        // Don't animate a bar that isn't connected to anything: if the device
        // gave us no VolumeControl, say so and point at the keys that do work.
        if (shell.engine.hasTrack() && !shell.engine.volumeControlled) {
            g.setFont(Theme.F_TINY);
            g.setColor(Theme.NP_COUNT);
            g.drawString("Use the phone's volume keys", w / 2, barY - 2,
                    Graphics.TOP | Graphics.HCENTER);
            return;
        }
        int bx = 36, bw = w - 72;
        ScrubBar.draw(g, bx, barY, bw, 9, shell.settings.volume, 100, true);
        speaker(g, 6, barY - 1, Theme.NP_COUNT, false);
        speaker(g, w - 20, barY - 1, Theme.NP_COUNT, true);
    }

    private void paintToast(Graphics g, int w, int h) {
        if (toast == null) return;
        Font f = Theme.F_TINYB;
        int tw = f.stringWidth(toast) + 24;
        if (tw > w - 20) tw = w - 20;
        int th = f.getHeight() + 14;
        int x = (w - tw) / 2, y = h / 2 - th / 2;
        g.setColor(0x000000);
        g.fillRoundRect(x, y, tw, th, 10, 10);
        g.setColor(0x555555);
        g.drawRoundRect(x, y, tw - 1, th - 1, 10, 10);
        g.setFont(f);
        g.setColor(0xffffff);
        g.drawString(Gfx.fit(toast, f, tw - 12), x + tw / 2, y + 7, Graphics.TOP | Graphics.HCENTER);
    }

    /* ---- little glyphs ------------------------------------------------------ */

    private void shuffleGlyph(Graphics g, int x, int y, int c) {
        g.setColor(c);
        g.drawLine(x, y, x + 4, y);
        g.drawLine(x + 4, y, x + 10, y + 6);
        g.drawLine(x + 10, y + 6, x + 14, y + 6);
        g.drawLine(x, y + 6, x + 4, y + 6);
        g.drawLine(x + 4, y + 6, x + 10, y);
        g.drawLine(x + 10, y, x + 14, y);
        g.fillTriangle(x + 12, y - 2, x + 12, y + 2, x + 15, y);
        g.fillTriangle(x + 12, y + 4, x + 12, y + 8, x + 15, y + 6);
    }

    private void repeatGlyph(Graphics g, int x, int y, int c, boolean one) {
        g.setColor(c);
        g.drawRect(x, y, 12, 7);
        g.setColor(Theme.LIST_BG);
        g.fillRect(x + 3, y - 1, 6, 3);
        g.fillRect(x + 3, y + 5, 6, 3);
        g.setColor(c);
        g.fillTriangle(x + 2, y - 2, x + 2, y + 2, x + 5, y);
        if (one) {
            g.drawLine(x + 6, y + 1, x + 6, y + 6);
            g.drawLine(x + 5, y + 2, x + 6, y + 1);
        }
    }

    private void speaker(Graphics g, int x, int y, int c, boolean loud) {
        g.setColor(c);
        g.fillRect(x, y + 3, 3, 4);
        g.fillTriangle(x + 3, y + 5, x + 7, y, x + 7, y + 10);
        if (loud) {
            g.drawArc(x + 6, y + 1, 8, 8, -60, 120);
            g.drawArc(x + 4, y + 3, 8, 4, -60, 120);
        }
    }

    /* ---- input --------------------------------------------------------------- */

    public void key(int action, int code) {
        Engine e = shell.engine;
        switch (action) {
            case Shell.SELECT:
                e.toggle();
                return;
            case Shell.LEFT:
                seeking = false;
                e.prev();
                return;
            case Shell.RIGHT:
                seeking = false;
                e.next();
                return;
            case Shell.UP:
                e.nudgeVolume(5);
                showHud(HUD_VOLUME, null);
                return;
            case Shell.DOWN:
                e.nudgeVolume(-5);
                showHud(HUD_VOLUME, null);
                return;
            case Shell.BACK:
                shell.pop();
                return;
            default:
                break;
        }
        if (code == 48 || code == 97 || code == 65) {          // 0 / a / A
            e.addToOnTheGo(e.current());
            showHud(HUD_TOAST, "Added to On-The-Go");
        }
    }

    /** Holding left or right scrubs rather than skipping. */
    public void keyRepeat(int action, int code) {
        if (action == Shell.LEFT) {
            seeking = true;
            shell.engine.seekBy(-5);
        } else if (action == Shell.RIGHT) {
            seeking = true;
            shell.engine.seekBy(5);
        } else {
            key(action, code);
        }
    }

    private void showHud(int kind, String message) {
        hud = kind;
        toast = message;
        hudUntil = System.currentTimeMillis() + (kind == HUD_TOAST ? 1400L : 1200L);
        if (kind == HUD_VOLUME) Store.saveSettings(shell.settings);
    }

    public boolean tick() {
        boolean more = false;
        if (hud != HUD_NONE) {
            if (System.currentTimeMillis() > hudUntil) hud = HUD_NONE;
            more = true;
        }
        Track t = shell.engine.current();
        if (t != null) {
            int over = Theme.F_BODYB.stringWidth(t.displayTitle()) - titleWidth;
            if (over > 0) {
                if (marqueePause > 0) marqueePause--;
                else {
                    marquee += 2;
                    if (marquee > over + 20) { marquee = 0; marqueePause = 16; }
                }
                more = true;
            } else if (marquee != 0) {
                marquee = 0;
                more = true;
            }
        }
        return more || shell.engine.playing;
    }
}
