package ipod;

import javax.microedition.lcdui.Font;

/** Colours and fonts lifted from the iPod classic (6G) UI. */
public final class Theme {

    /* ---- chrome ---- */
    public static final int BAR_TOP      = 0xf6f6f6;
    public static final int BAR_BOTTOM   = 0xc6c6c6;
    public static final int BAR_LINE     = 0x8c8c8c;
    public static final int BAR_TEXT     = 0x333333;

    /* ---- list ---- */
    public static final int LIST_BG      = 0xffffff;
    public static final int LIST_TEXT    = 0x000000;
    public static final int LIST_DIM     = 0x707070;
    public static final int LIST_RULE    = 0xe2e2e2;
    public static final int CHEVRON      = 0x9a9a9a;

    /* ---- the blue selection bar ---- */
    public static final int SEL_HILITE   = 0xa9cdff;
    public static final int SEL_TOP      = 0x6ba6ef;
    public static final int SEL_MID      = 0x3d7fd8;
    public static final int SEL_BOTTOM   = 0x2a63bd;
    public static final int SEL_EDGE     = 0x1c4c9c;
    public static final int SEL_TEXT     = 0xffffff;
    public static final int SEL_SUBTEXT  = 0xd6e6ff;

    /* ---- preview pane ---- */
    public static final int PANE_TOP     = 0xffffff;
    public static final int PANE_BOTTOM  = 0xe4ebf5;
    public static final int PANE_DIV     = 0xb9b9b9;

    /* ---- now playing ---- */
    public static final int NP_TITLE     = 0x101010;
    public static final int NP_SUB       = 0x5c5c5c;
    public static final int NP_COUNT     = 0x808080;

    /* ---- scrub bar ---- */
    public static final int SCRUB_BG     = 0xdedede;
    public static final int SCRUB_SHADE  = 0xc0c0c0;
    public static final int SCRUB_EDGE   = 0x9d9d9d;
    public static final int SCRUB_F_TOP  = 0x8dbcf7;
    public static final int SCRUB_F_BOT  = 0x2d6bcc;
    public static final int KNOB_FILL    = 0xffffff;
    public static final int KNOB_EDGE    = 0x6f6f6f;

    /* ---- art ---- */
    public static final int ART_EDGE     = 0x9a9a9a;
    public static final int ART_EMPTY_A  = 0xdfe3e8;
    public static final int ART_EMPTY_B  = 0xb9c0c9;

    /* ---- cover flow ---- */
    public static final int CF_TOP       = 0x1b1b1b;
    public static final int CF_BOTTOM    = 0x000000;
    public static final int CF_TEXT      = 0xffffff;
    public static final int CF_SUB       = 0xb0b0b0;

    /* ---- battery ---- */
    public static final int BATT_EDGE    = 0x4a4a4a;
    public static final int BATT_FILL    = 0x5fbf00;

    public static final Font F_TINY  = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    public static final Font F_TINYB = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD,  Font.SIZE_SMALL);
    public static final Font F_BODY  = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
    public static final Font F_BODYB = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD,  Font.SIZE_MEDIUM);
    public static final Font F_BIG   = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD,  Font.SIZE_LARGE);

    private Theme() {}
}
