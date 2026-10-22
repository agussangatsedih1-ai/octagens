package com.octagens.plugin.manager;

import com.octagens.api.event.WeekendBonusEndEvent;
import com.octagens.api.event.WeekendBonusStartEvent;
import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.util.Fmt;
import com.octagens.plugin.util.Text;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weekend extra-drop bonus. The window is evaluated in {@code weekend-bonus.timezone} (UTC by default);
 * all texts are displayed in {@code weekend-bonus.display-timezone} (Asia/Jakarta = WIB).
 */
public final class WeekendManager {
    public enum Mode { RANDOM_PER_DROP, FIXED_PER_WEEKEND, FIXED_PER_DAY }

    private static final long WEEK_SECONDS = 7L * 86400L;

    private final OctaGensPlugin plugin;
    private boolean enabled;
    private ZoneId zone = ZoneId.of("UTC");
    private ZoneId displayZone = ZoneId.of("Asia/Jakarta");
    private long startSec, endSec; // seconds since Monday 00:00 in the zone; end is inclusive
    private int endMinuteOfWeek;
    private double min = 2, max = 4;
    private Mode mode = Mode.RANDOM_PER_DROP;
    private DateTimeFormatter displayFmt;
    private String displayLabel = "WIB";
    private boolean broadcastStart, broadcastEnd;
    private String displayType = "BOSSBAR";
    private BarColor barColor = BarColor.PURPLE;
    private BarStyle barStyle = BarStyle.SEGMENTED_10;
    private long manualDefaultSeconds = 3600;

    private boolean active;
    private double manualMultiplier = 1.0;
    private long manualUntil;
    private BossBar bar;
    private BukkitTask task;

    public WeekendManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("weekend-bonus.enabled", true);
        zone = zone(c.getString("weekend-bonus.timezone", "UTC"), "UTC");
        displayZone = zone(c.getString("weekend-bonus.display-timezone", "Asia/Jakarta"), "Asia/Jakarta");
        displayLabel = c.getString("weekend-bonus.display-label", "WIB");
        Locale loc = Locale.forLanguageTag(c.getString("weekend-bonus.display-locale", "id-ID"));
        try {
            displayFmt = DateTimeFormatter.ofPattern(c.getString("weekend-bonus.display-format", "EEEE HH:mm"), loc);
        } catch (IllegalArgumentException e) {
            displayFmt = DateTimeFormatter.ofPattern("EEEE HH:mm", loc);
        }
        int startMin = parseWeekMinute(c.getString("weekend-bonus.start", "SATURDAY 00:00"), 5 * 1440);
        endMinuteOfWeek = parseWeekMinute(c.getString("weekend-bonus.end", "SUNDAY 23:59"), 6 * 1440 + 1439);
        startSec = startMin * 60L;
        endSec = endMinuteOfWeek * 60L + 59;
        min = Math.max(0, c.getDouble("weekend-bonus.min-multiplier", 2));
        max = Math.max(min, c.getDouble("weekend-bonus.max-multiplier", 4));
        try {
            mode = Mode.valueOf(c.getString("weekend-bonus.mode", "RANDOM_PER_DROP").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("weekend-bonus.mode tidak valid, memakai RANDOM_PER_DROP");
            mode = Mode.RANDOM_PER_DROP;
        }
        broadcastStart = c.getBoolean("weekend-bonus.broadcast.start", true);
        broadcastEnd = c.getBoolean("weekend-bonus.broadcast.end", true);
        displayType = c.getString("weekend-bonus.display", "BOSSBAR").toUpperCase(Locale.ROOT);
        try {
            barColor = BarColor.valueOf(c.getString("weekend-bonus.bossbar.color", "PURPLE").toUpperCase(Locale.ROOT));
            barStyle = BarStyle.valueOf(c.getString("weekend-bonus.bossbar.style", "SEGMENTED_10").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            barColor = BarColor.PURPLE;
            barStyle = BarStyle.SEGMENTED_10;
        }
        long d = Fmt.parseDuration(c.getString("weekend-bonus.manual-default-duration", "1h"));
        manualDefaultSeconds = d > 0 ? d : 3600;
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
    }

    private ZoneId zone(String id, String fallback) {
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            plugin.getLogger().warning("Timezone tidak valid '" + id + "', memakai " + fallback);
            return ZoneId.of(fallback);
        }
    }

    private int parseWeekMinute(String s, int def) {
        try {
            String[] p = s.trim().split("\\s+");
            DayOfWeek d = DayOfWeek.valueOf(p[0].toUpperCase(Locale.ROOT));
            LocalTime t = LocalTime.parse(p[1]);
            return (d.getValue() - 1) * 1440 + t.getHour() * 60 + t.getMinute();
        } catch (Exception e) {
            plugin.getLogger().warning("Format waktu weekend-bonus salah: '" + s + "' (contoh: SATURDAY 00:00)");
            return def;
        }
    }

    public void start() {
        stop();
        update();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        if (bar != null) bar.removeAll();
    }

    // ---------------------------------------------------------------- state

    private long nowSecondOfWeek() {
        ZonedDateTime n = ZonedDateTime.now(zone);
        return (n.getDayOfWeek().getValue() - 1) * 86400L + n.toLocalTime().toSecondOfDay();
    }

    private boolean inWindow(long now) {
        if (startSec <= endSec) return now >= startSec && now <= endSec;
        return now >= startSec || now <= endSec;
    }

    private void update() {
        long now = nowSecondOfWeek();
        boolean a = enabled && inWindow(now);
        if (manualMultiplier > 1.0 && System.currentTimeMillis() > manualUntil) {
            manualMultiplier = 1.0;
            plugin.getLogger().info("Event bonus manual berakhir.");
        }
        if (a && !active) {
            active = true;
            Bukkit.getPluginManager().callEvent(new WeekendBonusStartEvent(mode.name(), min, max));
            if (broadcastStart) broadcast("weekend-start");
        } else if (!a && active) {
            active = false;
            Bukkit.getPluginManager().callEvent(new WeekendBonusEndEvent(mode.name()));
            if (broadcastEnd) broadcast("weekend-end");
        }
        renderDisplay();
    }

    private void broadcast(String key) {
        String[] kv = placeholders();
        for (Player p : Bukkit.getOnlinePlayers()) plugin.messages().send(p, key, kv);
        plugin.getLogger().info(Text.strip(plugin.messages().get(key, kv)).replace('\n', ' '));
    }

    private String[] placeholders() {
        return new String[]{"multiplier", displayMultiplier(), "start", windowStartText(), "end", windowEndText()};
    }

    private ZonedDateTime weekBase() {
        return ZonedDateTime.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay(zone);
    }

    private String windowStartText() {
        return weekBase().plusSeconds(startSec).withZoneSameInstant(displayZone).format(displayFmt) + " " + displayLabel;
    }

    private String windowEndText() {
        // end shown as the last active minute (Sunday 23:59 UTC = Monday 06:59 WIB)
        return weekBase().plusMinutes(endMinuteOfWeek).withZoneSameInstant(displayZone).format(displayFmt) + " " + displayLabel;
    }

    private void renderDisplay() {
        if (!isActive() || displayType.equals("NONE")) {
            if (bar != null) bar.removeAll();
            return;
        }
        String[] kv = placeholders();
        if (displayType.equals("ACTIONBAR")) {
            String msg = plugin.messages().get("weekend-actionbar", append(kv, "remaining", Fmt.duration(remainingSeconds())));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
            }
            return;
        }
        if (bar == null) bar = Bukkit.createBossBar("", barColor, barStyle);
        bar.setTitle(plugin.messages().get("weekend-bossbar", append(kv, "remaining", Fmt.duration(remainingSeconds()))));
        long total = startSec <= endSec ? endSec + 1 - startSec : WEEK_SECONDS - startSec + endSec + 1;
        double prog = total <= 0 ? 1 : Math.max(0, Math.min(1, remainingSeconds() / (double) total));
        bar.setProgress(prog);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }

    private static String[] append(String[] a, String k, String v) {
        String[] out = new String[a.length + 2];
        System.arraycopy(a, 0, out, 0, a.length);
        out[a.length] = k;
        out[a.length + 1] = v;
        return out;
    }

    public void onJoin(Player p) {
        if (bar != null && isActive()) bar.addPlayer(p);
    }

    public void onQuit(Player p) {
        if (bar != null) bar.removePlayer(p);
    }

    // ---------------------------------------------------------------- queries

    public boolean isActive() {
        return active;
    }

    public long remainingSeconds() {
        if (!active) return 0;
        long now = nowSecondOfWeek();
        long end = endSec + 1;
        return now <= endSec ? end - now : WEEK_SECONDS - now + end;
    }

    public boolean isManualActive() {
        return manualMultiplier > 1.0 && System.currentTimeMillis() <= manualUntil;
    }

    /** Multiplier to apply to one drop (weekend x manual). 1.0 when nothing is active. */
    public double dropMultiplier() {
        double m = 1.0;
        if (active) m *= weekendValue();
        if (isManualActive()) m *= manualMultiplier;
        return m;
    }

    private double weekendValue() {
        if (max <= min) return min;
        boolean whole = min == Math.rint(min) && max == Math.rint(max);
        if (mode == Mode.RANDOM_PER_DROP) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            return whole ? min + r.nextInt((int) (max - min) + 1) : min + r.nextDouble() * (max - min);
        }
        ZonedDateTime n = ZonedDateTime.now(zone);
        long seed = mode == Mode.FIXED_PER_DAY ? n.toLocalDate().toEpochDay()
                : Math.floorDiv(n.toLocalDate().toEpochDay() + 3, 7L);
        Random r = new Random(seed * 0x9E3779B97F4A7C15L + 17);
        if (whole) return min + r.nextInt((int) (max - min) + 1);
        return Math.round((min + r.nextDouble() * (max - min)) * 10.0) / 10.0;
    }

    /** Text for placeholders/messages: "2x-4x" for random mode, fixed value otherwise. */
    public String displayMultiplier() {
        double manual = isManualActive() ? manualMultiplier : 1.0;
        String base;
        if (mode == Mode.RANDOM_PER_DROP && max > min) base = Fmt.number(min) + "x-" + Fmt.number(max) + "x";
        else base = Fmt.number(weekendValue()) + "x";
        if (manual > 1.0) return active ? base + " (+event " + Fmt.number(manual) + "x)" : Fmt.number(manual) + "x";
        return base;
    }

    public void setManual(double multiplier, long seconds) {
        manualMultiplier = Math.max(1.0, multiplier);
        manualUntil = System.currentTimeMillis() + (seconds > 0 ? seconds : manualDefaultSeconds) * 1000L;
    }

    public void clearManual() {
        manualMultiplier = 1.0;
    }

    public long manualDefaultSeconds() {
        return manualDefaultSeconds;
    }

    public Map<String, String> info() {
        Map<String, String> m = new HashMap<>();
        m.put("multiplier", displayMultiplier());
        m.put("remaining", Fmt.duration(remainingSeconds()));
        return m;
    }
}
