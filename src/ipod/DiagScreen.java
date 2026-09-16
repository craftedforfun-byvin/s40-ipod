package ipod;

import java.util.Vector;
import javax.microedition.lcdui.Graphics;

/**
 * What the last scan actually saw. When the library comes back empty this is
 * the difference between guessing and knowing: it reports whether JSR-75 is
 * there at all, which roots the phone handed back, how many folders were
 * walked, and the first error that got swallowed.
 */
public final class DiagScreen extends UIScreen {

    private Vector lines = new Vector();
    private int scroll;

    public DiagScreen() {
        title = "Diagnostics";
    }

    public void onShow() {
        lines = new Vector();
        Library lib = shell.lib;

        int withArt = 0;
        for (int i = 0; i < lib.albums.size(); i++) {
            if (lib.album(i).artTrack >= 0) withArt++;
        }
        add(Str.cat("Tracks in library: ", Str.num(lib.tracks.size())));
        add(Str.cat("Albums: ", Str.num(lib.albums.size()),
                    "  with artwork: ", Str.num(withArt)));
        if (lib.albums.size() < 2) {
            add("  Cover Flow needs 2+ albums");
        }
        add(Str.cat("Folders walked:    ", Str.num(lib.diagDirs)));
        add(Str.cat("MP3 files seen:    ", Str.num(lib.scanFound)));
        add(Str.cat("Tags read:         ", Str.num(lib.scanTagged)));
        add("");

        Runtime rt = Runtime.getRuntime();
        add("Memory:");
        add(Str.cat("  heap ", Str.num(rt.totalMemory() / 1024L), "K, free ",
                    Str.num(rt.freeMemory() / 1024L), "K"));
        add(Str.cat("  covers too big to decode: ", Str.num(ArtCache.artSkipped)));
        if (ArtCache.lastSkip != null) {
            add(Str.cat("  largest declined: ", ArtCache.lastSkip));
        }
        add("");

        add("Cover Flow:");
        add(Str.cat("  projections built: ", Str.num(CoverFlow.framesBuilt)));
        if (CoverFlow.lastError != null) {
            add(Str.cat("  error: ", CoverFlow.lastError));
        } else if (CoverFlow.framesBuilt == 0) {
            add("  none yet - open Cover Flow first");
        }
        add("");

        add("File API (JSR-75):");
        String jsr = System.getProperty("microedition.io.file.FileConnection.version");
        add(Str.cat("  version ", jsr == null ? "absent" : jsr));
        add("");

        add("Audio:");
        String mm = System.getProperty("microedition.media.version");
        add(Str.cat("  MMAPI ", mm == null ? "absent" : mm));
        add(Str.cat("  volume: ", shell.engine.volumeStatus()));
        add(Str.cat("  app volume keys: ",
                shell.engine.volumeControlled ? "working" : "no effect"));
        add("");

        // Straight from the device, so there is no guessing about what it can
        // decode. Anything not on this list cannot be played by any MIDlet.
        add("Formats this phone can decode:");
        try {
            String[] types = javax.microedition.media.Manager.getSupportedContentTypes(null);
            if (types == null || types.length == 0) {
                add("  (none reported)");
            } else {
                boolean flac = false;
                for (int i = 0; i < types.length; i++) {
                    add(Str.cat("  ", types[i]));
                    if (types[i].toLowerCase().indexOf("flac") >= 0) flac = true;
                }
                add(Str.cat("  FLAC: ", flac ? "supported" : "not supported"));
            }
        } catch (Throwable t) {
            add(Str.cat("  failed: ", Library.describe(t)));
        }
        add("");

        add("Roots reported:");
        boolean any = false;
        try {
            java.util.Enumeration e = javax.microedition.io.file.FileSystemRegistry.listRoots();
            while (e != null && e.hasMoreElements()) {
                add(Str.cat("  ", (String) e.nextElement()));
                any = true;
            }
        } catch (Throwable t) {
            add(Str.cat("  failed: ", Library.describe(t)));
            any = true;
        }
        if (!any) add("  (none)");
        add("");

        if (lib.diag.size() > 0) {
            add("Last scan:");
            for (int i = 0; i < lib.diag.size(); i++) {
                add(Str.cat("  ", (String) lib.diag.elementAt(i)));
            }
            add("");
        }

        if (lib.lastError != null) {
            add("First error:");
            add(Str.cat("  ", lib.lastError));
            add("");
        }

        add("If roots are listed but nothing was");
        add("found, use Settings > Browse Files");
        add("to see the folders as the app sees");
        add("them, and play a file directly.");
    }

    private void add(String s) {
        lines.addElement(s);
    }

    public void paint(Graphics g, int w, int h) {
        g.setColor(Theme.LIST_BG);
        g.fillRect(0, 0, w, h);

        int lineH = Theme.F_TINY.getHeight() + 1;
        int visible = h / lineH;
        if (scroll > lines.size() - visible) scroll = lines.size() - visible;
        if (scroll < 0) scroll = 0;

        g.setFont(Theme.F_TINY);
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            String s = (String) lines.elementAt(scroll + i);
            boolean heading = s.length() > 0 && s.charAt(0) != ' ';
            g.setColor(heading ? Theme.NP_TITLE : Theme.NP_SUB);
            g.setFont(heading ? Theme.F_TINYB : Theme.F_TINY);
            g.drawString(Gfx.fit(s, heading ? Theme.F_TINYB : Theme.F_TINY, w - 8),
                    4, i * lineH, Graphics.TOP | Graphics.LEFT);
        }

        if (lines.size() > visible) {
            int barH = h * visible / lines.size();
            int barY = h * scroll / lines.size();
            g.setColor(0xc8c8c8);
            g.fillRect(w - 3, barY, 2, barH);
        }
    }

    public void key(int action, int code) {
        if (action == Shell.DOWN) scroll++;
        else if (action == Shell.UP) scroll--;
        else if (action == Shell.BACK || action == Shell.LEFT) shell.pop();
    }
}
