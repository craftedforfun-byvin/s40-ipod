package ipod;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

public final class IPodMIDlet extends MIDlet {

    public static final String VERSION = "1.0";

    private Display display;
    Shell shell;          // package-private: the loader thread below touches these
    Library lib;
    private Engine engine;
    private Settings settings;
    private ArtCache art;
    private boolean started;

    protected void startApp() {
        if (started) {
            display.setCurrent(shell);
            return;
        }
        started = true;

        display = Display.getDisplay(this);
        settings = new Settings();
        Store.loadSettings(settings);

        lib = new Library();
        art = new ArtCache(lib);
        engine = new Engine(lib, settings);
        shell = new Shell(this, lib, engine, settings, art);
        art.attach(shell);
        engine.attach(shell);

        shell.root(Menus.build(shell, Menus.M_ROOT, 0));
        display.setCurrent(shell);

        // Loading the cached library touches RMS, so keep it off the UI thread.
        new Thread(new Runnable() {
            public void run() {
                boolean cached = Store.loadLibrary(lib);
                if (cached && lib.tracks.size() > 0) {
                    shell.root(Menus.build(shell, Menus.M_ROOT, 0));
                } else {
                    shell.root(new ScanScreen());
                }
            }
        }).start();
    }

    protected void pauseApp() {
        // keep playing in the background; the shell just stops repainting
    }

    protected void destroyApp(boolean unconditional) {
        try { Store.saveSettings(settings); } catch (Throwable e) {}
        try { engine.stopAll(); } catch (Throwable e) {}
        try { art.stop(); } catch (Throwable e) {}
        try { shell.shutdown(); } catch (Throwable e) {}
        try { Backlight.on(); } catch (Throwable e) {}
    }

    public void quit() {
        destroyApp(true);
        notifyDestroyed();
    }
}
