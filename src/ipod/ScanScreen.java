package ipod;

import javax.microedition.lcdui.Graphics;

/** "Updating Library" - walks the card, reads tags, then hands back to the menu. */
public final class ScanScreen extends UIScreen implements Runnable {

    private boolean started;
    private boolean done;
    private int spin;

    public ScanScreen() {
        title = "Updating Library";
    }

    public void onShow() {
        if (started) return;
        started = true;
        new Thread(this).start();
    }

    public void run() {
        try {
            shell.art.clear();
            shell.lib.scan();
            Store.saveLibrary(shell.lib);
        } catch (Throwable e) {
            // leave whatever was found
        }
        done = true;
        shell.refresh();
    }

    public void paint(Graphics g, int w, int h) {
        g.setColor(Theme.LIST_BG);
        g.fillRect(0, 0, w, h);

        Library lib = shell.lib;
        int cy = h / 2;

        g.setFont(Theme.F_BODYB);
        g.setColor(Theme.NP_TITLE);
        String head = done ? "Library Updated" : "Updating Library";
        g.drawString(head, w / 2, cy - 62, Graphics.TOP | Graphics.HCENTER);

        int bw = w - 60;
        int max = lib.scanFound > 0 ? lib.scanFound : 1;
        int val = done ? max : lib.scanTagged;
        ScrubBar.draw(g, 30, cy - 30, bw, 10, val, max, false);

        g.setFont(Theme.F_TINY);
        g.setColor(Theme.NP_SUB);
        String line;
        if (done) {
            line = Str.cat(Str.num(lib.tracks.size()), " songs, ", Str.num(lib.albums.size()), " albums");
        } else if (lib.scanTagged > 0) {
            line = Str.cat("Reading tags ", Str.num(lib.scanTagged), " of ", Str.num(lib.scanFound));
        } else {
            line = Str.cat("Looking for music", dots());
        }
        g.drawString(line, w / 2, cy - 10, Graphics.TOP | Graphics.HCENTER);

        if (!done) {
            g.setColor(0x9a9a9a);
            String where = lib.scanWhere;
            if (where != null && where.length() > 0) {
                g.drawString(Gfx.fit(where, Theme.F_TINY, w - 24), w / 2, cy + 8,
                        Graphics.TOP | Graphics.HCENTER);
            }
        } else {
            g.setColor(0x9a9a9a);
            g.drawString("Press any key", w / 2, cy + 14, Graphics.TOP | Graphics.HCENTER);
        }

        Gfx.note(g, w / 2, cy + 62, 26, 0xc2c8d0);
    }

    private String dots() {
        int n = (spin / 4) % 4;
        StringBuffer b = new StringBuffer();
        for (int i = 0; i < n; i++) b.append('.');
        return b.toString();
    }

    public void key(int action, int code) {
        if (!done) {
            if (action == Shell.BACK) shell.pop();
            return;
        }
        shell.root(Menus.build(shell, Menus.M_ROOT, 0));
    }

    public boolean tick() {
        spin++;
        return !done;
    }
}
