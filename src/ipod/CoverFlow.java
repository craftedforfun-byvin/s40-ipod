package ipod;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Cover Flow.
 *
 * The cover arriving in the centre rotates flat and grows as it lands; the ones
 * beside it stay angled inward. A phone this old cannot re-project a cover every
 * frame, so the rotation is done as pre-rendered angle keyframes (see ANGLES)
 * that a cover steps through while its position slides.
 *
 * The keyframes are built on a background thread, which asks for a repaint after
 * each one. That repaint matters: an earlier version built them during idle
 * ticks and never marked the screen dirty, so the projections went into the
 * cache and nothing was ever redrawn to show them - the covers stayed flat for
 * as long as you sat there looking at them.
 *
 * Painting never builds anything and never waits. A cover with no keyframe yet
 * is drawn flat, so the screen is always usable while the depth fills in.
 */
public final class CoverFlow extends UIScreen implements Runnable {

    /** 0 is flat; a cover steps down this list as it turns away from you. */
    private static final int[] ANGLES = { 0, 26, 46, 62 };
    private static final int REST = 3;               // index of the resting angle
    private static final long SLIDE_MS = 280;
    private static final int WINGS = 2;
    /**
     * Projections to keep. Each is a decoded bitmap, so this has to follow the
     * heap rather than being a fixed number: on a small VM eighteen of them is
     * more memory than the whole app is allowed.
     */
    private static int maxFrames() {
        long total = Runtime.getRuntime().totalMemory();
        if (total < 700000L) return 0;        // no room for depth at all
        if (total < 1300000L) return 6;
        if (total < 2200000L) return 12;
        return 18;
    }

    private static final int ONE = 256;              // fixed point: 1.0 slot

    /** Read by the Diagnostics screen. */
    public static volatile int framesBuilt;
    public static volatile String lastError;

    private int big = 96;
    private int cur;
    private int dir;
    private long slideStart;                         // 0 when settled

    private final Hashtable frames = new Hashtable();
    private final Vector frameOrder = new Vector();
    private final Hashtable mirrors = new Hashtable();

    private volatile boolean building;

    public CoverFlow() {
        title = "Cover Flow";
    }

    public void onShow() {
        Track t = shell.engine.current();
        if (t != null && t.albumIdx >= 0) cur = t.albumIdx;
        prefetch();
        building = true;
        new Thread(this).start();
    }

    public void onHide() {
        building = false;
        synchronized (this) { notify(); }
        frames.clear();
        frameOrder.removeAllElements();
        mirrors.clear();
    }

    /* ---- geometry ------------------------------------------------------- */

    private void sizeFor(int w, int h) {
        int b = h * 46 / 100;
        if (b > 96) b = 96;
        if (b > w / 3) b = w / 3;
        if (b < 40) b = 40;
        if (b != big) {
            // a size change invalidates every projection we hold
            big = b;
            frames.clear();
            frameOrder.removeAllElements();
            mirrors.clear();
            wake();
        }
    }

    private int progress() {
        if (slideStart == 0) return ONE;
        long dt = System.currentTimeMillis() - slideStart;
        if (dt >= SLIDE_MS) {
            slideStart = 0;
            return ONE;
        }
        if (dt < 0) dt = 0;
        int p = (int) (ONE * dt / SLIDE_MS);
        int inv = ONE - p;                           // ease out cubic
        return ONE - (inv * inv / ONE) * inv / ONE;
    }

    private int slotOf(int i, int eased) {
        return ((i - cur) * ONE) + dir * (ONE - eased);
    }

    private int xCentreOf(int slot, int cx, int d1, int d2) {
        int as = slot < 0 ? -slot : slot;
        if (as <= ONE) return cx + slot * d1 / ONE;
        int sign = slot < 0 ? -1 : 1;
        return cx + sign * (d1 + (as - ONE) * d2 / ONE);
    }

    private static int angleIndexFor(int slot) {
        int as = slot < 0 ? -slot : slot;
        if (as < ONE / 4) return 0;
        if (as < ONE / 2) return 1;
        if (as < ONE * 3 / 4) return 2;
        return REST;
    }

    /* ---- painting -------------------------------------------------------- */

    public void paint(Graphics g, int w, int h) {
        sizeFor(w, h);
        Gfx.vgrad(g, 0, 0, w, h, Theme.CF_TOP, Theme.CF_BOTTOM);

        int n = shell.lib.albums.size();
        if (n == 0) {
            g.setFont(Theme.F_BODY);
            g.setColor(Theme.CF_TEXT);
            g.drawString("No albums", w / 2, h / 2, Graphics.TOP | Graphics.HCENTER);
            return;
        }

        int labelH = Theme.F_TINYB.getHeight() + Theme.F_TINY.getHeight() + 8;
        int centreY = (h - labelH) / 2;
        int cx = w / 2;
        int shelf = centreY + big / 2;
        int eased = progress();

        int turned = turnedWidth();
        int d1 = big / 2 + 2 + turned / 2;
        int d2 = turned * 3 / 4;

        g.setColor(0x101010);
        g.drawLine(0, shelf, w, shelf);

        // farthest first, so nearer covers overlap them
        Vector order = new Vector();
        for (int i = cur - WINGS - 1; i <= cur + WINGS + 1; i++) {
            if (i >= 0 && i < n) order.addElement(new Integer(i));
        }
        for (int a = 0; a < order.size(); a++) {
            for (int b = a + 1; b < order.size(); b++) {
                int ia = ((Integer) order.elementAt(a)).intValue();
                int ib = ((Integer) order.elementAt(b)).intValue();
                if (absSlot(ia, eased) < absSlot(ib, eased)) {
                    order.setElementAt(new Integer(ib), a);
                    order.setElementAt(new Integer(ia), b);
                }
            }
        }
        for (int k = 0; k < order.size(); k++) {
            int i = ((Integer) order.elementAt(k)).intValue();
            drawCover(g, i, slotOf(i, eased), cx, centreY, d1, d2, w);
        }

        Library.Album al = shell.lib.album(cur);
        if (al != null) {
            int ly = h - labelH + 2;
            g.setFont(Theme.F_TINYB);
            g.setColor(Theme.CF_TEXT);
            g.drawString(Gfx.fit(al.name, Theme.F_TINYB, w - 16), cx, ly,
                    Graphics.TOP | Graphics.HCENTER);
            g.setFont(Theme.F_TINY);
            g.setColor(Theme.CF_SUB);
            g.drawString(Gfx.fit(al.artist, Theme.F_TINY, w - 16), cx,
                    ly + Theme.F_TINYB.getHeight() + 1, Graphics.TOP | Graphics.HCENTER);
        }

        g.setFont(Theme.F_TINY);
        g.setColor(0x707070);
        g.drawString(Str.pair(cur + 1, " / ", n), w - 6, 4, Graphics.TOP | Graphics.RIGHT);

        if (n < 2) {
            g.setColor(0x8a8a8a);
            g.drawString("Only one album - nothing to flow through", w / 2, 4,
                    Graphics.TOP | Graphics.HCENTER);
        }
    }

    private int absSlot(int i, int eased) {
        int s = slotOf(i, eased);
        return s < 0 ? -s : s;
    }

    private int turnedWidth() {
        Image f = (Image) frames.get(key(cur, REST, true));
        if (f == null) f = (Image) frames.get(key(cur, REST, false));
        if (f != null) return f.getWidth();
        return big * 45 / 100;                       // close enough at 62 degrees
    }

    private void drawCover(Graphics g, int idx, int slot, int cx, int centreY,
                           int d1, int d2, int w) {
        boolean leftSide = slot < 0;
        int ai = angleIndexFor(slot);

        Image img = (ai == 0) ? shell.art.get(idx, big)
                              : (Image) frames.get(key(idx, ai, leftSide));
        boolean flat = (ai == 0);
        if (img == null) {                           // not projected yet
            img = shell.art.get(idx, big);
            flat = true;
        }
        if (img == null) {                           // art still loading
            int x = xCentreOf(slot, cx, d1, d2) - big / 2;
            Gfx.art(g, null, x, centreY - big / 2, big);
            return;
        }

        int iw = img.getWidth(), ih = img.getHeight();
        int x = xCentreOf(slot, cx, d1, d2) - iw / 2;
        int y = centreY - ih / 2;
        if (x + iw < -8 || x > w + 8) return;

        g.drawImage(img, x, y, Graphics.TOP | Graphics.LEFT);
        if (flat) {
            g.setColor(Theme.ART_EDGE);
            g.drawRect(x, y, iw - 1, ih - 1);
        }
        if (ai == 0 || ai == REST) {
            Image m = (Image) mirrors.get(key(idx, flat ? 0 : ai, leftSide));
            if (m != null) g.drawImage(m, x, y + ih + 1, Graphics.TOP | Graphics.LEFT);
        }
    }

    /* ---- background builder ------------------------------------------------
     * Builds keyframes off the UI thread and asks for a repaint after each, so
     * the depth appears as it becomes available instead of sitting unseen in
     * the cache.
     */

    public void run() {
        while (building) {
            Integer k = nextNeeded();
            if (k == null) {
                synchronized (this) {
                    try { wait(700); } catch (InterruptedException e) {}
                }
                continue;
            }
            buildFrame(k);
            if (shell != null) shell.refresh();
        }
    }

    private void wake() {
        synchronized (this) { notify(); }
    }

    /** First keyframe that is wanted but missing, in priority order. */
    private Integer nextNeeded() {
        int n = shell.lib.albums.size();
        if (n == 0 || maxFrames() == 0) return null;

        // what is on screen at rest
        Integer k;
        if ((k = want(cur - 1, REST, true)) != null) return k;
        if ((k = want(cur + 1, REST, false)) != null) return k;
        if ((k = want(cur - 2, REST, true)) != null) return k;
        if ((k = want(cur + 2, REST, false)) != null) return k;

        // what the next move will step through
        for (int ai = REST; ai >= 1; ai--) {
            if ((k = want(cur, ai, true)) != null) return k;
            if ((k = want(cur, ai, false)) != null) return k;
            if ((k = want(cur - 1, ai, true)) != null) return k;
            if ((k = want(cur + 1, ai, false)) != null) return k;
        }
        return null;
    }

    private Integer want(int idx, int angleIdx, boolean leftSide) {
        if (idx < 0 || idx >= shell.lib.albums.size()) return null;
        Integer k = key(idx, angleIdx, leftSide);
        if (frames.get(k) != null) return null;
        if (shell.art.get(idx, big) == null) return null;   // art not ready yet
        return k;
    }

    private void buildFrame(Integer k) {
        int v = k.intValue();
        boolean leftSide = (v & 1) != 0;
        int rest = v >> 1;
        int angleIdx = rest % ANGLES.length;
        int idx = rest / ANGLES.length;

        Image flat = shell.art.get(idx, big);
        if (flat == null) return;
        try {
            // A cover left of centre angles inward - it is turned to face right,
            // which brings its OUTER (left) edge toward the viewer.
            Image p = Gfx.perspective(flat, ANGLES[angleIdx], !leftSide);
            put(k, p);
            framesBuilt++;
            if (angleIdx == REST) {
                try {
                    mirrors.put(k, Gfx.reflection(p, p.getHeight() / 3));
                } catch (Throwable e) {
                    // a missing reflection is cosmetic
                }
            }
        } catch (OutOfMemoryError oom) {
            frames.clear();
            frameOrder.removeAllElements();
            mirrors.clear();
            lastError = "out of memory";
            System.gc();
        } catch (Throwable e) {
            lastError = Library.describe(e);
        }
    }

    private static Integer key(int idx, int angleIdx, boolean leftSide) {
        return new Integer((idx * ANGLES.length + angleIdx) * 2 + (leftSide ? 1 : 0));
    }

    private void put(Integer k, Image img) {
        frames.put(k, img);
        frameOrder.addElement(k);
        while (frameOrder.size() > maxFrames()) {
            Object old = frameOrder.elementAt(0);
            frameOrder.removeElementAt(0);
            frames.remove(old);
            mirrors.remove(old);
        }
    }

    private void prefetch() {
        int n = shell.lib.albums.size();
        for (int d = 0; d <= WINGS + 1; d++) {
            if (cur - d >= 0) shell.art.get(cur - d, big);
            if (cur + d < n) shell.art.get(cur + d, big);
        }
    }

    /* ---- input ------------------------------------------------------------ */

    public void key(int action, int code) {
        switch (action) {
            case Shell.LEFT:
                if (cur > 0) { cur--; dir = -1; slideStart = System.currentTimeMillis(); prefetch(); wake(); }
                return;
            case Shell.RIGHT:
                if (cur < shell.lib.albums.size() - 1) {
                    cur++; dir = 1; slideStart = System.currentTimeMillis(); prefetch(); wake();
                }
                return;
            case Shell.SELECT:
            case Shell.DOWN: {
                MenuScreen menu = Menus.build(shell, Menus.M_ALBUM, cur);
                Image face = shell.art.get(cur, big);
                if (face != null && shell.settings.transitions) {
                    // turn the case over to show the listing on the back
                    shell.push(new FlipScreen(cur, big, face, menu), false);
                } else {
                    shell.push(menu);
                }
                return;
            }
            case Shell.BACK:
            case Shell.UP:
                shell.pop();
                return;
            default:
                return;
        }
    }

    public boolean tick() {
        return slideStart != 0;
    }
}
