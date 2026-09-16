package javax.microedition.lcdui;
public abstract class Displayable {
    Displayable() {}
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public boolean isShown() { return false; }
    protected void sizeChanged(int w, int h) {}
}
