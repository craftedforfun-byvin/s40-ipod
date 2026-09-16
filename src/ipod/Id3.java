package ipod;

import java.io.InputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

/**
 * Minimal ID3 reader: ID3v2.2/2.3/2.4 text frames plus the embedded picture
 * (APIC / PIC), falling back to ID3v1. Artwork is not copied into memory - we
 * remember where it lives in the file and read it on demand.
 */
public final class Id3 {

    private Id3() {}

    public static Track read(String url) {
        Track t = new Track(url);
        FileConnection fc = null;
        InputStream in = null;
        try {
            fc = (FileConnection) Connector.open(url, Connector.READ);
            if (!fc.exists()) return t;
            long size = fc.fileSize();
            in = fc.openInputStream();
            boolean gotV2 = parseV2(in, t);
            try { in.close(); } catch (Exception e) {}
            in = null;
            if (!gotV2 && size > 128) {
                in = fc.openInputStream();
                parseV1(in, size, t);
            }
        } catch (Throwable e) {
            // unreadable file - keep whatever we managed to fill in
        } finally {
            close(in);
            close(fc);
        }
        return t;
    }

    /** Pull the embedded artwork bytes for a track, or null. */
    public static byte[] readArt(Track t) {
        if (t == null || t.artLen <= 0) return null;
        FileConnection fc = null;
        InputStream in = null;
        try {
            fc = (FileConnection) Connector.open(t.url, Connector.READ);
            in = fc.openInputStream();
            skip(in, t.artOff);
            byte[] data = new byte[t.artLen];
            if (!readFully(in, data, 0, t.artLen)) return null;
            return data;
        } catch (Throwable e) {
            return null;
        } finally {
            close(in);
            close(fc);
        }
    }

    /* ------------------------------------------------------------------ */

    private static boolean parseV2(InputStream in, Track t) throws Exception {
        byte[] h = new byte[10];
        if (!readFully(in, h, 0, 10)) return false;
        if (h[0] != 73 || h[1] != 68 || h[2] != 51) return false;   // "ID3"

        int ver = h[3] & 0xff;
        int flags = h[5] & 0xff;
        int tagSize = syncsafe(h, 6);
        int pos = 10;
        int end = 10 + tagSize;
        // Unsynchronised tags would shift every offset; skip the art in that case.
        boolean unsync = (flags & 0x80) != 0;

        if ((flags & 0x40) != 0) {                 // extended header
            byte[] e = new byte[4];
            if (!readFully(in, e, 0, 4)) return true;
            pos += 4;
            int extLen = (ver >= 4) ? syncsafe(e, 0) - 4 : be32(e, 0);
            if (extLen > 0 && extLen < tagSize) {
                skip(in, extLen);
                pos += extLen;
            }
        }

        int idLen = (ver <= 2) ? 3 : 4;
        int hdrLen = (ver <= 2) ? 6 : 10;

        while (pos + hdrLen <= end) {
            byte[] fh = new byte[hdrLen];
            if (!readFully(in, fh, 0, hdrLen)) break;
            pos += hdrLen;
            if (fh[0] == 0) break;                 // padding

            String id = ascii(fh, 0, idLen);
            int fsize;
            if (ver <= 2)      fsize = ((fh[3] & 0xff) << 16) | ((fh[4] & 0xff) << 8) | (fh[5] & 0xff);
            else if (ver >= 4) fsize = syncsafe(fh, 4);
            else               fsize = be32(fh, 4);

            if (fsize <= 0 || pos + fsize > end) break;

            boolean picture = id.equals("APIC") || id.equals("PIC");
            boolean text = id.equals("TIT2") || id.equals("TPE1") || id.equals("TALB")
                    || id.equals("TRCK") || id.equals("TT2") || id.equals("TP1")
                    || id.equals("TAL") || id.equals("TRK");

            if (text) {
                int want = fsize < 512 ? fsize : 512;
                byte[] data = new byte[want];
                if (!readFully(in, data, 0, want)) break;
                pos += want;
                if (want < fsize) { skip(in, fsize - want); pos += fsize - want; }
                String v = decodeText(data, 0, want);
                if (id.equals("TIT2") || id.equals("TT2")) t.title = v;
                else if (id.equals("TPE1") || id.equals("TP1")) t.artist = v;
                else if (id.equals("TALB") || id.equals("TAL")) t.album = v;
                else t.trackNo = leadingInt(v);
            } else if (picture && !unsync && t.artLen == 0) {
                int peek = fsize < 320 ? fsize : 320;
                byte[] data = new byte[peek];
                if (!readFully(in, data, 0, peek)) break;
                int frameStart = pos;
                pos += peek;
                int dataStart = pictureDataStart(data, peek, ver);
                if (dataStart > 0 && dataStart < fsize) {
                    t.artOff = frameStart + dataStart;
                    t.artLen = fsize - dataStart;
                }
                if (peek < fsize) { skip(in, fsize - peek); pos += fsize - peek; }
            } else {
                skip(in, fsize);
                pos += fsize;
            }

            if (t.title != null && t.artist != null && t.album != null && t.artLen > 0) {
                break;                              // got everything worth having
            }
        }
        return true;
    }

    /** Offset of the raw image bytes inside an APIC/PIC frame. */
    private static int pictureDataStart(byte[] b, int len, int ver) {
        if (len < 4) return -1;
        int enc = b[0] & 0xff;
        int p = 1;
        if (ver <= 2) {
            p += 3;                                 // three-letter image format
        } else {
            while (p < len && b[p] != 0) p++;       // mime type
            p++;                                    // its terminator
        }
        if (p >= len) return -1;
        p++;                                        // picture type byte
        // description, terminated by one null (or two for the UTF-16 encodings)
        if (enc == 1 || enc == 2) {
            while (p + 1 < len && !(b[p] == 0 && b[p + 1] == 0)) p += 2;
            p += 2;
        } else {
            while (p < len && b[p] != 0) p++;
            p++;
        }
        return p <= len ? p : -1;
    }

    private static void parseV1(InputStream in, long size, Track t) throws Exception {
        skip(in, size - 128);
        byte[] b = new byte[128];
        if (!readFully(in, b, 0, 128)) return;
        if (b[0] != 84 || b[1] != 65 || b[2] != 71) return;         // "TAG"
        if (t.title == null)  t.title  = latin(b, 3, 30);
        if (t.artist == null) t.artist = latin(b, 33, 30);
        if (t.album == null)  t.album  = latin(b, 63, 30);
        if (t.trackNo == 0 && b[125] == 0) t.trackNo = b[126] & 0xff;
    }

    /* ---- text decoding ------------------------------------------------ */

    private static String decodeText(byte[] b, int off, int len) {
        if (len <= 1) return "";
        int enc = b[off] & 0xff;
        int p = off + 1, n = len - 1;
        String s;
        if (enc == 1 || enc == 2) {
            boolean little = false;
            if (enc == 1 && n >= 2) {
                int b0 = b[p] & 0xff, b1 = b[p + 1] & 0xff;
                if (b0 == 0xff && b1 == 0xfe) { little = true; p += 2; n -= 2; }
                else if (b0 == 0xfe && b1 == 0xff) { p += 2; n -= 2; }
            }
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i + 1 < n; i += 2) {
                int lo = b[p + i] & 0xff, hi = b[p + i + 1] & 0xff;
                char c = little ? (char) ((hi << 8) | lo) : (char) ((lo << 8) | hi);
                if (c == 0) break;
                sb.append(c);
            }
            s = sb.toString();
        } else if (enc == 3) {
            try {
                s = new String(b, p, n, "UTF-8");
            } catch (Exception e) {
                s = latin(b, p, n);
            }
        } else {
            s = latin(b, p, n);
        }
        return clean(s);
    }

    private static String latin(byte[] b, int off, int len) {
        StringBuffer sb = new StringBuffer(len);
        for (int i = 0; i < len && off + i < b.length; i++) {
            int c = b[off + i] & 0xff;
            if (c == 0) break;
            sb.append((char) c);
        }
        return clean(sb.toString());
    }

    private static String clean(String s) {
        if (s == null) return null;
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c == 0 || c == ' ' || c == 10 || c == 13 || c == 9) end--;
            else break;
        }
        int start = 0;
        while (start < end && s.charAt(start) == ' ') start++;
        s = s.substring(start, end);
        return s.length() == 0 ? null : s;
    }

    private static int leadingInt(String s) {
        if (s == null) return 0;
        int n = 0, i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c < 48 || c > 57) break;
            n = n * 10 + (c - 48);
            i++;
            if (n > 9999) break;
        }
        return n;
    }

    /* ---- byte helpers -------------------------------------------------- */

    private static String ascii(byte[] b, int off, int len) {
        StringBuffer sb = new StringBuffer(len);
        for (int i = 0; i < len; i++) sb.append((char) (b[off + i] & 0xff));
        return sb.toString();
    }

    private static int syncsafe(byte[] b, int off) {
        return ((b[off] & 0x7f) << 21) | ((b[off + 1] & 0x7f) << 14)
             | ((b[off + 2] & 0x7f) << 7) | (b[off + 3] & 0x7f);
    }

    private static int be32(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16)
             | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    static boolean readFully(InputStream in, byte[] b, int off, int len) throws Exception {
        int done = 0;
        while (done < len) {
            int n = in.read(b, off + done, len - done);
            if (n < 0) return false;
            done += n;
        }
        return true;
    }

    static void skip(InputStream in, long n) throws Exception {
        while (n > 0) {
            long s = in.skip(n);
            if (s <= 0) {
                if (in.read() < 0) return;
                s = 1;
            }
            n -= s;
        }
    }

    private static void close(Object o) {
        try {
            if (o instanceof InputStream) ((InputStream) o).close();
            else if (o instanceof FileConnection) ((FileConnection) o).close();
        } catch (Exception e) {}
    }
}
