package javax.microedition.io;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
public class Connector {
    public static final int READ = 1, WRITE = 2, READ_WRITE = 3;
    private Connector() {}
    public static Connection open(String name) throws IOException { return null; }
    public static Connection open(String name, int mode) throws IOException { return null; }
    public static Connection open(String name, int mode, boolean timeouts) throws IOException { return null; }
    public static InputStream openInputStream(String name) throws IOException { return null; }
    public static OutputStream openOutputStream(String name) throws IOException { return null; }
}
