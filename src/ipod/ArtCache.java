package ipod;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Album art, decoded off the UI thread. Ask for a cover and you either get it
 * straight away or you get null and a repaint once it has been loaded.
 */
public final class ArtCache implements Runnable {


    private final Library lib;
    private Shell shell;

    private final Hashtable cache = new Hashtable();   // key -> Image
    private final Hashtable failed = new Hashtable();  // key -> Boolean
    private final Vector order = new Vector();         // keys, oldest first

    /** Newest-first request queue, so Cover Flow's centre cover wins the race. */
    private final Vector pending = new Vector();
    private static final int MAX_PENDING = 8;
    private boolean running = true;

    public ArtCache(Library lib) {
        this.lib = lib;
        Thread t = new Thread(this);
        t.start();
    }

    public void attach(Shell s) {
        this.shell = s;
    }

    public void stop() {
        synchronized (this) {
            running = false;
            pending.removeAllElements();
            notify();
        }
    }

    /** Cover for an album at a given pixel size, or null while it loads. */
    public Image get(int albumIdx, int size) {
        if (albumIdx < 0) return null;
        String key = Str.pair(albumIdx, "x", size);
        Object img = cache.get(key);
        if (img != null) {
            touch(key);
            return (Image) img;
        }
        if (failed.get(key) != null) return null;
        synchronized (this) {
            if (!pending.contains(key)) {
                pending.addElement(key);
                while (pending.size() > MAX_PENDING) pending.removeElementAt(0);
            }
            notify();
        }
        return null;
    }

    /** Cover for whatever album a track belongs to. */
    public Image forTrack(Track t, int size) {
        if (t == null) return null;
        return get(t.albumIdx, size);
    }

    public void clear() {
        cache.clear();
        order.removeAllElements();
    }

    public void run() {
        while (true) {
            String key;
            synchronized (this) {
                while (running && pending.size() == 0) {
                    try { wait(); } catch (InterruptedException e) {}
                }
                if (!running) return;
                int last = pending.size() - 1;
                key = (String) pending.elementAt(last);
                pending.removeElementAt(last);
            }
            int x = key.indexOf('x');
            try {
                load(key, Integer.parseInt(key.substring(0, x)),
                          Integer.parseInt(key.substring(x + 1)));
            } catch (Throwable e) {
                failed.put(key, Boolean.TRUE);
            }
        }
    }

    private void load(String key, int albumIdx, int size) {
        if (cache.get(key) != null || failed.get(key) != null) return;
        try {
            Library.Album al = lib.album(albumIdx);
            boolean wanted = (shell == null) || shell.settings.artwork;
            byte[] raw = (wanted && al != null && al.artTrack >= 0)
                    ? Id3.readArt(lib.track(al.artTrack)) : null;

            if (raw != null && !affordable(raw)) {
                raw = null;                       // too big to decode safely
                artSkipped++;
            }
            if (raw == null) {
                // No embedded cover. Draw a stand-in rather than giving up: it
                // keeps every screen working, and - the reason this matters -
                // Cover Flow can only turn a cover it actually has, so without
                // this a library with no artwork stays resolutely flat.
                put(key, placeholder(albumIdx, size));
            } else {
                Image full = Image.createImage(raw, 0, raw.length);
                raw = null;
                Image small = Gfx.scale(full, size, size);
                full = null;
                put(key, small);
            }
        } catch (OutOfMemoryError oom) {
            clear();
            System.gc();
        } catch (Throwable e) {
            try {
                put(key, placeholder(albumIdx, size));
            } catch (Throwable e2) {
                failed.put(key, Boolean.TRUE);
            }
        }
        if (shell != null) shell.refresh();
    }

    /**
     * Will decoding this actually fit? Image.createImage inflates to the
     * source resolution - a 600x600 cover is roughly 1.4 MB - so on a phone
     * with a 2 MB heap one oversized cover can take the lot. We look at the
     * header, work out what the decode would cost, and decline if it would eat
     * more than half of what is free. A generated cover is used instead.
     */
    private boolean affordable(byte[] raw) {
        int dim = Gfx.imageDimensions(raw);
        if (dim == 0) {
            // unrecognised header: judge by the encoded size instead
            return raw.length < 120000;
        }
        int w = (dim >>> 16) & 0xffff;
        int h = dim & 0xffff;
        long needed = (long) w * h * 4L;
        long free = Runtime.getRuntime().freeMemory();
        if (needed > free / 2) {
            lastSkip = Str.cat(Str.num(w), "x", Str.num(h));
            return false;
        }
        return true;
    }

    /** How many covers were too big to decode, and the last one's size. */
    public static volatile int artSkipped;
    public static volatile String lastSkip;

    /**
     * How many covers to keep, scaled to the heap the device actually gave us.
     * A fixed twelve was fine on a roomy VM and far too many on a small one.
     */
    private static int maxEntries() {
        long total = Runtime.getRuntime().totalMemory();
        if (total < 700000L) return 3;
        if (total < 1300000L) return 5;
        if (total < 2200000L) return 8;
        return 12;
    }

    /** Colours for generated covers - picked so neighbours stay distinct. */
    private static final int[] PALETTE = {
        0x3f6fb5, 0xa8434a, 0x3f8a6e, 0x8a5fa8, 0xb57a34, 0x39708a, 0x8a5140, 0x5a6b8a
    };

    /**
     * A stand-in cover for an album with no embedded artwork: a tinted panel
     * carrying the album's initial. Deterministic, so the same album always
     * gets the same colour.
     */
    private Image placeholder(int albumIdx, int size) {
        Library.Album al = lib.album(albumIdx);
        String name = (al != null && al.name != null) ? al.name : "";

        int hash = 0;
        for (int i = 0; i < name.length(); i++) hash = hash * 31 + name.charAt(i);
        if (hash < 0) hash = -hash;
        int top = PALETTE[hash % PALETTE.length];

        Image img = Image.createImage(size, size);
        Graphics g = img.getGraphics();
        Gfx.vgrad(g, 0, 0, size, size, top, shade(top, 45));

        char initial = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c > ' ') { initial = Character.toUpperCase(c); break; }
        }
        if (initial != 0 && size >= 40) {
            Font f = Theme.F_BIG;
            g.setFont(f);
            g.setColor(shade(top, 70));
            g.drawChar(initial, size / 2 + 1, (size - f.getHeight()) / 2 + 1,
                    Graphics.TOP | Graphics.HCENTER);
            g.setColor(0xffffff);
            g.drawChar(initial, size / 2, (size - f.getHeight()) / 2,
                    Graphics.TOP | Graphics.HCENTER);
        } else {
            Gfx.note(g, size / 2, size / 2, size / 3, 0xffffff);
        }
        // Hand back an immutable copy: Gfx.perspective reads it with getRGB,
        // and not every implementation is happy doing that to a mutable image.
        return Image.createImage(img);
    }

    /** Same hue, darker by pct percent. */
    private static int shade(int rgb, int pct) {
        int r = ((rgb >> 16) & 0xff) * (100 - pct) / 100;
        int gg = ((rgb >> 8) & 0xff) * (100 - pct) / 100;
        int b = (rgb & 0xff) * (100 - pct) / 100;
        return (r << 16) | (gg << 8) | b;
    }

    private void put(String key, Image img) {
        cache.put(key, img);
        order.addElement(key);
        while (order.size() > maxEntries()) {
            String old = (String) order.elementAt(0);
            order.removeElementAt(0);
            cache.remove(old);
        }
    }

    private void touch(String key) {
        int i = order.indexOf(key);
        if (i >= 0 && i != order.size() - 1) {
            order.removeElementAt(i);
            order.addElement(key);
        }
    }
}
