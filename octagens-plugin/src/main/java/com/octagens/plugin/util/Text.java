package com.octagens.plugin.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text formatter. Converts MiniMessage-style tags (colors, decorations, gradients),
 * legacy {@code &} codes and hex ({@code &#RRGGBB}, {@code <#RRGGBB>}) into legacy
 * section-sign strings, so the same output works on Spigot and Paper.
 */
public final class Text {
    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z_#][a-zA-Z0-9_#:.,\\- ]*)>");
    private static final Pattern STRIP_CODES = Pattern.compile("\u00a7[0-9a-fk-orxA-FK-ORX]");
    private static final Pattern STRIP_AMP = Pattern.compile("&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]");
    private static final Map<String, String> NAMED_CODE = new HashMap<>();
    private static final Map<String, String> NAMED_HEX = new HashMap<>();
    private static final String HEXDIGITS = "0123456789abcdefABCDEF";

    static {
        named("black", '0', "000000");
        named("dark_blue", '1', "0000AA");
        named("dark_green", '2', "00AA00");
        named("dark_aqua", '3', "00AAAA");
        named("dark_red", '4', "AA0000");
        named("dark_purple", '5', "AA00AA");
        named("gold", '6', "FFAA00");
        named("gray", '7', "AAAAAA");
        named("grey", '7', "AAAAAA");
        named("dark_gray", '8', "555555");
        named("dark_grey", '8', "555555");
        named("blue", '9', "5555FF");
        named("green", 'a', "55FF55");
        named("aqua", 'b', "55FFFF");
        named("red", 'c', "FF5555");
        named("light_purple", 'd', "FF55FF");
        named("yellow", 'e', "FFFF55");
        named("white", 'f', "FFFFFF");
    }

    private static void named(String n, char code, String hex) {
        NAMED_CODE.put(n, "L:" + code);
        NAMED_HEX.put(n, hex);
    }

    private Text() {
    }

    private static final class State {
        final Deque<String> colors = new ArrayDeque<>();
        boolean bold, italic, underline, strike, obf;
        List<int[]> gradient;
        int gradIndex, gradTotal;
        String lastKey = "-";

        String color() {
            return colors.peek();
        }

        void setColor(String c) {
            if (!colors.isEmpty()) colors.pop();
            colors.push(c);
        }

        void clearDecos() {
            bold = italic = underline = strike = obf = false;
        }

        void reset() {
            colors.clear();
            clearDecos();
            gradient = null;
        }
    }

    /** Converts tags/legacy/hex to a legacy section-sign string. */
    public static String color(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder out = new StringBuilder(input.length() + 16);
        State st = new State();
        int n = input.length();
        int i = 0;
        while (i < n) {
            char c = input.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < n) {
                char d = input.charAt(i + 1);
                if (c == '&' && d == '#' && i + 8 <= n && isHex(input, i + 2, 6)) {
                    st.gradient = null;
                    st.setColor("H:" + input.substring(i + 2, i + 8).toLowerCase(Locale.ROOT));
                    st.clearDecos();
                    i += 8;
                    continue;
                }
                if (c == '\u00a7' && (d == 'x' || d == 'X') && i + 14 <= n && legacyHex(input, i)) {
                    StringBuilder hex = new StringBuilder();
                    for (int k = 0; k < 6; k++) hex.append(input.charAt(i + 3 + k * 2));
                    st.gradient = null;
                    st.setColor("H:" + hex.toString().toLowerCase(Locale.ROOT));
                    st.clearDecos();
                    i += 14;
                    continue;
                }
                char lc = Character.toLowerCase(d);
                if ("0123456789abcdef".indexOf(lc) >= 0) {
                    st.gradient = null;
                    st.setColor("L:" + lc);
                    st.clearDecos();
                    i += 2;
                    continue;
                }
                if ("klmnor".indexOf(lc) >= 0) {
                    switch (lc) {
                        case 'k' -> st.obf = true;
                        case 'l' -> st.bold = true;
                        case 'm' -> st.strike = true;
                        case 'n' -> st.underline = true;
                        case 'o' -> st.italic = true;
                        default -> st.reset();
                    }
                    i += 2;
                    continue;
                }
            }
            if (c == '<') {
                Matcher m = TAG.matcher(input);
                m.region(i, n);
                if (m.lookingAt() && handleTag(input, m, st, out)) {
                    i = m.end();
                    continue;
                }
            }
            emit(out, st, c);
            i++;
        }
        return out.toString();
    }

    private static boolean handleTag(String input, Matcher m, State st, StringBuilder out) {
        boolean closing = !m.group(1).isEmpty();
        String body = m.group(2).trim();
        String lower = body.toLowerCase(Locale.ROOT);
        String[] parts = lower.split(":");
        String name = parts[0];

        if (name.startsWith("#")) {
            if (closing) {
                if (!st.colors.isEmpty()) st.colors.pop();
                return true;
            }
            if (name.length() == 7 && isHex(name, 1, 6)) {
                st.colors.push("H:" + name.substring(1));
                return true;
            }
            return false;
        }
        if (NAMED_CODE.containsKey(name)) {
            if (closing) {
                if (!st.colors.isEmpty()) st.colors.pop();
            } else {
                st.colors.push(NAMED_CODE.get(name));
            }
            return true;
        }
        switch (name) {
            case "color", "colour", "c" -> {
                if (closing) {
                    if (!st.colors.isEmpty()) st.colors.pop();
                    return true;
                }
                if (parts.length < 2) return false;
                String col = parseColor(parts[1]);
                if (col == null) return false;
                st.colors.push(col);
                return true;
            }
            case "bold", "b" -> {
                st.bold = !closing;
                return true;
            }
            case "italic", "em", "i" -> {
                st.italic = !closing;
                return true;
            }
            case "underlined", "u" -> {
                st.underline = !closing;
                return true;
            }
            case "strikethrough", "st" -> {
                st.strike = !closing;
                return true;
            }
            case "obfuscated", "obf" -> {
                st.obf = !closing;
                return true;
            }
            case "reset", "r" -> {
                st.reset();
                return true;
            }
            case "newline", "br" -> {
                out.append('\n');
                return true;
            }
            case "gradient" -> {
                if (closing) {
                    st.gradient = null;
                    return true;
                }
                List<int[]> cols = new ArrayList<>();
                for (int k = 1; k < parts.length; k++) {
                    String hex = hexOf(parts[k]);
                    if (hex == null) return false;
                    cols.add(new int[]{
                            Integer.parseInt(hex.substring(0, 2), 16),
                            Integer.parseInt(hex.substring(2, 4), 16),
                            Integer.parseInt(hex.substring(4, 6), 16)});
                }
                if (cols.size() < 2) return false;
                int end = input.indexOf("</gradient>", m.end());
                String content = input.substring(m.end(), end < 0 ? input.length() : end);
                st.gradient = cols;
                st.gradIndex = 0;
                st.gradTotal = visibleLength(content);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static String parseColor(String s) {
        if (s.startsWith("#") && s.length() == 7 && isHex(s, 1, 6)) return "H:" + s.substring(1);
        return NAMED_CODE.get(s);
    }

    private static String hexOf(String s) {
        if (s.startsWith("#") && s.length() == 7 && isHex(s, 1, 6)) return s.substring(1);
        return NAMED_HEX.get(s);
    }

    private static boolean isHex(String s, int from, int len) {
        if (from + len > s.length()) return false;
        for (int k = from; k < from + len; k++) {
            if (HEXDIGITS.indexOf(s.charAt(k)) < 0) return false;
        }
        return true;
    }

    private static boolean legacyHex(String s, int i) {
        for (int k = 0; k < 6; k++) {
            if (s.charAt(i + 2 + k * 2) != '\u00a7') return false;
            if (HEXDIGITS.indexOf(s.charAt(i + 3 + k * 2)) < 0) return false;
        }
        return true;
    }

    private static int visibleLength(String s) {
        String t = TAG.matcher(s).replaceAll("");
        t = STRIP_AMP.matcher(t).replaceAll("");
        return STRIP_CODES.matcher(t).replaceAll("").length();
    }

    private static void emit(StringBuilder out, State st, char ch) {
        String col = st.color();
        if (st.gradient != null && ch != '\n') {
            int total = Math.max(1, st.gradTotal);
            double ratio = total <= 1 ? 0 : Math.min(1.0, (double) st.gradIndex / (total - 1));
            st.gradIndex++;
            int segs = st.gradient.size() - 1;
            double pos = ratio * segs;
            int seg = Math.min((int) Math.floor(pos), segs - 1);
            double local = pos - seg;
            int[] a = st.gradient.get(seg);
            int[] b = st.gradient.get(seg + 1);
            int r = (int) Math.round(a[0] + (b[0] - a[0]) * local);
            int g = (int) Math.round(a[1] + (b[1] - a[1]) * local);
            int bl = (int) Math.round(a[2] + (b[2] - a[2]) * local);
            col = "H:" + String.format("%02x%02x%02x", r, g, bl);
        }
        String key = (col == null ? "-" : col) + (st.bold ? "b" : "") + (st.italic ? "i" : "")
                + (st.underline ? "u" : "") + (st.strike ? "s" : "") + (st.obf ? "k" : "");
        if (!key.equals(st.lastKey)) {
            if (col == null) {
                out.append("\u00a7r");
            } else if (col.startsWith("H:")) {
                out.append("\u00a7x");
                for (int k = 2; k < 8; k++) out.append('\u00a7').append(col.charAt(k));
            } else {
                out.append('\u00a7').append(col.charAt(2));
            }
            if (st.bold) out.append("\u00a7l");
            if (st.italic) out.append("\u00a7o");
            if (st.underline) out.append("\u00a7n");
            if (st.strike) out.append("\u00a7m");
            if (st.obf) out.append("\u00a7k");
            st.lastKey = key;
        }
        out.append(ch);
    }

    /** Colorizes for item names/lore: prefixed with a reset so the default italic is removed. */
    public static String item(String input) {
        return "\u00a7r" + color(input);
    }

    /** Replaces {key} placeholders. */
    public static String replace(String in, Map<String, String> repl) {
        if (in == null || repl == null || repl.isEmpty()) return in == null ? "" : in;
        String out = in;
        for (Map.Entry<String, String> e : repl.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /** Removes color codes: for console output. */
    public static String strip(String s) {
        return s == null ? "" : STRIP_CODES.matcher(s).replaceAll("");
    }
}
