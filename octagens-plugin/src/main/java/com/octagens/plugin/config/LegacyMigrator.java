package com.octagens.plugin.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Moves files of the old flat layout (1.0.0 - 1.0.3) into legacy/ so the new folder structure
 * (lang/, generators/, energy/, oregen/, sellwand/, guis/, logs/) is generated fresh.
 * config.yml and the database file stay where they are.
 */
public final class LegacyMigrator {
    private static final List<String> OLD_ROOT_FILES = List.of(
            "messages.yml", "generators.yml", "energy.yml", "energyshop.yml", "oregen.yml", "sellwand.yml");

    private LegacyMigrator() {
    }

    public static void run(JavaPlugin plugin) {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) return;
        File legacy = new File(dir, "legacy");
        int moved = 0;
        try {
            for (String name : OLD_ROOT_FILES) {
                File f = new File(dir, name);
                if (f.isFile()) {
                    legacy.mkdirs();
                    Files.move(f.toPath(), new File(legacy, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    moved++;
                }
            }
            // old GUI files have no {lang:...} tokens: back them up so the new 6-row menu etc. are generated
            File guiMain = new File(dir, "guis/main.yml");
            if (guiMain.isFile() && !Files.readString(guiMain.toPath(), StandardCharsets.UTF_8).contains("{lang:")) {
                legacy.mkdirs();
                File target = new File(legacy, "guis");
                if (target.exists()) deleteRecursively(target);
                Files.move(new File(dir, "guis").toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                moved++;
            }
            File oldLog = new File(dir, "sell-log.txt");
            File newLog = new File(dir, "logs/sell-log.txt");
            if (oldLog.isFile() && !newLog.exists()) {
                newLog.getParentFile().mkdirs();
                Files.move(oldLog.toPath(), newLog.toPath());
                moved++;
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Migrasi file lama gagal: " + e.getMessage());
        }
        if (moved > 0) {
            plugin.getLogger().info("Struktur folder baru: " + moved + " file/folder lama dipindah ke plugins/OctaGens/legacy/ "
                    + "(config.yml dan database tetap). Salin pengaturan kustom Anda ke file baru bila perlu.");
        }
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursively(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
