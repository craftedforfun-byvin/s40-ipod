package javax.microedition.io.file;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import javax.microedition.io.Connection;
public interface FileConnection extends Connection {
    boolean exists();
    boolean isDirectory();
    long fileSize() throws IOException;
    long directorySize(boolean includeSubDirs) throws IOException;
    String getName();
    String getPath();
    String getURL();
    boolean canRead();
    boolean isHidden();
    Enumeration list() throws IOException;
    Enumeration list(String filter, boolean includeHidden) throws IOException;
    InputStream openInputStream() throws IOException;
    OutputStream openOutputStream() throws IOException;
    void create() throws IOException;
    void mkdir() throws IOException;
    void delete() throws IOException;
    void close() throws IOException;
}
