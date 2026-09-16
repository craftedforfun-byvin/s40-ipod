package javax.microedition.io.file;
import java.util.Enumeration;
import java.util.Vector;
/** Test-only: reports whatever roots the harness points it at. */
public class FileSystemRegistry {
    private static final Vector roots = new Vector();
    private FileSystemRegistry() {}
    public static void setRoots(String[] r) {
        roots.removeAllElements();
        for (int i = 0; i < r.length; i++) roots.addElement(r[i]);
    }
    public static Enumeration listRoots() { return roots.elements(); }
    public static boolean addFileSystemListener(FileSystemListener l) { return false; }
    public static boolean removeFileSystemListener(FileSystemListener l) { return false; }
}
