package ipod;

/**
 * String joining without the "+" operator.
 *
 * Compiling with -target 5 or later turns "a" + b into java.lang.StringBuilder,
 * which CLDC 1.1 does not have - the MIDlet would die with NoClassDefFoundError
 * on the phone. Everything here goes through StringBuffer instead, so the build
 * works whatever bytecode level the JDK produces.
 */
public final class Str {

    private Str() {}

    public static String cat(String a, String b) {
        StringBuffer sb = new StringBuffer();
        sb.append(a);
        sb.append(b);
        return sb.toString();
    }

    public static String cat(String a, String b, String c) {
        StringBuffer sb = new StringBuffer();
        sb.append(a);
        sb.append(b);
        sb.append(c);
        return sb.toString();
    }

    public static String cat(String a, String b, String c, String d) {
        StringBuffer sb = new StringBuffer();
        sb.append(a);
        sb.append(b);
        sb.append(c);
        sb.append(d);
        return sb.toString();
    }

    public static String cat(String a, String b, String c, String d, String e) {
        StringBuffer sb = new StringBuffer();
        sb.append(a);
        sb.append(b);
        sb.append(c);
        sb.append(d);
        sb.append(e);
        return sb.toString();
    }

    public static String num(int n) {
        return Integer.toString(n);
    }

    public static String num(long n) {
        return Long.toString(n);
    }

    /** "3 of 12", "1 / 24" and friends. */
    public static String pair(int a, String sep, int b) {
        StringBuffer sb = new StringBuffer();
        sb.append(a);
        sb.append(sep);
        sb.append(b);
        return sb.toString();
    }
}
