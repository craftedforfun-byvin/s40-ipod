package ipod;

import java.util.Vector;

/** Stable merge sort - CLDC has no Collections.sort. */
public final class Sort {

    public interface Cmp {
        int compare(Object a, Object b);
    }

    private Sort() {}

    public static void sort(Vector v, Cmp c) {
        int n = v.size();
        if (n < 2) return;
        Object[] a = new Object[n];
        v.copyInto(a);
        Object[] tmp = new Object[n];
        mergeSort(a, tmp, 0, n - 1, c);
        for (int i = 0; i < n; i++) v.setElementAt(a[i], i);
    }

    private static void mergeSort(Object[] a, Object[] tmp, int lo, int hi, Cmp c) {
        if (lo >= hi) return;
        int mid = (lo + hi) / 2;
        mergeSort(a, tmp, lo, mid, c);
        mergeSort(a, tmp, mid + 1, hi, c);
        int i = lo, j = mid + 1, k = lo;
        while (i <= mid && j <= hi) {
            tmp[k++] = (c.compare(a[j], a[i]) < 0) ? a[j++] : a[i++];
        }
        while (i <= mid) tmp[k++] = a[i++];
        while (j <= hi) tmp[k++] = a[j++];
        for (int x = lo; x <= hi; x++) a[x] = tmp[x];
    }

    /** Case-insensitive, and "The Beatles" files under B like iTunes does. */
    public static int name(String a, String b) {
        return strip(a).compareTo(strip(b));
    }

    private static String strip(String s) {
        if (s == null) return "";
        s = s.toLowerCase();
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') i++;
        s = s.substring(i);
        if (s.length() > 4 && s.substring(0, 4).equals("the ")) return s.substring(4);
        if (s.length() > 3 && s.substring(0, 3).equals("an ")) return s.substring(3);
        if (s.length() > 2 && s.substring(0, 2).equals("a ")) return s.substring(2);
        return s;
    }
}
