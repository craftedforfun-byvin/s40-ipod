package ipod;

/**
 * The Hold switch dims the screen. Uses the Nokia UI API when the device has
 * it and quietly does nothing when it does not.
 */
public final class Backlight {

    private static boolean available = true;

    private Backlight() {}

    public static void off() {
        set(0);
    }

    public static void on() {
        set(100);
    }

    private static void set(int level) {
        if (!available) return;
        try {
            com.nokia.mid.ui.DeviceControl.setLights(0, level);
        } catch (Throwable e) {
            available = false;
        }
    }
}
