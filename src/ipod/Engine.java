package ipod;

import java.io.InputStream;
import java.util.Random;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;

/** Playback: a queue of track indices, one MMAPI player at a time. */
public final class Engine implements PlayerListener {

    private final Library lib;
    private final Settings set;
    private Shell shell;

    private Vector queue = new Vector();     // Integer track indices
    private int pos = -1;
    private Player player;
    private VolumeControl vol;
    private InputStream stream;              // only when the URL locator was refused
    private FileConnection conn;

    public volatile boolean playing;
    public volatile String error;
    /** false when the device gave us no working VolumeControl. */
    public volatile boolean volumeControlled;
    public Vector onTheGo = new Vector();    // urls

    private final Random rnd = new Random();

    public Engine(Library lib, Settings set) {
        this.lib = lib;
        this.set = set;
        this.onTheGo = Store.loadOnTheGo();
    }

    public void attach(Shell s) {
        this.shell = s;
    }

    /* ---- queue --------------------------------------------------------- */

    public void play(Vector trackIndices, int startAt) {
        if (trackIndices == null || trackIndices.size() == 0) return;
        Vector q = new Vector(trackIndices.size());
        for (int i = 0; i < trackIndices.size(); i++) q.addElement(trackIndices.elementAt(i));

        if (set.shuffle == Settings.SHUFFLE_SONGS) {
            Object first = (startAt >= 0 && startAt < q.size()) ? q.elementAt(startAt) : null;
            shuffle(q);
            if (first != null) {
                q.removeElement(first);
                q.insertElementAt(first, 0);
            }
            startAt = 0;
        }
        queue = q;
        pos = (startAt < 0 || startAt >= q.size()) ? 0 : startAt;
        open(true);
    }

    public void shuffleAll() {
        int old = set.shuffle;
        set.shuffle = Settings.SHUFFLE_SONGS;
        play(lib.allTrackIndices(), 0);
        set.shuffle = old == Settings.SHUFFLE_OFF ? Settings.SHUFFLE_SONGS : old;
    }

    private void shuffle(Vector v) {
        for (int i = v.size() - 1; i > 0; i--) {
            int j = Math.abs(rnd.nextInt()) % (i + 1);
            Object a = v.elementAt(i);
            v.setElementAt(v.elementAt(j), i);
            v.setElementAt(a, j);
        }
    }

    public Track current() {
        if (pos < 0 || pos >= queue.size()) return null;
        return lib.track(((Integer) queue.elementAt(pos)).intValue());
    }

    public int queueSize() { return queue.size(); }
    public int queuePos()  { return pos; }
    public boolean hasTrack() { return current() != null; }

    /* ---- transport ------------------------------------------------------ */

    public void toggle() {
        if (player == null) {
            if (current() != null) open(true);
            return;
        }
        try {
            if (playing) {
                player.stop();
                playing = false;
            } else {
                player.start();
                playing = true;
            }
        } catch (Throwable e) {
            error = "Playback error";
        }
        refresh();
    }

    public void next() {
        if (queue.size() == 0) return;
        if (pos + 1 >= queue.size()) {
            if (set.repeat == Settings.REPEAT_ALL) pos = 0;
            else { stopAll(); return; }
        } else {
            pos++;
        }
        open(true);
    }

    public void prev() {
        if (queue.size() == 0) return;
        // the iPod restarts the track if you are more than a couple of seconds in
        if (position() > 3000L) {
            seekTo(0);
            return;
        }
        if (pos - 1 < 0) {
            if (set.repeat == Settings.REPEAT_ALL) pos = queue.size() - 1;
            else { seekTo(0); return; }
        } else {
            pos--;
        }
        open(true);
    }

    void autoAdvance() {   // package-private: an inner class calls it
        if (set.repeat == Settings.REPEAT_ONE) {
            open(true);
            return;
        }
        next();
    }

    /* ---- position ------------------------------------------------------- */

    public long position() {
        try {
            if (player != null) {
                long t = player.getMediaTime();
                if (t >= 0) return t / 1000L;
            }
        } catch (Throwable e) {}
        return 0;
    }

    public long duration() {
        try {
            if (player != null) {
                long d = player.getDuration();
                if (d > 0) return d / 1000L;
            }
        } catch (Throwable e) {}
        return 0;
    }

    public void seekTo(long ms) {
        try {
            if (player != null) player.setMediaTime(ms * 1000L);
        } catch (Throwable e) {}
    }

    public void seekBy(int seconds) {
        long d = duration();
        long p = position() + seconds * 1000L;
        if (p < 0) p = 0;
        if (d > 0 && p > d - 500) p = d - 500;
        seekTo(p);
    }

    /* ---- volume ---------------------------------------------------------- */

    /**
     * Find the VolumeControl, lazily and stubbornly. Implementations differ on
     * when the control appears - some only expose it once the player is
     * PREFETCHED, some not until it has STARTED - so we re-ask rather than
     * caching a null forever. MMAPI says an unqualified name gets the
     * javax.microedition.media.control prefix applied, but not every device
     * honours that, so the full name is tried too.
     */
    private VolumeControl volumeControl() {
        if (vol != null) return vol;
        if (player == null) return null;
        String[] names = { "VolumeControl", "javax.microedition.media.control.VolumeControl" };
        for (int i = 0; i < names.length; i++) {
            try {
                Object c = player.getControl(names[i]);
                if (c instanceof VolumeControl) {
                    vol = (VolumeControl) c;
                    return vol;
                }
            } catch (Throwable e) {
                // try the next spelling
            }
        }
        return null;
    }

    /**
     * Sets the level and then reads it back, so the UI shows what the device
     * actually did rather than what we asked for. volumeControlled says whether
     * anything is listening at all.
     */
    public void setVolume(int v) {
        if (v < 0) v = 0;
        if (v > 100) v = 100;
        set.volume = v;

        VolumeControl c = volumeControl();
        if (c == null) {
            volumeControlled = false;
            return;
        }
        try {
            if (c.isMuted()) c.setMute(false);
            c.setLevel(v);
            int got = c.getLevel();
            if (got >= 0 && got <= 100) set.volume = got;   // the device may quantise
            volumeControlled = true;
        } catch (Throwable e) {
            volumeControlled = false;
        }
    }

    public void nudgeVolume(int delta) {
        setVolume(set.volume + delta);
    }

    /** Describes the volume path, for the Diagnostics screen. */
    public String volumeStatus() {
        if (player == null) return "no player";
        VolumeControl c = volumeControl();
        if (c == null) return "no VolumeControl";
        try {
            return Str.cat("level ", Str.num(c.getLevel()), c.isMuted() ? " (muted)" : "");
        } catch (Throwable e) {
            return Library.describe(e);
        }
    }

    /* ---- player lifecycle -------------------------------------------------- */

    private void open(boolean start) {
        Track t = current();
        if (t == null) return;
        closePlayer();
        error = null;
        set.lastUrl = t.url;
        try {
            player = Manager.createPlayer(t.url);
            player.addPlayerListener(this);
            player.realize();
            player.prefetch();
        } catch (Throwable e) {
            player = null;
            if (!openViaStream(t)) {
                error = "Cannot play file";
                playing = false;
                refresh();
                return;
            }
        }
        vol = null;
        setVolume(set.volume);          // may be too early on some devices
        if (start) {
            try {
                player.start();
                playing = true;
            } catch (Throwable e) {
                error = "Playback error";
                playing = false;
            }
            // Several MMAPI implementations ignore setLevel before the player
            // has started, and some only expose the control at that point, so
            // apply it again now that sound is actually coming out.
            setVolume(set.volume);
        }
        refresh();
    }

    /** Some Series 40 builds refuse the file:// locator and want a stream. */
    private boolean openViaStream(Track t) {
        try {
            conn = (FileConnection) Connector.open(t.url, Connector.READ);
            stream = conn.openInputStream();
            player = Manager.createPlayer(stream, "audio/mpeg");
            player.addPlayerListener(this);
            player.realize();
            player.prefetch();
            return true;
        } catch (Throwable e) {
            closeStream();
            player = null;
            return false;
        }
    }

    public void stopAll() {
        closePlayer();
        playing = false;
        refresh();
    }

    private void closePlayer() {
        try {
            if (player != null) {
                player.removePlayerListener(this);
                if (player.getState() == Player.STARTED) player.stop();
                player.deallocate();
                player.close();
            }
        } catch (Throwable e) {}
        player = null;
        vol = null;
        closeStream();
    }

    private void closeStream() {
        try { if (stream != null) stream.close(); } catch (Throwable e) {}
        try { if (conn != null) conn.close(); } catch (Throwable e) {}
        stream = null;
        conn = null;
    }

    public void playerUpdate(Player p, String event, Object data) {
        if (PlayerListener.END_OF_MEDIA.equals(event)) {
            playing = false;
            // never do real work on the media callback thread
            new Thread(new Runnable() {
                public void run() {
                    autoAdvance();
                }
            }).start();
        } else if (PlayerListener.ERROR.equals(event)) {
            error = "Playback error";
            playing = false;
            refresh();
        }
    }

    /* ---- on-the-go -------------------------------------------------------- */

    public void addToOnTheGo(Track t) {
        if (t == null) return;
        if (!onTheGo.contains(t.url)) {
            onTheGo.addElement(t.url);
            Store.saveOnTheGo(onTheGo);
        }
    }

    public void clearOnTheGo() {
        onTheGo.removeAllElements();
        Store.saveOnTheGo(onTheGo);
    }

    /** Resolve the saved urls to current library indices. */
    public Vector onTheGoIndices() {
        Vector v = new Vector();
        for (int i = 0; i < onTheGo.size(); i++) {
            String url = (String) onTheGo.elementAt(i);
            for (int j = 0; j < lib.tracks.size(); j++) {
                if (url.equals(lib.track(j).url)) {
                    v.addElement(new Integer(j));
                    break;
                }
            }
        }
        return v;
    }

    private void refresh() {
        if (shell != null) shell.refresh();
    }
}
