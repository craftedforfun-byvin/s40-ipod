package javax.microedition.io;
import java.io.*;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.file.FileConnection;
public class DesktopFile implements FileConnection {
    private final File f;
    public DesktopFile(String path) { this.f = new File(path); }
    public boolean exists() { return f.exists(); }
    public boolean isDirectory() { return f.isDirectory(); }
    public long fileSize() { return f.length(); }
    public long directorySize(boolean s) { return 0; }
    public String getName() { return f.getName(); }
    public String getPath() { return f.getParent(); }
    public String getURL() { return "file:///" + f.getAbsolutePath(); }
    public boolean canRead() { return f.canRead(); }
    public boolean isHidden() { return f.isHidden(); }
    public Enumeration list() { return list("*", false); }
    public Enumeration list(String filter, boolean hidden) {
        Vector v = new Vector();
        String[] kids = f.list();
        if (kids != null) for (String k : kids) v.addElement(new File(f, k).isDirectory() ? k + "/" : k);
        return v.elements();
    }
    public InputStream openInputStream() throws IOException { return new FileInputStream(f); }
    public OutputStream openOutputStream() throws IOException { return new FileOutputStream(f); }
    public void create() throws IOException { f.createNewFile(); }
    public void mkdir() { f.mkdirs(); }
    public void delete() { f.delete(); }
    public void close() {}
}
