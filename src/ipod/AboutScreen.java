package ipod;

import javax.microedition.lcdui.Graphics;

public final class AboutScreen extends UIScreen {

    public AboutScreen() {
        title = "About";
    }

    private static final String[] KEYS = {
        "Up / Down    scroll",
        "Right / OK   select",
        "Left / Back  go back",
        "Space        play / pause",
        "H            hold (lock)",
        "M            now playing",
        "A            add to On-The-Go",
        "A-Z          jump in a list"
    };

    public void paint(Graphics g, int w, int h) {
        g.setColor(Theme.LIST_BG);
        g.fillRect(0, 0, w, h);

        long free = Runtime.getRuntime().freeMemory() / 1024L;
        long total = Runtime.getRuntime().totalMemory() / 1024L;

        // On a landscape screen the stats and the key map sit side by side;
        // stacked they would run off the bottom.
        boolean twoUp = w >= h;
        int colW = twoUp ? w / 2 - 12 : w;

        int y = 10;
        g.setFont(Theme.F_BIG);
        g.setColor(Theme.NP_TITLE);
        g.drawString("iPod", twoUp ? 10 + colW / 2 : w / 2, y, Graphics.TOP | Graphics.HCENTER);
        y += Theme.F_BIG.getHeight() + 8;

        int statsLeft = twoUp ? 10 : 10;
        int statsRight = twoUp ? 10 + colW : w - 10;
        y = row(g, statsLeft, statsRight, y, "Songs", String.valueOf(shell.lib.tracks.size()));
        y = row(g, statsLeft, statsRight, y, "Albums", String.valueOf(shell.lib.albums.size()));
        y = row(g, statsLeft, statsRight, y, "Artists", String.valueOf(shell.lib.artists.size()));
        y = row(g, statsLeft, statsRight, y, "On-The-Go", String.valueOf(shell.engine.onTheGo.size()));
        y = row(g, statsLeft, statsRight, y, "Memory", Str.cat(Str.num(free), "K / ", Str.num(total), "K"));
        y = row(g, statsLeft, statsRight, y, "Version", IPodMIDlet.VERSION);

        int kx = twoUp ? w / 2 + 8 : 10;
        int ky = twoUp ? 10 : y + 10;
        if (twoUp) {
            g.setColor(Theme.LIST_RULE);
            g.drawLine(w / 2, 8, w / 2, h - 8);
            g.setFont(Theme.F_TINYB);
            g.setColor(Theme.NP_COUNT);
            g.drawString("Keys", kx, ky, Graphics.TOP | Graphics.LEFT);
            ky += Theme.F_TINYB.getHeight() + 4;
        }
        g.setFont(Theme.F_TINY);
        g.setColor(Theme.NP_SUB);
        for (int i = 0; i < KEYS.length && ky < h - 12; i++) {
            g.drawString(KEYS[i], kx, ky, Graphics.TOP | Graphics.LEFT);
            ky += Theme.F_TINY.getHeight() + 1;
        }
    }

    private int row(Graphics g, int left, int right, int y, String label, String value) {
        g.setFont(Theme.F_TINYB);
        g.setColor(Theme.NP_TITLE);
        g.drawString(label, left, y, Graphics.TOP | Graphics.LEFT);
        g.setFont(Theme.F_TINY);
        g.setColor(Theme.NP_SUB);
        g.drawString(value, right, y, Graphics.TOP | Graphics.RIGHT);
        int nh = Theme.F_TINYB.getHeight() + 4;
        g.setColor(Theme.LIST_RULE);
        g.drawLine(left - 2, y + nh - 2, right, y + nh - 2);
        return y + nh;
    }

    public void key(int action, int code) {
        if (action == Shell.BACK || action == Shell.LEFT) shell.pop();
    }
}
