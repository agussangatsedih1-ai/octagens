package com.octagens.plugin.config;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.util.Text;
import com.octagens.plugin.util.Yml;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** messages.yml accessor. Missing keys fall back to the bundled defaults. */
public final class Messages {
    private final OctaGensPlugin plugin;
    private YamlConfiguration cfg;

    public Messages(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        cfg = Yml.load(plugin, "messages.yml", true);
    }

    /** Raw (uncolored) message or list joined with newlines. */
    public String raw(String key) {
        if (cfg.isList(key)) return String.join("\n", cfg.getStringList(key));
        String s = cfg.getString(key);
        return s == null ? "<red>Missing message: " + key : s;
    }

    private Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        m.put("prefix", cfg.getString("prefix", ""));
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    /** Colored message with placeholders applied (lines separated by \n). */
    public String get(String key, String... kv) {
        return Text.color(Text.replace(raw(key), map(kv)));
    }

    /** Colored lines. */
    public List<String> lines(String key, String... kv) {
        List<String> out = new ArrayList<>();
        for (String l : get(key, kv).split("\n")) out.add(l);
        return out;
    }

    public void send(CommandSender to, String key, String... kv) {
        for (String line : lines(key, kv)) {
            if (!line.isEmpty()) to.sendMessage(line);
        }
    }
}
