package ipod;

public final class MenuItem {

    public String label;
    public String value;          // right-aligned text on settings rows
    public boolean submenu;       // draw the chevron
    public int action;
    public int arg;               // menu id, or position within a track queue
    public int arg2;              // artist / album index for the menu being opened
    public int albumIdx = -1;     // drives the preview pane
    public int trackIdx = -1;
    public String path;           // file:// url, for the file browser

    public MenuItem(String label, int action, int arg, boolean submenu) {
        this.label = label;
        this.action = action;
        this.arg = arg;
        this.submenu = submenu;
    }

    public static MenuItem sub(String label, int action, int arg) {
        return new MenuItem(label, action, arg, true);
    }

    public static MenuItem act(String label, int action, int arg) {
        return new MenuItem(label, action, arg, false);
    }

    public MenuItem value(String v) {
        this.value = v;
        return this;
    }

    public MenuItem album(int idx) {
        this.albumIdx = idx;
        return this;
    }

    public MenuItem arg2(int v) {
        this.arg2 = v;
        return this;
    }

    public MenuItem path(String p) {
        this.path = p;
        return this;
    }

    public MenuItem track(int idx) {
        this.trackIdx = idx;
        return this;
    }
}
