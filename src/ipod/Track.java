package ipod;

/** One song. Album art is kept as a file offset so we only read it when it is shown. */
public final class Track {
    public String url;        // file:///E:/Music/foo.mp3
    public String title;
    public String artist;
    public String album;
    public int    trackNo;
    public int    artOff;     // byte offset of embedded artwork, 0 when there is none
    public int    artLen;
    public int    albumIdx = -1;

    public Track() {}

    public Track(String url) {
        this.url = url;
    }

    public String displayTitle() {
        if (title != null && title.length() > 0) return title;
        return fileName();
    }

    public String displayArtist() {
        if (artist != null && artist.length() > 0) return artist;
        return "Unknown Artist";
    }

    public String displayAlbum() {
        if (album != null && album.length() > 0) return album;
        return "Unknown Album";
    }

    public String fileName() {
        int slash = url.lastIndexOf('/');
        String n = slash >= 0 ? url.substring(slash + 1) : url;
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        return n;
    }

    public boolean hasArt() {
        return artLen > 0;
    }
}
