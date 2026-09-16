package javax.microedition.media;
import java.io.IOException;
import java.io.InputStream;
public final class Manager {
    public static final String TONE_DEVICE_LOCATOR = "device://tone";
    private Manager() {}
    public static Player createPlayer(String locator) throws IOException, MediaException { return null; }
    public static Player createPlayer(InputStream stream, String type) throws IOException, MediaException { return null; }
    public static String[] getSupportedContentTypes(String protocol) { return null; }
    public static String[] getSupportedProtocols(String content_type) { return null; }
    public static void playTone(int note, int duration, int volume) throws MediaException {}
}
