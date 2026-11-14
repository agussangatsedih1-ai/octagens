package com.octagens.plugin.config;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.util.Text;
import com.octagens.plugin.util.Yml;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Language files (lang/id.yml, en.yml, zh.yml, ja.yml, es.yml). The language is chosen with general.language.
 * Lookup order: selected file in the data folder -> bundled copy of the selected language -> bundled English.
 */
public final class Messages {
    public static final List<String> LANGUAGES = List.of("id", "en", "zh", "ja", "es");
    private static final Pattern LANG_TOKEN = Pattern.compile("\\{lang:([a-zA-Z0-9_.\\-]+)}");

    private final OctaGensPlugin plugin;
    private YamlConfiguration cfg;
    private YamlConfiguration english;
    private String code = "id";

    public Messages(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    /** Maps "en_US", "zh-CN", "ja_JP", "es-ES", "id-ID"... to one of the five supported codes (default id). */
    public static String normalize(String input) {
        if (input == null) return "id";
        String s = input.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        String base = s.contains("-") ? s.substring(0, s.indexOf('-')) : s;
        if (base.equals("in")) base = "id";
        if (base.equals("jp")) base = "ja";
        if (base.equals("cn")) base = "zh";
        return LANGUAGES.contains(base) ? base : "id";
    }

    public String code() {
        return code;
    }

    public void load() {
        for (String c : LANGUAGES) {
            java.io.File f = new java.io.File(plugin.getDataFolder(), "lang/" + c + ".yml");
            if (!f.exists()) plugin.saveResource("lang/" + c + ".yml", false);
        }
        String wanted = plugin.settings().language;
        code = normalize(wanted);
        if (wanted != null && !LANGUAGES.contains(wanted.toLowerCase(Locale.ROOT)) && !normalize(wanted).equals(wanted.toLowerCase(Locale.ROOT))) {
            plugin.getLogger().warning("general.language '" + wanted + "' tidak dikenal. Pilihan: id, en, zh, ja, es. Memakai '" + code + "'.");
        }
        cfg = Yml.load(plugin, "lang/" + code + ".yml", true);
        InputStream in = plugin.getResource("lang/en.yml");
        english = in == null ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private boolean has(String key) {
        return cfg.contains(key) || english.contains(key);
    }

    /** Raw (uncolored) message; lists are joined with newlines. */
    public String raw(String key) {
        if (cfg.isList(key)) return String.join("\n", cfg.getStringList(key));
        String s = cfg.getString(key);
        if (s != null) return s;
        if (english.isList(key)) return String.join("\n", english.getStringList(key));
        s = english.getString(key);
        return s == null ? "<red>Missing message: " + key : s;
    }

    /** Raw lines of a list entry. */
    public List<String> rawList(String key) {
        if (cfg.isList(key)) return cfg.getStringList(key);
        if (english.isList(key)) return english.getStringList(key);
        return List.of(raw(key));
    }

    /** Replaces {lang:key} tokens with the raw text of the key in the active language. */
    public String resolveLang(String in) {
        if (in == null || in.indexOf("{lang:") < 0) return in;
        Matcher m = LANG_TOKEN.matcher(in);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            m.appendReplacement(sb, Matcher.quoteReplacement(has(key) ? raw(key) : "<red>" + key));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        m.put("prefix", raw("prefix"));
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    /** Colored message with placeholders applied (lines separated by \n). */
    public String get(String key, String... kv) {
        return Text.color(Text.replace(resolveLang(raw(key)), map(kv)));
    }

    /** Colored lines. */
    public List<String> lines(String key, String... kv) {
        List<String> out = new ArrayList<>();
        for (String l : get(key, kv).split("\n")) out.add(l);
        return out;
    }

    public void send(CommandSender to, String key, String... kv) {
        String prefix = get("prefix");
        for (String line : lines(key, kv)) {
            if (!line.isEmpty()) {
                if (!line.contains("OctaGens") && !line.contains(prefix)) line = prefix + line;
                to.sendMessage(line);
            }
        }
    }
}
