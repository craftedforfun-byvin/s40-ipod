package javax.microedition.io.file;
import java.util.Enumeration;
public class FileSystemRegistry {
    private FileSystemRegistry() {}
    public static Enumeration listRoots() { return null; }
    public static boolean addFileSystemListener(FileSystemListener listener) { return false; }
    public static boolean removeFileSystemListener(FileSystemListener listener) { return false; }
}
