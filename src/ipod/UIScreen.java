package ipod;

import javax.microedition.lcdui.Graphics;

/** A screen in the shell's stack. Coordinates are relative to the content area. */
public abstract class UIScreen {

    protected Shell shell;
    protected String title = "iPod";

    public void bind(Shell s) {
        this.shell = s;
    }

    public String title() {
        return title;
    }

    /** Content only - the shell has already drawn the status bar. */
    public abstract void paint(Graphics g, int w, int h);

    public void key(int action, int code) {}

    public void keyRepeat(int action, int code) {
        key(action, code);
    }

    /** Return true to ask for another animation frame. */
    public boolean tick() {
        return false;
    }

    public void onShow() {}

    public void onHide() {}
}
