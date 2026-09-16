package ipod;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Vector;

/**
 * Desktop smoke test for the parts of the MIDlet that have no UI: ID3 reading,
 * library grouping, sorting and string joining. Run with the test doubles in
 * test/ ahead of stubs/ on the classpath.
 *
 *   java -cp build/classes;build/test;build/stubs ipod.Harness
 */
public final class Harness {

    static int pass, fail;

    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "ipodtest");
        dir.mkdirs();

        id3v23(dir);
        id3v23Utf16(dir);
        id3v22(dir);
        id3v1Only(dir);
        noTagAtAll(dir);
        scanning(dir);
        grouping();
        sorting();
        strings();

        System.out.println();
        System.out.println("passed " + pass + ", failed " + fail);
        if (fail > 0) System.exit(1);
    }

    /* ---------------- ID3v2.3 with embedded art ---------------- */

    static void id3v23(File dir) throws Exception {
        byte[] art = fakePng(400);
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        frame23(tag, "TIT2", textFrame(0, "Low Tide Signal"));
        frame23(tag, "TPE1", textFrame(0, "Blue Harbour"));
        frame23(tag, "TALB", textFrame(0, "Lantern Weather"));
        frame23(tag, "TRCK", textFrame(0, "4/10"));
        frame23(tag, "APIC", apicFrame(art));
        byte[] body = tag.toByteArray();

        File f = write(dir, "v23.mp3", id3Header(3, body.length + 200), body, new byte[200], mp3Frames());
        Track t = Id3.read(url(f));

        eq("v2.3 title",  "Low Tide Signal", t.title);
        eq("v2.3 artist", "Blue Harbour",    t.artist);
        eq("v2.3 album",  "Lantern Weather", t.album);
        eq("v2.3 track",  "4",               String.valueOf(t.trackNo));
        eq("v2.3 artLen", String.valueOf(art.length), String.valueOf(t.artLen));

        byte[] got = Id3.readArt(t);
        eq("v2.3 art bytes match", "true", String.valueOf(sameBytes(art, got)));
    }

    /* ---------------- UTF-16 text, the encoding that breaks parsers ------- */

    static void id3v23Utf16(File dir) throws Exception {
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        frame23(tag, "TIT2", textFrame(1, "Kowloon Side"));      // UTF-16 + BOM
        frame23(tag, "TPE1", textFrame(2, "Ossa Mira"));         // UTF-16BE, no BOM
        frame23(tag, "TALB", textFrame(3, "Salt & Static"));     // UTF-8
        byte[] body = tag.toByteArray();
        File f = write(dir, "utf16.mp3", id3Header(3, body.length), body, new byte[0], mp3Frames());
        Track t = Id3.read(url(f));
        eq("utf16 BOM title", "Kowloon Side",  t.title);
        eq("utf16be artist",  "Ossa Mira",     t.artist);
        eq("utf8 album",      "Salt & Static", t.album);
    }

    /* ---------------- the old three-letter frame ids ---------------- */

    static void id3v22(File dir) throws Exception {
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        frame22(tag, "TT2", textFrame(0, "Night Bus Radio"));
        frame22(tag, "TP1", textFrame(0, "Kowloon Tape Club"));
        byte[] body = tag.toByteArray();
        File f = write(dir, "v22.mp3", id3Header(2, body.length), body, new byte[0], mp3Frames());
        Track t = Id3.read(url(f));
        eq("v2.2 title",  "Night Bus Radio",   t.title);
        eq("v2.2 artist", "Kowloon Tape Club", t.artist);
    }

    /* ---------------- ID3v1 trailer only ---------------- */

    static void id3v1Only(File dir) throws Exception {
        byte[] v1 = new byte[128];
        v1[0] = 'T'; v1[1] = 'A'; v1[2] = 'G';
        put(v1, 3,  "Runway 27");
        put(v1, 33, "Halcyon Pilots");
        put(v1, 63, "Cloud Base");
        v1[125] = 0; v1[126] = 7;
        File f = write(dir, "v1.mp3", mp3Frames(), v1);
        Track t = Id3.read(url(f));
        eq("v1 title",  "Runway 27",      t.title);
        eq("v1 artist", "Halcyon Pilots", t.artist);
        eq("v1 album",  "Cloud Base",     t.album);
        eq("v1 track",  "7",              String.valueOf(t.trackNo));
    }

    /* ---------------- no tag: fall back to the filename ---------------- */

    static void noTagAtAll(File dir) throws Exception {
        File f = write(dir, "Holding Pattern.mp3", mp3Frames());
        Track t = Id3.read(url(f));
        eq("untagged displayTitle",  "Holding Pattern", t.displayTitle());
        eq("untagged displayArtist", "Unknown Artist",  t.displayArtist());
        eq("untagged has no art",    "false",           String.valueOf(t.hasArt()));
    }

    /* ---------------- the directory walk ----------------
     * Regression guard: an early version skipped "predefgallery/", which on a
     * Series 40 phone is exactly where phone-memory Music lives, so a real
     * library scanned to zero tracks.
     */

    static void scanning(File tmp) throws Exception {
        File root = new File(tmp, "tree");
        deleteTree(root);
        root.mkdirs();

        tagged(new File(root, "predefgallery/Music"), "Phone Music.mp3", "Phone Music");
        tagged(new File(root, "Music"),               "Card Music.mp3",  "Card Music");
        tagged(new File(root, "a/b/c/d"),             "Deep.mp3",        "Deep");
        tagged(new File(root, "Music"),               "Upper.MP3",       "Upper");
        tagged(new File(root, "system"),              "Protected.mp3",   "Protected");
        writeFile(new File(root, "Music"), "notes.txt", "hello".getBytes("UTF-8"));

        String rootUrl = root.getAbsolutePath().replace((char) 92, '/');
        if (rootUrl.charAt(rootUrl.length() - 1) != '/') rootUrl = rootUrl + "/";
        javax.microedition.io.file.FileSystemRegistry.setRoots(new String[] { rootUrl });

        Library lib = new Library();
        lib.scan();

        eq("scan found the phone Music folder", "true",  String.valueOf(has(lib, "Phone Music")));
        eq("scan found a top-level Music folder", "true", String.valueOf(has(lib, "Card Music")));
        eq("scan recurses into nested folders", "true",  String.valueOf(has(lib, "Deep")));
        eq("scan accepts .MP3 uppercase",       "true",  String.valueOf(has(lib, "Upper")));
        eq("scan skips system/",                "false", String.valueOf(has(lib, "Protected")));
        eq("scan ignores non-mp3 files",        "4",     String.valueOf(lib.tracks.size()));
        eq("scan recorded folders walked",      "true",  String.valueOf(lib.diagDirs > 0));
    }

    static boolean has(Library lib, String title) {
        for (int i = 0; i < lib.tracks.size(); i++) {
            if (title.equals(lib.track(i).displayTitle())) return true;
        }
        return false;
    }

    /** An mp3 with a real ID3v2.3 title, in a folder that is created for it. */
    static void tagged(File dir, String name, String title) throws Exception {
        dir.mkdirs();
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        frame23(tag, "TIT2", textFrame(0, title));
        byte[] body = tag.toByteArray();
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.write(id3Header(3, body.length));
        all.write(body);
        all.write(mp3Frames());
        writeFile(dir, name, all.toByteArray());
    }

    /** distinct from the varargs write() used by the tag tests */
    static void writeFile(File dir, String name, byte[] data) throws Exception {
        dir.mkdirs();
        FileOutputStream o = new FileOutputStream(new File(dir, name));
        o.write(data);
        o.close();
    }

    static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (int i = 0; i < kids.length; i++) deleteTree(kids[i]);
        f.delete();
    }

    /* ---------------- grouping and the index rebuild ---------------- */

    static void grouping() {
        Library lib = new Library();
        lib.tracks = new Vector();
        lib.tracks.addElement(mk("Departures",  "Neon Postcards", "Terminal 3", 1, true));
        lib.tracks.addElement(mk("Gate 42",     "Neon Postcards", "Terminal 3", 3, false));
        lib.tracks.addElement(mk("Duty Free",   "Neon Postcards", "Terminal 3", 2, false));
        lib.tracks.addElement(mk("Red Eye",     "Neon Postcards", "Arrivals",   1, false));
        lib.tracks.addElement(mk("Low Tide",    "Blue Harbour",   "Lantern",    1, false));
        lib.tracks.addElement(mk("Untagged",    null,             null,         0, false));
        lib.group();

        eq("albums found",  "4", String.valueOf(lib.albums.size()));
        eq("artists found", "3", String.valueOf(lib.artists.size()));

        // every track must point at the album that actually contains it
        boolean links = true;
        for (int i = 0; i < lib.albums.size(); i++) {
            Library.Album al = lib.album(i);
            for (int j = 0; j < al.trackIdx.size(); j++) {
                if (lib.track(Library.index(al.trackIdx, j)).albumIdx != i) links = false;
            }
        }
        eq("track -> album links", "true", String.valueOf(links));

        // Terminal 3 must be in disc order, not the order we added them
        Library.Album t3 = null;
        for (int i = 0; i < lib.albums.size(); i++) {
            if (lib.album(i).name.equals("Terminal 3")) t3 = lib.album(i);
        }
        StringBuffer order = new StringBuffer();
        for (int j = 0; j < t3.trackIdx.size(); j++) {
            if (j > 0) order.append(",");
            order.append(lib.track(Library.index(t3.trackIdx, j)).title);
        }
        eq("album track order", "Departures,Duty Free,Gate 42", order.toString());

        // the artist's album list must survive the album re-sort
        Library.Artist np = null;
        for (int i = 0; i < lib.artists.size(); i++) {
            if (lib.artist(i).name.equals("Neon Postcards")) np = lib.artist(i);
        }
        eq("artist album count", "2", String.valueOf(np.albumIdx.size()));
        boolean ok = true;
        for (int i = 0; i < np.albumIdx.size(); i++) {
            if (!lib.album(Library.index(np.albumIdx, i)).artist.equals("Neon Postcards")) ok = false;
        }
        eq("artist album links", "true", String.valueOf(ok));

        // the cover-bearing track must be the one that has art
        Library.Album withArt = null;
        for (int i = 0; i < lib.albums.size(); i++) {
            if (lib.album(i).artTrack >= 0) withArt = lib.album(i);
        }
        eq("album art track found", "Terminal 3", withArt == null ? "none" : withArt.name);

        eq("songOrder size", "6", String.valueOf(lib.songOrder().size()));
        eq("unknown artist grouped", "Unknown Artist",
           lib.track(((Integer) lib.songOrder().elementAt(lib.songOrder().size() - 1)).intValue()).displayArtist());
    }

    static void sorting() {
        eq("The Ferry sorts under F", "true", String.valueOf(Sort.name("The Ferry Lights", "Gate") < 0));
        eq("A Night sorts under N",   "true", String.valueOf(Sort.name("A Night Out", "Morning") > 0));
        eq("case insensitive",        "0",    String.valueOf(Sort.name("blue harbour", "Blue Harbour")));
    }

    static void strings() {
        eq("Str.cat",  "file:///E:/Music/", Str.cat("file:///", "E:/Music/"));
        eq("Str.pair", "3 of 12",           Str.pair(3, " of ", 12));
        eq("time 0",   "0:00",              Gfx.time(0));
        eq("time 72s", "1:12",              Gfx.time(72000));
        eq("time 9s",  "0:09",              Gfx.time(9400));
        eq("time 1h",  "60:00",             Gfx.time(3600000));
    }

    /* ---------------- helpers ---------------- */

    static Track mk(String title, String artist, String album, int no, boolean art) {
        Track t = new Track("file:///E:/" + title + ".mp3");
        t.title = title; t.artist = artist; t.album = album; t.trackNo = no;
        if (art) { t.artOff = 100; t.artLen = 400; }
        return t;
    }

    static byte[] id3Header(int ver, int size) {
        byte[] h = new byte[10];
        h[0] = 'I'; h[1] = 'D'; h[2] = '3';
        h[3] = (byte) ver; h[4] = 0; h[5] = 0;
        h[6] = (byte) ((size >> 21) & 0x7f);
        h[7] = (byte) ((size >> 14) & 0x7f);
        h[8] = (byte) ((size >> 7) & 0x7f);
        h[9] = (byte) (size & 0x7f);
        return h;
    }

    static void frame23(ByteArrayOutputStream out, String id, byte[] data) {
        for (int i = 0; i < 4; i++) out.write(id.charAt(i));
        out.write((data.length >> 24) & 0xff); out.write((data.length >> 16) & 0xff);
        out.write((data.length >> 8) & 0xff);  out.write(data.length & 0xff);
        out.write(0); out.write(0);
        out.write(data, 0, data.length);
    }

    static void frame22(ByteArrayOutputStream out, String id, byte[] data) {
        for (int i = 0; i < 3; i++) out.write(id.charAt(i));
        out.write((data.length >> 16) & 0xff); out.write((data.length >> 8) & 0xff);
        out.write(data.length & 0xff);
        out.write(data, 0, data.length);
    }

    static byte[] textFrame(int enc, String s) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(enc);
        if (enc == 0) { for (int i = 0; i < s.length(); i++) o.write(s.charAt(i) & 0xff); }
        else if (enc == 1) { o.write(0xff); o.write(0xfe);
            for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); o.write(c & 0xff); o.write((c >> 8) & 0xff); } }
        else if (enc == 2) { for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); o.write((c >> 8) & 0xff); o.write(c & 0xff); } }
        else { byte[] b = s.getBytes("UTF-8"); o.write(b, 0, b.length); }
        return o.toByteArray();
    }

    static byte[] apicFrame(byte[] art) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0);                                   // ISO-8859-1
        byte[] mime = "image/png".getBytes("UTF-8");
        o.write(mime, 0, mime.length); o.write(0);
        o.write(3);                                   // front cover
        byte[] desc = "Cover".getBytes("UTF-8");
        o.write(desc, 0, desc.length); o.write(0);
        o.write(art, 0, art.length);
        return o.toByteArray();
    }

    static byte[] fakePng(int n) {
        byte[] b = new byte[n];
        b[0] = (byte) 0x89; b[1] = 'P'; b[2] = 'N'; b[3] = 'G';
        for (int i = 4; i < n; i++) b[i] = (byte) ((i * 31) & 0xff);
        return b;
    }

    static byte[] mp3Frames() {
        byte[] b = new byte[512];
        for (int i = 0; i < b.length; i += 4) { b[i] = (byte) 0xff; b[i + 1] = (byte) 0xfb; }
        return b;
    }

    static void put(byte[] dst, int off, String s) {
        for (int i = 0; i < s.length(); i++) dst[off + i] = (byte) s.charAt(i);
    }

    /** file:/// URL with forward slashes, the way Series 40 spells a path. */
    static String url(File f) {
        return "file:///" + f.getAbsolutePath().replace((char) 92, '/');
    }

    static File write(File dir, String name, byte[]... parts) throws Exception {
        File f = new File(dir, name);
        FileOutputStream o = new FileOutputStream(f);
        for (byte[] p : parts) o.write(p);
        o.close();
        return f;
    }

    static boolean sameBytes(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    static void eq(String what, String expect, String got) {
        boolean ok = expect == null ? got == null : expect.equals(got);
        if (ok) { pass++; System.out.println("  ok    " + what); }
        else { fail++; System.out.println("  FAIL  " + what + ": expected <" + expect + "> got <" + got + ">"); }
    }
}
