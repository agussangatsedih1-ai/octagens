package com.octagens.plugin.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Number, money and duration formatting. */
public final class Fmt {
    /** When true {@link #money(double)} abbreviates (1.2M, 10B). */
    public static volatile boolean moneyShort = true;

    private static final String[] SUFFIX = {"", "K", "M", "B", "T", "Qa", "Qi"};
    private static final Pattern DURATION = Pattern.compile("^(\\d+)([smhdSMHD]?)$");
    private static final ThreadLocal<DecimalFormat> PLAIN =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> GROUPED =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)));

    private Fmt() {
    }

    /** Plain decimal without trailing zeros: 1.2, 120.5, 200. */
    public static String number(double v) {
        return PLAIN.get().format(v);
    }

    /** Abbreviated number: 1.2K, 1.2M, 10B. */
    public static String shorten(double v) {
        double x = v;
        int idx = 0;
        while (Math.abs(x) >= 1000 && idx < SUFFIX.length - 1) {
            x /= 1000.0;
            idx++;
        }
        return PLAIN.get().format(x) + SUFFIX[idx];
    }

    /** Money formatted according to {@link #moneyShort}. */
    public static String money(double v) {
        return moneyShort ? shorten(v) : GROUPED.get().format(v);
    }

    /** Ratio (0..1) as percent text: 85.5%. */
    public static String percent(double ratio) {
        return number(ratio * 100.0) + "%";
    }

    /** Seconds as "1d 2h 3m 4s". */
    public static String duration(long seconds) {
        if (seconds <= 0) return "0s";
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    /** Parses "30", "30s", "15m", "2h", "1d" to seconds; returns -1 when invalid. */
    public static long parseDuration(String in) {
        if (in == null) return -1;
        Matcher m = DURATION.matcher(in.trim());
        if (!m.matches()) return -1;
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2).toLowerCase(Locale.ROOT)) {
            case "m" -> n * 60;
            case "h" -> n * 3600;
            case "d" -> n * 86400;
            default -> n;
        };
    }

    /** Title-cases a material/enum name: DIAMOND_BLOCK -> Diamond Block. */
    public static String pretty(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).replace(':', ' ').split("[_ ]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
