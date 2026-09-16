package ipod;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

/** Walks the phone and the memory card for MP3s, reads their tags, groups them. */
public final class Library {

    public static final int MAX_TRACKS = 1500;
    private static final int MAX_DEPTH = 8;

    public Vector tracks  = new Vector();   // Track
    public Vector albums  = new Vector();   // Album
    public Vector artists = new Vector();   // Artist

    public boolean ready;
    public volatile boolean scanning;
    public volatile int  scanFound;
    public volatile int  scanTagged;
    public volatile String scanWhere = "";

    /** What the last scan actually saw - shown on the Diagnostics screen. */
    public Vector diag = new Vector();
    public volatile int diagDirs;
    public volatile String lastError;

    public static final class Album {
        public String name;
        public String artist;
        public Vector trackIdx = new Vector();
        public int artTrack = -1;           // index of a track that carries the cover
    }

    public static final class Artist {
        public String name;
        public Vector albumIdx = new Vector();
        public Vector trackIdx = new Vector();
    }

    /* ---- scanning ----------------------------------------------------- */

    public void scan() {
        scanning = true;
        scanFound = 0;
        scanTagged = 0;
        Vector files = new Vector();
        diag = new Vector();
        diagDirs = 0;
        lastError = null;
        try {
            Enumeration roots = FileSystemRegistry.listRoots();
            if (roots == null || !roots.hasMoreElements()) {
                note("listRoots: empty");
            }
            while (roots != null && roots.hasMoreElements()) {
                String root = (String) roots.nextElement();
                int before = files.size();
                collect(rootUrl(root), 0, files);
                note(Str.cat(root, " -> ", Str.num(files.size() - before), " mp3"));
                if (files.size() >= MAX_TRACKS) break;
            }
        } catch (Throwable e) {
            lastError = describe(e);
            note(Str.cat("listRoots failed: ", lastError));
        }

        Vector found = new Vector();
        for (int i = 0; i < files.size(); i++) {
            String url = (String) files.elementAt(i);
            scanWhere = shortName(url);
            Track t = Id3.read(url);
            found.addElement(t);
            scanTagged = i + 1;
        }

        tracks = found;
        group();
        ready = true;
        scanning = false;
        scanWhere = "";
    }

    private void collect(String dirUrl, int depth, Vector out) {
        if (depth > MAX_DEPTH || out.size() >= MAX_TRACKS) return;
        FileConnection dir = null;
        try {
            dir = (FileConnection) Connector.open(dirUrl, Connector.READ);
            if (!dir.isDirectory()) return;
            diagDirs++;
            scanWhere = shortName(dirUrl);
            // include hidden entries - Series 40 flags some user folders hidden
            Enumeration e = dir.list("*", true);
            Vector subdirs = new Vector();
            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();
                if (name.length() == 0 || name.charAt(0) == '.') continue;
                if (name.charAt(name.length() - 1) == '/') {
                    if (!skipDir(name)) subdirs.addElement(name);
                } else if (isMp3(name)) {
                    out.addElement(Str.cat(dirUrl, name));
                    scanFound = out.size();
                    if (out.size() >= MAX_TRACKS) break;
                }
            }
            try { dir.close(); } catch (Exception ex) {}
            dir = null;
            for (int i = 0; i < subdirs.size() && out.size() < MAX_TRACKS; i++) {
                collect(Str.cat(dirUrl, (String) subdirs.elementAt(i)), depth + 1, out);
            }
        } catch (Throwable ex) {
            // unreadable folder (permissions, system area) - note it and move on
            if (lastError == null) lastError = Str.cat(shortName(dirUrl), ": ", describe(ex));
            if (depth <= 1) note(Str.cat(shortName(dirUrl), " x ", describe(ex)));
        } finally {
            if (dir != null) try { dir.close(); } catch (Exception ex) {}
        }
    }

    /** file:///E:/ from whatever shape listRoots handed back. */
    public static String rootUrl(String root) {
        String r = root;
        if (r.length() > 0 && r.charAt(r.length() - 1) != '/') r = Str.cat(r, "/");
        if (r.length() > 8 && r.substring(0, 8).equals("file:///")) return r;
        return Str.cat("file:///", r);
    }

    private void note(String line) {
        if (diag.size() < 24) diag.addElement(line);
    }

    /** Exception class and message, short enough for a phone screen. */
    public static String describe(Throwable t) {
        if (t == null) return "null";
        String cls = t.getClass().getName();
        int dot = cls.lastIndexOf('.');
        if (dot >= 0) cls = cls.substring(dot + 1);
        String msg = null;
        try { msg = t.getMessage(); } catch (Throwable e) {}
        if (msg == null || msg.length() == 0) return cls;
        if (msg.length() > 40) msg = msg.substring(0, 40);
        return Str.cat(cls, ": ", msg);
    }

    /** Append a file we were handed directly, outside the scanned library. */
    public int addAdHoc(Track t) {
        for (int i = 0; i < tracks.size(); i++) {
            if (t.url.equals(track(i).url)) return i;
        }
        tracks.addElement(t);
        return tracks.size() - 1;
    }

    private static boolean isMp3(String name) {
        int n = name.length();
        if (n < 5) return false;
        return name.substring(n - 4).toLowerCase().equals(".mp3");
    }

    /**
     * Only skip what is genuinely off limits. An earlier version also skipped
     * "predefgallery/", which was a mistake: on Series 40 that is exactly where
     * phone-memory Music, Images and Videos live, so the scan walked straight
     * past the user's music. When in doubt, descend - an unreadable folder
     * throws and is handled, which costs nothing.
     */
    private static boolean skipDir(String name) {
        String d = name.toLowerCase();
        return d.equals("system/") || d.equals("private/") || d.equals("sys/");
    }

    private static String shortName(String url) {
        String s = url;
        if (s.length() > 0 && s.charAt(s.length() - 1) == '/') s = s.substring(0, s.length() - 1);
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    /* ---- grouping ------------------------------------------------------ */

    public void group() {
        albums = new Vector();
        artists = new Vector();
        Hashtable albumMap = new Hashtable();
        Hashtable artistMap = new Hashtable();

        Sort.sort(tracks, new Sort.Cmp() {
            public int compare(Object a, Object b) {
                Track x = (Track) a, y = (Track) b;
                int c = Sort.name(x.displayArtist(), y.displayArtist());
                if (c != 0) return c;
                c = Sort.name(x.displayAlbum(), y.displayAlbum());
                if (c != 0) return c;
                if (x.trackNo != y.trackNo) return x.trackNo - y.trackNo;
                return Sort.name(x.displayTitle(), y.displayTitle());
            }
        });

        for (int i = 0; i < tracks.size(); i++) {
            Track t = (Track) tracks.elementAt(i);
            String ar = t.displayArtist();
            String al = t.displayAlbum();

            Artist artist = (Artist) artistMap.get(ar.toLowerCase());
            if (artist == null) {
                artist = new Artist();
                artist.name = ar;
                artistMap.put(ar.toLowerCase(), artist);
                artists.addElement(artist);
            }
            artist.trackIdx.addElement(new Integer(i));

            String key = Str.cat(al, "/", ar).toLowerCase();
            Album album = (Album) albumMap.get(key);
            if (album == null) {
                album = new Album();
                album.name = al;
                album.artist = ar;
                albumMap.put(key, album);
                albums.addElement(album);
                artist.albumIdx.addElement(new Integer(albums.size() - 1));
            }
            album.trackIdx.addElement(new Integer(i));
            if (album.artTrack < 0 && t.hasArt()) album.artTrack = i;
        }

        Sort.sort(albums, new Sort.Cmp() {
            public int compare(Object a, Object b) {
                Album x = (Album) a, y = (Album) b;
                int c = Sort.name(x.name, y.name);
                return c != 0 ? c : Sort.name(x.artist, y.artist);
            }
        });
        Sort.sort(artists, new Sort.Cmp() {
            public int compare(Object a, Object b) {
                return Sort.name(((Artist) a).name, ((Artist) b).name);
            }
        });

        // album order changed, so rebuild the track -> album links
        for (int i = 0; i < albums.size(); i++) {
            Album al = (Album) albums.elementAt(i);
            for (int j = 0; j < al.trackIdx.size(); j++) {
                track(index(al.trackIdx, j)).albumIdx = i;
            }
        }
        for (int i = 0; i < artists.size(); i++) {
            Artist ar = (Artist) artists.elementAt(i);
            ar.albumIdx = new Vector();
            for (int j = 0; j < albums.size(); j++) {
                Album al = (Album) albums.elementAt(j);
                if (al.artist.toLowerCase().equals(ar.name.toLowerCase())) {
                    ar.albumIdx.addElement(new Integer(j));
                }
            }
        }
    }

    /* ---- accessors ----------------------------------------------------- */

    public Track track(int i) {
        if (i < 0 || i >= tracks.size()) return null;
        return (Track) tracks.elementAt(i);
    }

    public Album album(int i) {
        if (i < 0 || i >= albums.size()) return null;
        return (Album) albums.elementAt(i);
    }

    public Artist artist(int i) {
        if (i < 0 || i >= artists.size()) return null;
        return (Artist) artists.elementAt(i);
    }

    public static int index(Vector v, int i) {
        return ((Integer) v.elementAt(i)).intValue();
    }

    /** Every track index, in the library's own order. */
    public Vector allTrackIndices() {
        Vector v = new Vector(tracks.size());
        for (int i = 0; i < tracks.size(); i++) v.addElement(new Integer(i));
        return v;
    }

    /** Songs menu order: alphabetical by title. */
    public Vector songOrder() {
        Vector v = allTrackIndices();
        final Library self = this;
        Sort.sort(v, new Sort.Cmp() {
            public int compare(Object a, Object b) {
                Track x = self.track(((Integer) a).intValue());
                Track y = self.track(((Integer) b).intValue());
                return Sort.name(x.displayTitle(), y.displayTitle());
            }
        });
        return v;
    }
}
