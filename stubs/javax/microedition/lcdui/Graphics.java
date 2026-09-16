package javax.microedition.lcdui;
public class Graphics {
    public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8, TOP = 16, BOTTOM = 32, BASELINE = 64;
    public static final int SOLID = 0, DOTTED = 1;
    Graphics() {}
    public void setColor(int RGB) {}
    public void setColor(int red, int green, int blue) {}
    public int getColor() { return 0; }
    public void setGrayScale(int value) {}
    public void setFont(Font font) {}
    public Font getFont() { return null; }
    public void setStrokeStyle(int style) {}
    public int getStrokeStyle() { return 0; }
    public void setClip(int x, int y, int width, int height) {}
    public void clipRect(int x, int y, int width, int height) {}
    public int getClipX() { return 0; }
    public int getClipY() { return 0; }
    public int getClipWidth() { return 0; }
    public int getClipHeight() { return 0; }
    public void translate(int x, int y) {}
    public int getTranslateX() { return 0; }
    public int getTranslateY() { return 0; }
    public void drawLine(int x1, int y1, int x2, int y2) {}
    public void fillRect(int x, int y, int width, int height) {}
    public void drawRect(int x, int y, int width, int height) {}
    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {}
    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {}
    public void fillArc(int x, int y, int w, int h, int startAngle, int arcAngle) {}
    public void drawArc(int x, int y, int w, int h, int startAngle, int arcAngle) {}
    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {}
    public void drawString(String str, int x, int y, int anchor) {}
    public void drawSubstring(String str, int offset, int len, int x, int y, int anchor) {}
    public void drawChar(char character, int x, int y, int anchor) {}
    public void drawChars(char[] data, int offset, int length, int x, int y, int anchor) {}
    public void drawImage(Image img, int x, int y, int anchor) {}
    public void drawRegion(Image src, int x_src, int y_src, int width, int height, int transform, int x_dest, int y_dest, int anchor) {}
    public void drawRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha) {}
    public void copyArea(int x_src, int y_src, int width, int height, int x_dest, int y_dest, int anchor) {}
}
