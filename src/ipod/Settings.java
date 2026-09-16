package ipod;

public final class Settings {

    public static final int SHUFFLE_OFF    = 0;
    public static final int SHUFFLE_SONGS  = 1;
    public static final int SHUFFLE_ALBUMS = 2;

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;

    public int shuffle = SHUFFLE_OFF;
    public int repeat = REPEAT_OFF;
    public boolean transitions = true;
    /** Off trades covers for headroom on a phone with a small heap. */
    public boolean artwork = true;
    public int volume = 70;
    public String lastUrl;

    public String shuffleLabel() {
        if (shuffle == SHUFFLE_SONGS) return "Songs";
        if (shuffle == SHUFFLE_ALBUMS) return "Albums";
        return "Off";
    }

    public String repeatLabel() {
        if (repeat == REPEAT_ONE) return "One";
        if (repeat == REPEAT_ALL) return "All";
        return "Off";
    }
}
