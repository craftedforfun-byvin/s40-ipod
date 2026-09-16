package ipod;

import java.util.Vector;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** The iPod classic split view: list on the left, preview pane on the right. */
public final class MenuScreen extends UIScreen {

    public Vector items = new Vector();
    /** When this menu is a track listing, the queue that selecting a row plays. */
    public Vector queue;

    private int sel;
    private int scroll;
    private int rowH;
    private int marquee;
    private int marqueePause;

    public MenuScreen(String title) {
        this.title = title;
        rowH = Theme.F_TINYB.getHeight() + 9;
        if (rowH < 20) rowH = 20;
    }

    public MenuScreen add(MenuItem it) {
        items.addElement(it);
        return this;
    }

    public int selected() {
        return sel;
    }

    public void select(int i) {
        if (i >= 0 && i < items.size()) {
            sel = i;
            resetMarquee();
        }
    }

    private MenuItem item(int i) {
        return (MenuItem) items.elementAt(i);
    }

    /* ---- painting -------------------------------------------------------- */

    public void paint(Graphics g, int w, int h) {
        int listW = w * 53 / 100;
        if (listW < 110) listW = 110;

        g.setColor(Theme.LIST_BG);
        g.fillRect(0, 0, listW, h);
        Gfx.vgrad(g, listW + 1, 0, w - listW - 1, h, Theme.PANE_TOP, Theme.PANE_BOTTOM);
        g.setColor(Theme.PANE_DIV);
        g.drawLine(listW, 0, listW, h);

        int visible = h / rowH;
        if (visible < 1) visible = 1;
        if (sel < scroll) scroll = sel;
        if (sel >= scroll + visible) scroll = sel - visible + 1;
        if (scroll > items.size() - visible) scroll = items.size() - visible;
        if (scroll < 0) scroll = 0;

        for (int i = 0; i < visible && scroll + i < items.size(); i++) {
            paintRow(g, item(scroll + i), scroll + i == sel, 0, i * rowH, listW);
        }

        paintPane(g, listW + 1, 0, w - listW - 1, h);
    }

    private void paintRow(Graphics g, MenuItem it, boolean on, int x, int y, int w) {
        Font f = Theme.F_TINYB;
        int ty = y + (rowH - f.getHeight()) / 2;
        int textRight = w - (it.submenu ? 14 : 6);

        if (on) {
            Gfx.selectionBar(g, x, y, w, rowH);
            g.setColor(Theme.SEL_TEXT);
        } else {
            g.setColor(Theme.LIST_RULE);
            g.drawLine(x + 4, y + rowH - 1, x + w - 4, y + rowH - 1);
            g.setColor(Theme.LIST_TEXT);
        }

        int valW = 0;
        if (it.value != null) {
            valW = Theme.F_TINY.stringWidth(it.value) + 8;
            g.setFont(Theme.F_TINY);
            g.setColor(on ? Theme.SEL_SUBTEXT : Theme.LIST_DIM);
            g.drawString(it.value, x + textRight, ty + 1, Graphics.TOP | Graphics.RIGHT);
            g.setColor(on ? Theme.SEL_TEXT : Theme.LIST_TEXT);
        }

        g.setFont(f);
        int avail = textRight - (x + 6) - valW;
        int labelW = f.stringWidth(it.label);
        if (on && labelW > avail) {
            // slide the selected label along, iPod style
            int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
            g.clipRect(x + 6, y, avail, rowH);
            g.drawString(it.label, x + 6 - marquee, ty, Graphics.TOP | Graphics.LEFT);
            g.setClip(cx, cy, cw, ch);
        } else {
            g.drawString(Gfx.fit(it.label, f, avail), x + 6, ty, Graphics.TOP | Graphics.LEFT);
        }

        if (it.submenu) {
            Gfx.chevron(g, x + w - 11, y + rowH / 2, on ? Theme.SEL_TEXT : Theme.CHEVRON);
        }
    }

    /* ---- preview pane ------------------------------------------------------ */

    private void paintPane(Graphics g, int x, int y, int w, int h) {
        MenuItem it = items.size() > 0 ? item(sel) : null;
        int artSize = w - 26;
        if (artSize > 92) artSize = 92;
        int ax = x + (w - artSize) / 2;

        int albumIdx = -1;
        String line1 = null, line2 = null;
        if (it != null && it.albumIdx >= 0) {
            albumIdx = it.albumIdx;
            Library.Album al = shell.lib.album(albumIdx);
            if (al != null) { line1 = al.name; line2 = al.artist; }
        } else if (it != null && it.trackIdx >= 0) {
            Track t = shell.lib.track(it.trackIdx);
            if (t != null) { albumIdx = t.albumIdx; line1 = t.displayAlbum(); line2 = t.displayArtist(); }
        }

        if (albumIdx >= 0) {
            int ay = y + 42;
            Image img = shell.art.get(albumIdx, artSize);
            Gfx.art(g, img, ax, ay, artSize);
            g.setFont(Theme.F_TINYB);
            g.setColor(Theme.NP_TITLE);
            g.drawString(Gfx.fit(line1, Theme.F_TINYB, w - 8), x + w / 2, ay + artSize + 10,
                    Graphics.TOP | Graphics.HCENTER);
            g.setFont(Theme.F_TINY);
            g.setColor(Theme.NP_SUB);
            g.drawString(Gfx.fit(line2, Theme.F_TINY, w - 8), x + w / 2, ay + artSize + 10 + Theme.F_TINYB.getHeight() + 2,
                    Graphics.TOP | Graphics.HCENTER);
            return;
        }

        Track cur = shell.engine.current();
        if (cur != null) {
            int ay = y + 34;
            int s = artSize - 12;
            Image img = shell.art.forTrack(cur, s);
            Gfx.art(g, img, x + (w - s) / 2, ay, s);
            g.setFont(Theme.F_TINY);
            g.setColor(Theme.NP_COUNT);
            g.drawString("Now Playing", x + w / 2, y + 12, Graphics.TOP | Graphics.HCENTER);
            g.setFont(Theme.F_TINYB);
            g.setColor(Theme.NP_TITLE);
            g.drawString(Gfx.fit(cur.displayTitle(), Theme.F_TINYB, w - 8), x + w / 2, ay + s + 8,
                    Graphics.TOP | Graphics.HCENTER);
            g.setFont(Theme.F_TINY);
            g.setColor(Theme.NP_SUB);
            g.drawString(Gfx.fit(cur.displayArtist(), Theme.F_TINY, w - 8), x + w / 2,
                    ay + s + 8 + Theme.F_TINYB.getHeight() + 2, Graphics.TOP | Graphics.HCENTER);

            long d = shell.engine.duration();
            if (d > 0) {
                int bw = w - 24;
                int bx = x + 12;
                int by = ay + s + 8 + Theme.F_TINYB.getHeight() + Theme.F_TINY.getHeight() + 10;
                ScrubBar.draw(g, bx, by, bw, 7, shell.engine.position(), d, false);
            }
            return;
        }

        // nothing selected, nothing playing - the idle splash
        g.setColor(0x9aa3ad);
        Gfx.note(g, x + w / 2, y + h / 2 - 14, 26, 0xaeb6c0);
        g.setFont(Theme.F_TINYB);
        g.setColor(0x8d949d);
        g.drawString("iPod", x + w / 2, y + h / 2 + 18, Graphics.TOP | Graphics.HCENTER);
    }

    /* ---- input ------------------------------------------------------------- */

    public void key(int action, int code) {
        if (items.size() == 0) {
            if (action == Shell.LEFT || action == Shell.BACK) shell.pop();
            return;
        }
        switch (action) {
            case Shell.UP:
                if (sel > 0) { sel--; resetMarquee(); }
                return;
            case Shell.DOWN:
                if (sel < items.size() - 1) { sel++; resetMarquee(); }
                return;
            case Shell.LEFT:
            case Shell.BACK:
                shell.pop();
                return;
            case Shell.RIGHT:
            case Shell.SELECT:
                Menus.activate(shell, this, item(sel));
                return;
            default:
                break;
        }
        jumpToLetter(code);
    }

    /** QWERTY shortcut: type a letter to jump down the list. */
    private void jumpToLetter(int code) {
        if (code < 32 || code > 126) return;
        char c = Character.toLowerCase((char) code);
        for (int i = 1; i <= items.size(); i++) {
            int idx = (sel + i) % items.size();
            String l = item(idx).label;
            if (l.length() > 0 && Character.toLowerCase(l.charAt(0)) == c) {
                sel = idx;
                resetMarquee();
                return;
            }
        }
    }

    private void resetMarquee() {
        marquee = 0;
        marqueePause = 12;
    }

    public boolean tick() {
        if (items.size() == 0) return false;
        MenuItem it = item(sel);
        int listW = 126;
        int avail = listW - 26;
        int over = Theme.F_TINYB.stringWidth(it.label) - avail;
        if (over <= 0) {
            if (marquee != 0) { marquee = 0; return true; }
            return false;
        }
        if (marqueePause > 0) { marqueePause--; return marqueePause == 0; }
        marquee += 2;
        if (marquee > over + 16) { marquee = 0; marqueePause = 14; }
        return true;
    }
}
