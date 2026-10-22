package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.util.Text;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Console + file logging of sales/purchases. File writes are async. */
public final class TransactionLog {
    private final OctaGensPlugin plugin;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OctaGens-Log");
        t.setDaemon(true);
        return t;
    });

    public TransactionLog(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    /** @param sale true for a sell, false for a purchase */
    public void log(boolean sale, String player, String text, double amount) {
        String line = Text.strip(text);
        if (amount >= plugin.settings().largeTransaction && plugin.settings().largeTransaction > 0) {
            plugin.getLogger().warning("[Transaksi besar] " + player + ": " + line);
        } else if (plugin.settings().debug) {
            plugin.getLogger().info("[" + (sale ? "SELL" : "BUY") + "] " + player + ": " + line);
        }
        boolean toFile = sale ? plugin.settings().logSell : plugin.settings().logPurchase;
        if (!toFile) return;
        String out = Instant.now() + " [" + (sale ? "SELL" : "BUY") + "] " + player + " " + line + System.lineSeparator();
        File f = new File(plugin.getDataFolder(), plugin.settings().logFile);
        try {
            exec.execute(() -> {
                try {
                    Files.writeString(f.toPath(), out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    plugin.getLogger().warning("Gagal menulis " + f.getName() + ": " + e.getMessage());
                }
            });
        } catch (RuntimeException ignored) {
            // shut down
        }
    }

    public void close() {
        exec.shutdown();
        try {
            exec.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
