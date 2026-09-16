package ipod;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * The Cover Flow flip: the chosen album turns over like a CD case to show its
 * track list on the back, then hands over to the real list screen.
 *
 * The front half of the turn is the cover; the back half is a panel rendered
 * once with the track listing on it. Both are nearly edge-on as they pass 90
 * degrees, which is what lets the small cover be swapped for the larger panel
 * without anyone seeing the join - the same trick a real card flip uses.
 *
 * All the projections are built on a background thread before the turn starts,
 * because six of them is far too much work to do inside a frame on this
 * hardware. Until they are ready the cover just sits there; if they take too
 * long, or anything fails, the flip is abandoned and the list opens directly.
 * The UI thread never waits.
 */
public final class FlipScreen extends UIScreen {

    private static final int[] FRONT = { 0, 45, 78 };   // cover turning away
    private static final int[] BACK  = { 78, 45, 0 };   // panel turning to face us
    private static final long FLIP_MS = 420;
    private static final long GIVE_UP_MS = 1500;

    private final int albumIdx;
    private final int coverSize;
    private final MenuScreen target;

    private Image cover;
    private Image[] frontFrames;
    private Image[] backFrames;

    private volatile boolean ready;
    private volatile boolean failed;
    private long startedBuilding;
    private long flipStart;

    public FlipScreen(int albumIdx, int coverSize, Image cover, MenuScreen target) {
        this.albumIdx = albumIdx;
        this.coverSize = coverSize;
        this.cover = cover;
        this.target = target;
        this.title = target != null ? target.title() : "Album";
    }

    public void onShow() {
        startedBuilding = System.currentTimeMillis();
        new Thread(new Runnable() {
            public void run() {
                build();
            }
        }).start();
    }

    /* ---- building -------------------------------------------------------- */

    private void build() {
        try {
            int w = shell.getWidth();
            int h = shell.getHeight() - shell.barH;
            int panelW = w - 60;
            if (panelW > 200) panelW = 200;
            int panelH = h - 40;
            if (panelH > 170) panelH = 170;
            if (panelW < 80 || panelH < 70) { failed = true; return; }

            // The cover also grows as it turns, from the size Cover Flow was
            // showing it at up to the height of the back panel. That matters:
            // at the half-way point the two halves swap, and if their heights
            // did not match by then you would see the join.
            Image[] front = new Image[FRONT.length];
            front[0] = cover;
            for (int i = 1; i < FRONT.length; i++) {
                int size = coverSize + (panelH - coverSize) * i / (FRONT.length - 1);
                Image grown = (size == coverSize) ? cover : Gfx.scale(cover, size, size);
                front[i] = Gfx.perspective(grown, FRONT[i], false);
            }

            Image panel = backPanel(panelW, panelH);
            Image[] back = new Image[BACK.length];
            back[BACK.length - 1] = panel;
            for (int i = 0; i < BACK.length - 1; i++) {
                back[i] = Gfx.perspective(panel, BACK[i], true);
            }

            frontFrames = front;
            backFrames = back;
            ready = true;
            flipStart = System.currentTimeMillis();
        } catch (Throwable e) {
            failed = true;
        }
        if (shell != null) shell.refresh();
    }

    /** The back of the case: album, artist and as much of the listing as fits. */
    private Image backPanel(int w, int h) {
        Image img = Image.createImage(w, h);
        Graphics g = img.getGraphics();

        Gfx.vgrad(g, 0, 0, w, h, 0xf7f6f2, 0xdedbd3);
        g.setColor(0x8d8a84);
        g.drawRect(0, 0, w - 1, h - 1);
        g.setColor(0xffffff);
        g.drawLine(1, 1, w - 2, 1);

        Library.Album al = shell.lib.album(albumIdx);
        if (al == null) return Image.createImage(img);

        int y = 7;
        g.setFont(Theme.F_TINYB);
        g.setColor(0x1a1a1a);
        g.drawString(Gfx.fit(al.name, Theme.F_TINYB, w - 14), 7, y, Graphics.TOP | Graphics.LEFT);
        y += Theme.F_TINYB.getHeight() + 1;

        g.setFont(Theme.F_TINY);
        g.setColor(0x66635e);
        g.drawString(Gfx.fit(al.artist, Theme.F_TINY, w - 14), 7, y, Graphics.TOP | Graphics.LEFT);
        y += Theme.F_TINY.getHeight() + 4;

        g.setColor(0xa9a5a0);
        g.drawLine(7, y, w - 8, y);
        y += 4;

        Font f = Theme.F_TINY;
        int lineH = f.getHeight() + 2;
        int numW = f.stringWidth("88.") + 4;
        int n = al.trackIdx.size();
        for (int i = 0; i < n && y + lineH <= h - 14; i++) {
            Track t = shell.lib.track(Library.index(al.trackIdx, i));
            if (t == null) continue;
            g.setFont(f);
            g.setColor(0x97938d);
            StringBuffer num = new StringBuffer();
            num.append(i + 1);
            num.append('.');
            g.drawString(num.toString(), 7 + numW - 4, y, Graphics.TOP | Graphics.RIGHT);
            g.setColor(0x24221f);
            g.drawString(Gfx.fit(t.displayTitle(), f, w - 14 - numW), 7 + numW, y,
                    Graphics.TOP | Graphics.LEFT);
            y += lineH;
        }

        g.setFont(Theme.F_TINY);
        g.setColor(0x8d8a84);
        StringBuffer foot = new StringBuffer();
        foot.append(n);
        foot.append(n == 1 ? " song" : " songs");
        g.drawString(foot.toString(), w - 8, h - f.getHeight() - 5, Graphics.TOP | Graphics.RIGHT);

        // immutable copy: Gfx.perspective reads it back with getRGB
        return Image.createImage(img);
    }

    /* ---- painting -------------------------------------------------------- */

    public void paint(Graphics g, int w, int h) {
        Gfx.vgrad(g, 0, 0, w, h, Theme.CF_TOP, Theme.CF_BOTTOM);

        if (failed) return;
        if (!ready) {
            // still rendering - hold the cover exactly where Cover Flow left it
            if (cover != null) {
                Gfx.art(g, cover, (w - coverSize) / 2, (h - coverSize) / 2, coverSize);
            }
            return;
        }

        long dt = System.currentTimeMillis() - flipStart;
        if (dt > FLIP_MS) dt = FLIP_MS;
        int p = (int) (255L * dt / FLIP_MS);          // 0..255 through the turn

        Image img;
        if (p < 128) {
            int i = p * FRONT.length / 128;
            if (i >= FRONT.length) i = FRONT.length - 1;
            img = frontFrames[i];
        } else {
            int i = (p - 128) * BACK.length / 128;
            if (i >= BACK.length) i = BACK.length - 1;
            img = backFrames[i];
        }
        if (img == null) return;

        int iw = img.getWidth(), ih = img.getHeight();
        g.drawImage(img, (w - iw) / 2, (h - ih) / 2, Graphics.TOP | Graphics.LEFT);
    }

    private void finish() {
        release();
        if (target != null) shell.replaceTop(target);
        else shell.pop();
    }

    private void release() {
        frontFrames = null;
        backFrames = null;
        cover = null;
    }

    /** Any key skips straight to the listing. */
    public void key(int action, int code) {
        finish();
    }

    /**
     * The turn is advanced here rather than in paint: finishing swaps the top
     * of the screen stack, which must not happen in the middle of rendering it.
     */
    public boolean tick() {
        long now = System.currentTimeMillis();
        if (failed) { finish(); return false; }
        if (!ready) {
            if (now - startedBuilding > GIVE_UP_MS) { finish(); return false; }
            return true;
        }
        if (now - flipStart >= FLIP_MS) { finish(); return false; }
        return true;
    }

    public void onHide() {
        release();
    }
}
