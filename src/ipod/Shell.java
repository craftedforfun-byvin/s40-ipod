package ipod;

import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * The canvas everything lives on: status bar, screen stack, sliding
 * transitions, key routing and the Hold lock.
 */
public final class Shell extends Canvas implements Runnable {

    public static final int UP = 1, DOWN = 2, LEFT = 3, RIGHT = 4,
                            SELECT = 5, BACK = 6, MENU = 7, PLAY = 8, NONE = 0;

    private static final int SLIDE_STEPS = 6;

    public final Library lib;
    public final Engine engine;
    public final Settings settings;
    public final ArtCache art;
    public final IPodMIDlet midlet;

    private final Vector stack = new Vector();
    private Image outgoing;
    private int slideDir;
    private int slideStep;

    public boolean hold;
    private long holdSince;
    private boolean lightsOff;

    private volatile boolean alive = true;
    private volatile boolean dirty = true;

    public final int barH;

    public Shell(IPodMIDlet midlet, Library lib, Engine engine, Settings settings, ArtCache art) {
        this.midlet = midlet;
        this.lib = lib;
        this.engine = engine;
        this.settings = settings;
        this.art = art;
        setFullScreenMode(true);
        int h = Theme.F_TINYB.getHeight() + 6;
        barH = h < 18 ? 18 : h;
        new Thread(this).start();
    }

    /* ---- stack ---------------------------------------------------------- */

    public UIScreen top() {
        if (stack.size() == 0) return null;
        return (UIScreen) stack.elementAt(stack.size() - 1);
    }

    public void push(UIScreen s) {
        push(s, settings.transitions);
    }

    /** Push without the slide - for screens that animate themselves. */
    public void push(UIScreen s, boolean animate) {
        s.bind(this);
        UIScreen old = top();
        if (old != null) {
            old.onHide();
            if (animate) startSlide(1);
        }
        stack.addElement(s);
        s.onShow();
        refresh();
    }

    /** Swap the top screen for another, leaving what is under it alone. */
    public void replaceTop(UIScreen s) {
        if (stack.size() == 0) {
            root(s);
            return;
        }
        top().onHide();
        stack.removeElementAt(stack.size() - 1);
        s.bind(this);
        stack.addElement(s);
        s.onShow();
        refresh();
    }

    public void pop() {
        if (stack.size() <= 1) return;
        UIScreen old = top();
        old.onHide();
        if (settings.transitions) startSlide(-1);
        stack.removeElementAt(stack.size() - 1);
        top().onShow();
        refresh();
    }

    /** Replace the whole stack - used by the Menu key. */
    public void root(UIScreen s) {
        if (stack.size() > 1 && settings.transitions) startSlide(-1);
        stack.removeAllElements();
        s.bind(this);
        stack.addElement(s);
        s.onShow();
        refresh();
    }

    /** Drop back to the root without rebuilding it. */
    public void popToRoot() {
        if (stack.size() <= 1) return;
        if (settings.transitions) startSlide(-1);
        while (stack.size() > 1) stack.removeElementAt(stack.size() - 1);
        top().onShow();
        refresh();
    }

    private void startSlide(int dir) {
        try {
            int w = getWidth(), h = getHeight();
            if (outgoing == null || outgoing.getWidth() != w || outgoing.getHeight() != h) {
                outgoing = Image.createImage(w, h);
            }
            Graphics g = outgoing.getGraphics();
            g.setClip(0, 0, w, h);
            paintFrame(g, w, h);
            slideDir = dir;
            slideStep = SLIDE_STEPS;
        } catch (Throwable e) {
            outgoing = null;          // not enough heap for transitions
            slideStep = 0;
            settings.transitions = false;
        }
    }

    /* ---- painting -------------------------------------------------------- */

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        try {
            if (hold) {
                paintHold(g, w, h);
                return;
            }
            if (slideStep > 0 && outgoing != null) {
                int off = slideDir * w * slideStep / SLIDE_STEPS;
                g.drawImage(outgoing, off - slideDir * w, 0, Graphics.TOP | Graphics.LEFT);
                g.translate(off, 0);
                paintFrame(g, w, h);
                g.translate(-off, 0);
            } else {
                paintFrame(g, w, h);
            }
        } catch (Throwable e) {
            g.setColor(0xffffff);
            g.fillRect(0, 0, w, h);
            g.setColor(0x000000);
            g.setFont(Theme.F_TINY);
            g.drawString("UI error", 6, 6, Graphics.TOP | Graphics.LEFT);
        }
    }

    private void paintFrame(Graphics g, int w, int h) {
        UIScreen s = top();
        String t = s == null ? "iPod" : s.title();
        statusBar(g, w, t);
        if (s == null) return;
        g.translate(0, barH);
        g.setClip(0, 0, w, h - barH);
        s.paint(g, w, h - barH);
        g.setClip(0, -barH, w, h);
        g.translate(0, -barH);
    }

    private void statusBar(Graphics g, int w, String title) {
        Gfx.vgrad(g, 0, 0, w, barH - 1, Theme.BAR_TOP, Theme.BAR_BOTTOM);
        g.setColor(Theme.BAR_LINE);
        g.drawLine(0, barH - 1, w, barH - 1);

        g.setFont(Theme.F_TINYB);
        g.setColor(Theme.BAR_TEXT);
        int ty = (barH - 1 - Theme.F_TINYB.getHeight()) / 2;
        String fitted = Gfx.fit(title, Theme.F_TINYB, w - 76);
        g.drawString(fitted, w / 2, ty, Graphics.TOP | Graphics.HCENTER);

        if (engine.hasTrack()) {
            Gfx.transport(g, 6, (barH - 10) / 2, engine.playing, Theme.BAR_TEXT);
        }
        Gfx.battery(g, w - 26, (barH - 10) / 2, battery());
    }

    private void paintHold(Graphics g, int w, int h) {
        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);
        Track t = engine.current();
        int cy = h / 2;
        padlock(g, w / 2 - 9, cy - 62, 0xffffff);
        g.setFont(Theme.F_BODYB);
        g.setColor(0xffffff);
        g.drawString("Hold", w / 2, cy - 24, Graphics.TOP | Graphics.HCENTER);
        g.setFont(Theme.F_TINY);
        g.setColor(0x9a9a9a);
        g.drawString("press H to unlock", w / 2, cy - 4, Graphics.TOP | Graphics.HCENTER);
        if (t != null) {
            g.setFont(Theme.F_TINYB);
            g.setColor(0xdddddd);
            g.drawString(Gfx.fit(t.displayTitle(), Theme.F_TINYB, w - 20), w / 2, cy + 30,
                    Graphics.TOP | Graphics.HCENTER);
            g.setFont(Theme.F_TINY);
            g.setColor(0x8a8a8a);
            g.drawString(Gfx.fit(t.displayArtist(), Theme.F_TINY, w - 20), w / 2, cy + 46,
                    Graphics.TOP | Graphics.HCENTER);
        }
    }

    private void padlock(Graphics g, int x, int y, int colour) {
        g.setColor(colour);
        g.drawArc(x + 3, y, 12, 16, 0, 180);
        g.drawArc(x + 4, y + 1, 10, 14, 0, 180);
        g.fillRect(x, y + 8, 18, 13);
        g.setColor(0x000000);
        g.fillRect(x + 8, y + 12, 2, 5);
    }

    private int battery() {
        try {
            String v = System.getProperty("com.nokia.mid.batterylevel");
            if (v != null) {
                int n = Integer.parseInt(v.trim());
                if (n >= 0 && n <= 100) return n;
            }
        } catch (Throwable e) {}
        return 100;
    }

    /* ---- input ------------------------------------------------------------ */

    protected void keyPressed(int code) {
        route(code, false);
    }

    protected void keyRepeated(int code) {
        route(code, true);
    }

    private void route(int code, boolean repeat) {
        if (hold) {
            if (code == 104 || code == 72) setHold(false);   // h / H
            return;
        }
        int action = map(code);

        // global shortcuts, available from any screen
        if (!repeat) {
            if (code == 104 || code == 72) { setHold(true); return; }
            if (code == 32 || code == 112 || code == 80) { engine.toggle(); refresh(); return; }
            if (code == 109 || code == 77) { openNowPlaying(); return; }
            if (action == MENU) { popToRoot(); return; }
        }

        UIScreen s = top();
        if (s == null) return;
        if (repeat) s.keyRepeat(action, code);
        else s.key(action, code);
        refresh();
    }

    private int map(int code) {
        switch (code) {
            case -1: return UP;
            case -2: return DOWN;
            case -3: return LEFT;
            case -4: return RIGHT;
            case -5: return SELECT;
            case -6: return MENU;
            case -7: return BACK;
            case -8: return BACK;
            case  8: return BACK;
            case 10: return SELECT;
            case 98: case 66: return BACK;             // b / B
            default: break;
        }
        int ga = 0;
        try { ga = getGameAction(code); } catch (Throwable e) {}
        if (ga == Canvas.UP) return UP;
        if (ga == Canvas.DOWN) return DOWN;
        if (ga == Canvas.LEFT) return LEFT;
        if (ga == Canvas.RIGHT) return RIGHT;
        if (ga == Canvas.FIRE) return SELECT;
        return NONE;
    }

    public void openNowPlaying() {
        if (!engine.hasTrack()) return;
        if (top() instanceof NowPlaying) return;
        push(new NowPlaying());
    }

    public void setHold(boolean on) {
        hold = on;
        holdSince = System.currentTimeMillis();
        if (!on) Backlight.on();
        lightsOff = false;
        refresh();
    }

    /* ---- frame loop --------------------------------------------------------- */

    public void refresh() {
        dirty = true;
    }

    public void run() {
        while (alive) {
            boolean busy = slideStep > 0;
            UIScreen s = top();
            if (s != null && !hold && s.tick()) busy = true;
            if (slideStep > 0) {
                slideStep--;
                if (slideStep == 0) outgoing = null;
                dirty = true;
            }
            if (hold && !lightsOff && System.currentTimeMillis() - holdSince > 3000L) {
                Backlight.off();
                lightsOff = true;
            }
            if (dirty || busy) {
                dirty = false;
                repaint();
                serviceRepaints();
            }
            try {
                // ~25 fps while something is moving, idle otherwise
                Thread.sleep(busy ? 40L : 180L);
            } catch (InterruptedException e) {}
        }
    }

    public void shutdown() {
        alive = false;
    }

    protected void sizeChanged(int w, int h) {
        outgoing = null;
        refresh();
    }
}
