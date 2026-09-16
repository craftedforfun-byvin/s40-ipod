package ipod;

import java.util.Vector;

/** Builds every menu and handles what happens when a row is chosen. */
public final class Menus {

    /* menus */
    public static final int M_ROOT = 0, M_MUSIC = 1, M_PLAYLISTS = 2, M_ARTISTS = 3,
            M_ALBUMS = 4, M_SONGS = 5, M_SETTINGS = 6, M_ARTIST = 7, M_ALBUM = 8,
            M_OTG = 9, M_ARTIST_SONGS = 10, M_BROWSE_ROOTS = 11;

    /* actions */
    public static final int A_MENU = 1, A_PLAY_TRACK = 2, A_SHUFFLE_ALL = 3,
            A_NOWPLAYING = 4, A_COVERFLOW = 5, A_SET_SHUFFLE = 6, A_SET_REPEAT = 7,
            A_SET_TRANS = 8, A_UPDATE = 9, A_ABOUT = 10, A_CLEAR_OTG = 11,
            A_EXIT = 12, A_NOTHING = 13, A_BROWSE_DIR = 14, A_PLAY_FILE = 15,
            A_DIAG = 16, A_SET_ART = 17;

    private Menus() {}

    public static MenuScreen build(Shell sh, int menu, int arg) {
        Library lib = sh.lib;
        Settings set = sh.settings;

        switch (menu) {

            case M_ROOT: {
                MenuScreen m = new MenuScreen("iPod");
                m.add(MenuItem.sub("Music", A_MENU, M_MUSIC));
                m.add(MenuItem.act("Cover Flow", A_COVERFLOW, 0));
                m.add(MenuItem.act("Shuffle Songs", A_SHUFFLE_ALL, 0));
                m.add(MenuItem.sub("Settings", A_MENU, M_SETTINGS));
                if (sh.engine.hasTrack()) m.add(MenuItem.act("Now Playing", A_NOWPLAYING, 0));
                return m;
            }

            case M_MUSIC: {
                MenuScreen m = new MenuScreen("Music");
                if (lib.tracks.size() == 0) {
                    m.add(MenuItem.act("No songs found", A_UPDATE, 0));
                    m.add(MenuItem.act("Update Library", A_UPDATE, 0));
                    m.add(MenuItem.sub("Browse Files", A_MENU, M_BROWSE_ROOTS));
                    m.add(MenuItem.act("Diagnostics", A_DIAG, 0));
                    return m;
                }
                m.add(MenuItem.act("Cover Flow", A_COVERFLOW, 0));
                m.add(MenuItem.sub("Playlists", A_MENU, M_PLAYLISTS));
                m.add(MenuItem.sub("Artists", A_MENU, M_ARTISTS));
                m.add(MenuItem.sub("Albums", A_MENU, M_ALBUMS));
                m.add(MenuItem.sub("Songs", A_MENU, M_SONGS));
                return m;
            }

            case M_PLAYLISTS: {
                MenuScreen m = new MenuScreen("Playlists");
                m.add(MenuItem.sub("On-The-Go", A_MENU, M_OTG)
                        .value(String.valueOf(sh.engine.onTheGo.size())));
                return m;
            }

            case M_OTG: {
                MenuScreen m = new MenuScreen("On-The-Go");
                Vector q = sh.engine.onTheGoIndices();
                m.queue = q;
                if (q.size() == 0) {
                    m.add(MenuItem.act("Empty", A_NOTHING, 0));
                    m.add(MenuItem.act("Hold 0 on a song to add", A_NOTHING, 0));
                    return m;
                }
                fillTracks(sh, m, q);
                return m;
            }

            case M_ARTISTS: {
                MenuScreen m = new MenuScreen("Artists");
                for (int i = 0; i < lib.artists.size(); i++) {
                    Library.Artist a = lib.artist(i);
                    MenuItem it = MenuItem.sub(a.name, A_MENU, M_ARTIST).arg2(i);
                    if (a.albumIdx.size() > 0) it.albumIdx = Library.index(a.albumIdx, 0);
                    m.add(it);
                }
                return m;
            }

            case M_ARTIST: {
                Library.Artist a = lib.artist(arg);
                MenuScreen m = new MenuScreen(a == null ? "Artist" : a.name);
                if (a == null) return m;
                m.add(MenuItem.sub("All Songs", A_MENU, M_ARTIST_SONGS).arg2(arg));
                for (int i = 0; i < a.albumIdx.size(); i++) {
                    int ai = Library.index(a.albumIdx, i);
                    Library.Album al = lib.album(ai);
                    m.add(MenuItem.sub(al.name, A_MENU, M_ALBUM).arg2(ai).album(ai));
                }
                return m;
            }

            case M_ARTIST_SONGS: {
                Library.Artist a = lib.artist(arg);
                MenuScreen m = new MenuScreen(a == null ? "Songs" : a.name);
                if (a == null) return m;
                m.queue = a.trackIdx;
                fillTracks(sh, m, a.trackIdx);
                return m;
            }

            case M_ALBUMS: {
                MenuScreen m = new MenuScreen("Albums");
                for (int i = 0; i < lib.albums.size(); i++) {
                    Library.Album al = lib.album(i);
                    m.add(MenuItem.sub(al.name, A_MENU, M_ALBUM).arg2(i).album(i));
                }
                return m;
            }

            case M_ALBUM: {
                Library.Album al = lib.album(arg);
                MenuScreen m = new MenuScreen(al == null ? "Album" : al.name);
                if (al == null) return m;
                m.queue = al.trackIdx;
                fillTracks(sh, m, al.trackIdx);
                return m;
            }

            case M_SONGS: {
                MenuScreen m = new MenuScreen("Songs");
                Vector q = lib.songOrder();
                m.queue = q;
                fillTracks(sh, m, q);
                return m;
            }

            case M_SETTINGS: {
                MenuScreen m = new MenuScreen("Settings");
                m.add(MenuItem.act("Shuffle", A_SET_SHUFFLE, 0).value(set.shuffleLabel()));
                m.add(MenuItem.act("Repeat", A_SET_REPEAT, 0).value(set.repeatLabel()));
                m.add(MenuItem.act("Transitions", A_SET_TRANS, 0).value(set.transitions ? "On" : "Off"));
                m.add(MenuItem.act("Album Art", A_SET_ART, 0).value(set.artwork ? "On" : "Off"));
                m.add(MenuItem.act("Update Library", A_UPDATE, 0));
                m.add(MenuItem.sub("Browse Files", A_MENU, M_BROWSE_ROOTS));
                m.add(MenuItem.act("Diagnostics", A_DIAG, 0));
                m.add(MenuItem.act("Clear On-The-Go", A_CLEAR_OTG, 0));
                m.add(MenuItem.act("About", A_ABOUT, 0));
                m.add(MenuItem.act("Quit iPod", A_EXIT, 0));
                return m;
            }

            case M_BROWSE_ROOTS:
                return browseRoots();

            default:
                return new MenuScreen("iPod");
        }
    }

    /* ---- file browser -------------------------------------------------------
     * Both a workaround and a diagnostic: it shows what the filesystem really
     * looks like from inside the MIDlet, and lets you play a file the scan
     * missed.
     */

    public static MenuScreen browseRoots() {
        MenuScreen m = new MenuScreen("Browse Files");
        try {
            java.util.Enumeration e = javax.microedition.io.file.FileSystemRegistry.listRoots();
            int n = 0;
            while (e != null && e.hasMoreElements()) {
                String r = (String) e.nextElement();
                m.add(MenuItem.sub(r, A_BROWSE_DIR, 0).path(Library.rootUrl(r)));
                n++;
            }
            if (n == 0) m.add(MenuItem.act("No roots reported", A_NOTHING, 0));
        } catch (Throwable t) {
            m.add(MenuItem.act("Cannot list roots", A_NOTHING, 0));
            m.add(MenuItem.act(Library.describe(t), A_NOTHING, 0));
        }
        return m;
    }

    public static MenuScreen browse(String url) {
        MenuScreen m = new MenuScreen(folderName(url));
        javax.microedition.io.file.FileConnection dir = null;
        try {
            dir = (javax.microedition.io.file.FileConnection)
                    javax.microedition.io.Connector.open(url, javax.microedition.io.Connector.READ);
            if (!dir.exists()) {
                m.add(MenuItem.act("Does not exist", A_NOTHING, 0));
                return m;
            }
            if (!dir.isDirectory()) {
                m.add(MenuItem.act("Not a folder", A_NOTHING, 0));
                return m;
            }
            java.util.Enumeration e = dir.list("*", true);
            Vector dirs = new Vector(), files = new Vector();
            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();
                if (name.length() == 0) continue;
                if (name.charAt(name.length() - 1) == '/') dirs.addElement(name);
                else files.addElement(name);
            }
            Sort.sort(dirs, NAMES);
            Sort.sort(files, NAMES);
            for (int i = 0; i < dirs.size(); i++) {
                String name = (String) dirs.elementAt(i);
                m.add(MenuItem.sub(name, A_BROWSE_DIR, 0).path(Str.cat(url, name)));
            }
            for (int i = 0; i < files.size(); i++) {
                String name = (String) files.elementAt(i);
                if (isPlayable(name)) {
                    m.add(MenuItem.act(name, A_PLAY_FILE, 0).path(Str.cat(url, name)).value("play"));
                } else {
                    m.add(MenuItem.act(name, A_NOTHING, 0));
                }
            }
            if (dirs.size() == 0 && files.size() == 0) {
                m.add(MenuItem.act("(empty)", A_NOTHING, 0));
            }
        } catch (Throwable t) {
            m.add(MenuItem.act("Cannot open", A_NOTHING, 0));
            m.add(MenuItem.act(Library.describe(t), A_NOTHING, 0));
        } finally {
            if (dir != null) try { dir.close(); } catch (Exception ex) {}
        }
        return m;
    }

    private static final Sort.Cmp NAMES = new Sort.Cmp() {
        public int compare(Object a, Object b) {
            return Sort.name((String) a, (String) b);
        }
    };

    private static boolean isPlayable(String name) {
        int n = name.length();
        if (n < 5) return false;
        return name.substring(n - 4).toLowerCase().equals(".mp3");
    }

    private static String folderName(String url) {
        String s = url;
        if (s.length() > 0 && s.charAt(s.length() - 1) == '/') s = s.substring(0, s.length() - 1);
        int i = s.lastIndexOf('/');
        String n = i >= 0 ? s.substring(i + 1) : s;
        return n.length() == 0 ? "Files" : n;
    }

    private static void fillTracks(Shell sh, MenuScreen m, Vector queue) {
        for (int i = 0; i < queue.size(); i++) {
            int ti = Library.index(queue, i);
            Track t = sh.lib.track(ti);
            if (t == null) continue;
            MenuItem it = new MenuItem(t.displayTitle(), A_PLAY_TRACK, i, false);
            it.trackIdx = ti;
            m.add(it);
        }
    }

    /* ---- selection ---------------------------------------------------------- */

    public static void activate(Shell sh, MenuScreen from, MenuItem it) {
        switch (it.action) {

            case A_MENU:
                sh.push(build(sh, it.arg, it.arg2));
                return;

            case A_PLAY_TRACK:
                if (from.queue != null && from.queue.size() > 0) {
                    sh.engine.play(from.queue, it.arg);
                    sh.push(new NowPlaying());
                }
                return;

            case A_SHUFFLE_ALL:
                if (sh.lib.tracks.size() == 0) return;
                sh.engine.shuffleAll();
                sh.push(new NowPlaying());
                return;

            case A_NOWPLAYING:
                sh.openNowPlaying();
                return;

            case A_COVERFLOW:
                if (sh.lib.albums.size() == 0) return;
                sh.push(new CoverFlow());
                return;

            case A_SET_SHUFFLE:
                sh.settings.shuffle = (sh.settings.shuffle + 1) % 3;
                it.value = sh.settings.shuffleLabel();
                Store.saveSettings(sh.settings);
                return;

            case A_SET_REPEAT:
                sh.settings.repeat = (sh.settings.repeat + 1) % 3;
                it.value = sh.settings.repeatLabel();
                Store.saveSettings(sh.settings);
                return;

            case A_SET_ART:
                sh.settings.artwork = !sh.settings.artwork;
                it.value = sh.settings.artwork ? "On" : "Off";
                sh.art.clear();
                Store.saveSettings(sh.settings);
                return;

            case A_SET_TRANS:
                sh.settings.transitions = !sh.settings.transitions;
                it.value = sh.settings.transitions ? "On" : "Off";
                Store.saveSettings(sh.settings);
                return;

            case A_UPDATE:
                sh.push(new ScanScreen());
                return;

            case A_CLEAR_OTG:
                sh.engine.clearOnTheGo();
                return;

            case A_ABOUT:
                sh.push(new AboutScreen());
                return;

            case A_DIAG:
                sh.push(new DiagScreen());
                return;

            case A_BROWSE_DIR:
                if (it.path != null) sh.push(browse(it.path));
                return;

            case A_PLAY_FILE:
                if (it.path != null) {
                    Track t = Id3.read(it.path);
                    Vector q = new Vector();
                    q.addElement(new Integer(sh.lib.addAdHoc(t)));
                    sh.engine.play(q, 0);
                    sh.push(new NowPlaying());
                }
                return;

            case A_EXIT:
                sh.midlet.quit();
                return;

            default:
                return;
        }
    }
}
