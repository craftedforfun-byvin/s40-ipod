package javax.microedition.io;
import java.io.IOException;
import javax.microedition.io.file.FileConnection;
/** Test-only: a real Connector backed by java.io, so Id3 can be run on a desktop JVM. */
public class Connector {
    public static final int READ = 1, WRITE = 2, READ_WRITE = 3;
    public static Connection open(String name) throws IOException { return open(name, READ); }
    public static Connection open(String name, int mode) throws IOException {
        if (!name.startsWith("file:///")) throw new IOException("not a file url: " + name);
        return new DesktopFile(name.substring(8));
    }
    public static Connection open(String name, int mode, boolean t) throws IOException { return open(name, mode); }
}
