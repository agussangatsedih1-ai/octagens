package com.octagens.plugin.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** YAML loading helper: copies the bundled default if the file does not exist yet. */
public final class Yml {
    private Yml() {
    }

    public static YamlConfiguration load(JavaPlugin plugin, String name, boolean withDefaults) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            plugin.saveResource(name, false);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (withDefaults) {
            InputStream in = plugin.getResource(name);
            if (in != null) {
                y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        }
        return y;
    }
}
