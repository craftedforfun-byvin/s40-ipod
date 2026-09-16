package ipod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Vector;
import javax.microedition.rms.RecordStore;

/** RMS persistence: the scanned library, the settings, and the On-The-Go list. */
public final class Store {

    private static final String LIB = "ipodlib";
    private static final String SET = "ipodset";
    private static final String OTG = "ipodotg";
    private static final int CHUNK = 6000;
    private static final int VERSION = 1;

    private Store() {}

    /* ---- library cache ------------------------------------------------- */

    public static void saveLibrary(Library lib) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bo);
            out.writeInt(VERSION);
            out.writeInt(lib.tracks.size());
            for (int i = 0; i < lib.tracks.size(); i++) {
                Track t = (Track) lib.tracks.elementAt(i);
                out.writeUTF(t.url);
                out.writeUTF(t.title == null ? "" : t.title);
                out.writeUTF(t.artist == null ? "" : t.artist);
                out.writeUTF(t.album == null ? "" : t.album);
                out.writeShort(t.trackNo);
                out.writeInt(t.artOff);
                out.writeInt(t.artLen);
            }
            out.flush();
            writeChunks(LIB, bo.toByteArray());
        } catch (Throwable e) {
            // a cache that cannot be written just means a rescan next time
        }
    }

    public static boolean loadLibrary(Library lib) {
        try {
            byte[] all = readChunks(LIB);
            if (all == null || all.length < 8) return false;
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(all));
            if (in.readInt() != VERSION) return false;
            int n = in.readInt();
            if (n <= 0 || n > Library.MAX_TRACKS) return false;
            Vector v = new Vector(n);
            for (int i = 0; i < n; i++) {
                Track t = new Track();
                t.url = in.readUTF();
                t.title = nz(in.readUTF());
                t.artist = nz(in.readUTF());
                t.album = nz(in.readUTF());
                t.trackNo = in.readShort();
                t.artOff = in.readInt();
                t.artLen = in.readInt();
                v.addElement(t);
            }
            lib.tracks = v;
            lib.group();
            lib.ready = true;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static String nz(String s) {
        return (s == null || s.length() == 0) ? null : s;
    }

    /* ---- settings ------------------------------------------------------ */

    public static void saveSettings(Settings s) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bo);
            out.writeInt(VERSION);
            out.writeInt(s.shuffle);
            out.writeInt(s.repeat);
            out.writeBoolean(s.transitions);
            out.writeInt(s.volume);
            out.writeUTF(s.lastUrl == null ? "" : s.lastUrl);
            out.writeBoolean(s.artwork);
            out.flush();
            writeChunks(SET, bo.toByteArray());
        } catch (Throwable e) {}
    }

    public static void loadSettings(Settings s) {
        try {
            byte[] all = readChunks(SET);
            if (all == null) return;
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(all));
            if (in.readInt() != VERSION) return;
            s.shuffle = in.readInt();
            s.repeat = in.readInt();
            s.transitions = in.readBoolean();
            s.volume = in.readInt();
            s.lastUrl = nz(in.readUTF());
            // appended after the original format - a record written by an
            // older build simply ends here and keeps the default
            s.artwork = in.readBoolean();
        } catch (Throwable e) {}
    }

    /* ---- on-the-go playlist (stored by url so it survives a rescan) ----- */

    public static void saveOnTheGo(Vector urls) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bo);
            out.writeInt(urls.size());
            for (int i = 0; i < urls.size(); i++) out.writeUTF((String) urls.elementAt(i));
            out.flush();
            writeChunks(OTG, bo.toByteArray());
        } catch (Throwable e) {}
    }

    public static Vector loadOnTheGo() {
        Vector v = new Vector();
        try {
            byte[] all = readChunks(OTG);
            if (all == null) return v;
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(all));
            int n = in.readInt();
            for (int i = 0; i < n; i++) v.addElement(in.readUTF());
        } catch (Throwable e) {}
        return v;
    }

    /* ---- raw chunked records ------------------------------------------- */

    private static void writeChunks(String name, byte[] data) throws Exception {
        try { RecordStore.deleteRecordStore(name); } catch (Throwable e) {}
        RecordStore rs = RecordStore.openRecordStore(name, true);
        try {
            int off = 0;
            while (off < data.length) {
                int len = data.length - off;
                if (len > CHUNK) len = CHUNK;
                rs.addRecord(data, off, len);
                off += len;
            }
        } finally {
            try { rs.closeRecordStore(); } catch (Throwable e) {}
        }
    }

    private static byte[] readChunks(String name) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(name, false);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int next = rs.getNextRecordID();
            for (int id = 1; id < next; id++) {
                try {
                    byte[] b = rs.getRecord(id);
                    if (b != null) bo.write(b, 0, b.length);
                } catch (Throwable e) {
                    // deleted record - skip
                }
            }
            byte[] out = bo.toByteArray();
            return out.length == 0 ? null : out;
        } catch (Throwable e) {
            return null;
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Throwable e) {}
        }
    }

    public static void clearLibrary() {
        try { RecordStore.deleteRecordStore(LIB); } catch (Throwable e) {}
    }
}
