package javax.microedition.lcdui;
import javax.microedition.midlet.MIDlet;
public class Display {
    private Display() {}
    public static Display getDisplay(MIDlet m) { return null; }
    public void setCurrent(Displayable d) {}
    public Displayable getCurrent() { return null; }
    public boolean flashBacklight(int millis) { return false; }
    public boolean vibrate(int millis) { return false; }
}
