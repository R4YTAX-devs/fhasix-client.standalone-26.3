package com.fhasix.uitheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Product Sans font (font id "inter" is just the old internal name), used ONLY by Fhasix's own screens (title screen, Mod Menu, launcher, ...). It is a separate
 * font ("fhasixuitheme:inter"), so the normal game text and the in-game HUD are not touched.
 * Every string is upper-cased and keeps the normal text shadow when the caller asks for it.
 *
 * To resize the font change "size" in assets/fhasixuitheme/font/inter.json (bigger number = bigger text).
 * If text sits a little high or low inside buttons change Y_FIX (in GUI units).
 */
public final class NewFont {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("fhasixuitheme", "inter");
    /** Vertical nudge (GUI units) applied where text is centred inside buttons. */
    public static final int Y_FIX = 0;
    /** Text shadow: false = off everywhere this font is used. */
    public static final boolean SHADOW = false;

    private static Style style;
    private static Style titleSansStyle;
    private static Style titleInterStyle;

    /** Vertical nudge (GUI units) for the bigger title-screen text (Y_FIX is for the smaller general text). */
    public static final int Y_TITLE = 1;

    private NewFont() {}

    /** The ONE place that depends on how this Minecraft version names a text font. */
    private static Style style() {
        if (style == null) {
            style = Style.EMPTY.withFont(new FontDescription.Resource(ID));
        }
        return style;
    }

    /** true = every Fhasix screen shows capitals only; false = text keeps the case it was written in. */
    public static final boolean ALL_CAPS = true;

    private static Component make(String s, boolean upper) {
        String v = s == null ? "" : s;
        return Component.literal(upper ? v.toUpperCase(Locale.ROOT) : v).setStyle(style());
    }

    public static Component t(String s) {
        return make(s, ALL_CAPS);
    }

    /** Same font, but the text keeps its own capitalisation (used by the title-screen buttons). */
    public static void drawMixed(GuiGraphicsExtractor c, Font tr, String s, int x, int y, int color, boolean shadow) {
        c.text(tr, make(s, false), x, y, color, shadow && SHADOW);
    }

    public static int widthMixed(Font tr, String s) {
        return tr.width(make(s, false));
    }

    public static Component t(Component text) {
        return t(text == null ? "" : text.getString());
    }

    public static void draw(GuiGraphicsExtractor c, Font tr, String s, int x, int y, int color, boolean shadow) {
        c.text(tr, t(s), x, y, color, shadow && SHADOW);
    }

    public static void draw(GuiGraphicsExtractor c, Font tr, Component s, int x, int y, int color, boolean shadow) {
        c.text(tr, t(s), x, y, color, shadow && SHADOW);
    }

    public static void draw(GuiGraphicsExtractor c, Font tr, FormattedCharSequence s, int x, int y, int color, boolean shadow) {
        c.text(tr, s, x, y, color, shadow && SHADOW);
    }

    public static void drawCentered(GuiGraphicsExtractor c, Font tr, String s, int cx, int y, int color) {
        Component tx = t(s);
        c.text(tr, tx, cx - tr.width(tx) / 2, y, color, SHADOW);
    }

    public static void drawCentered(GuiGraphicsExtractor c, Font tr, Component s, int cx, int y, int color) {
        drawCentered(c, tr, s == null ? "" : s.getString(), cx, y, color);
    }

    public static void drawCentered(GuiGraphicsExtractor c, Font tr, FormattedCharSequence s, int cx, int y, int color) {
        c.text(tr, s, cx - tr.width(s) / 2, y, color, SHADOW);
    }

    public static int width(Font tr, String s) {
        return tr.width(t(s));
    }

    public static int width(Font tr, Component s) {
        return tr.width(t(s));
    }

    public static int width(Font tr, FormattedCharSequence s) {
        return tr.width(s);
    }

    // ------------------------------------------------------------ title-screen fonts
    // The title buttons use two fonts, like the reference: Product Sans (Singleplayer, Multiplayer, ...) and
    // Inter (STORE, QUIT GAME). Both are a little bigger than the general screen text.

    private static Style titleStyle(boolean inter) {
        if (inter) {
            if (titleInterStyle == null) {
                titleInterStyle = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("fhasixuitheme", "title_inter")));
            }
            return titleInterStyle;
        }
        if (titleSansStyle == null) {
            titleSansStyle = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("fhasixuitheme", "title_sans")));
        }
        return titleSansStyle;
    }

    private static Component titleText(String s, boolean inter, boolean upper) {
        String v = s == null ? "" : s;
        return Component.literal(upper ? v.toUpperCase(Locale.ROOT) : v).setStyle(titleStyle(inter));
    }

    public static void drawTitle(GuiGraphicsExtractor c, Font tr, String s, int x, int y, int color,
                                 boolean inter, boolean upper) {
        c.text(tr, titleText(s, inter, upper), x, y, color, SHADOW);
    }

    public static int widthTitle(Font tr, String s, boolean inter, boolean upper) {
        return tr.width(titleText(s, inter, upper));
    }

    // ------------------------------------------------------------ Mod Menu fonts (light weights, normal case)
    // Three weights (regular / medium / bold) at many sizes. The files ui_regular.ttf, ui_medium.ttf and
    // ui_bold.ttf in assets/fhasixuitheme/font/ can simply be replaced with Product Sans Regular / Medium /
    // Bold (same file names) - nothing else has to change.

    public static final int W_REG = 0;
    public static final int W_MED = 1;
    public static final int W_BOLD = 2;

    private static final Map<Integer, Style> UI_STYLES = new HashMap<>();

    private static Style uiStyle(int weight, float size) {
        int half = Math.max(11, Math.min(28, Math.round(size * 2f)));
        int key = weight * 100 + half;
        Style s = UI_STYLES.get(key);
        if (s == null) {
            String w = weight == W_BOLD ? "b" : weight == W_MED ? "m" : "r";
            s = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("fhasixuitheme", "ui_" + w + "_" + half)));
            UI_STYLES.put(key, s);
        }
        return s;
    }

    // Every frame the screens ask for the same strings in the same sizes: keep the Text objects and their
    // measured widths instead of building / measuring them again and again (this is a big part of the UI
    // smoothness on phones). The caches are emptied when they grow too large.
    private static final Map<Integer, Map<String, Component>> TEXT_CACHE = new HashMap<>();
    private static final Map<Integer, Map<String, Integer>> WIDTH_CACHE = new HashMap<>();
    private static int cacheEntries = 0;

    private static int styleKey(int weight, float size) {
        return weight * 100 + Math.max(11, Math.min(28, Math.round(size * 2f)));
    }

    private static void trimCaches() {
        if (cacheEntries > 6000) {
            TEXT_CACHE.clear();
            WIDTH_CACHE.clear();
            cacheEntries = 0;
        }
    }

    private static Component uiText(String s, int weight, float size) {
        String v = s == null ? "" : s;
        int key = styleKey(weight, size);
        Map<String, Component> m = TEXT_CACHE.get(key);
        Component t = m == null ? null : m.get(v);
        if (t == null) {
            trimCaches();
            m = TEXT_CACHE.computeIfAbsent(key, k -> new HashMap<>());
            t = Component.literal(v).setStyle(uiStyle(weight, size));
            m.put(v, t);
            cacheEntries++;
        }
        return t;
    }

    public static void drawUi(GuiGraphicsExtractor c, Font tr, String s, int x, int y, int color, int weight, float size) {
        c.text(tr, uiText(s, weight, size), x, y, color, false);
    }

    public static int widthUi(Font tr, String s, int weight, float size) {
        String v = s == null ? "" : s;
        int key = styleKey(weight, size);
        Map<String, Integer> m = WIDTH_CACHE.get(key);
        Integer w = m == null ? null : m.get(v);
        if (w == null) {
            trimCaches();
            m = WIDTH_CACHE.computeIfAbsent(key, k -> new HashMap<>());
            w = tr.width(uiText(v, weight, size));
            m.put(v, w);
            cacheEntries++;
        }
        return w;
    }

    /** y for drawUi so the text is vertically centred in a box (the text baseline sits 7 units below y). */
    public static int centerYUi(int boxY, int boxH, float size) {
        return Math.round(boxY + boxH / 2f - 7f + 0.35f * Math.max(5.5f, Math.min(14f, size)));
    }

    /** Cuts text with ".." so it fits maxW. */
    public static String fitUi(Font tr, String s, int weight, float size, int maxW) {
        if (widthUi(tr, s, weight, size) <= maxW) {
            return s;
        }
        String cut = s;
        while (cut.length() > 1 && widthUi(tr, cut + "..", weight, size) > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "..";
    }
}
