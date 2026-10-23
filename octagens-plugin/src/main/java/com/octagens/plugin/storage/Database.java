package com.octagens.plugin.storage;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.config.Settings;
import com.octagens.plugin.util.BlockKey;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite / MySQL storage. All I/O runs on one dedicated thread (keeps write ordering);
 * callers on the main thread never block.
 */
public final class Database {
    public record BlockRow(String world, int x, int y, int z, UUID owner, String ownerName, String type, long placedAt) {
    }

    public record PlayerRow(UUID uuid, String name, int bonusGenerators, int bonusEnergy, double mcuMax, double mcuCurrent) {
    }

    public record LoadResult(List<BlockRow> generators, List<BlockRow> energies, List<PlayerRow> players) {
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection c) throws SQLException;
    }

    private final OctaGensPlugin plugin;
    private HikariDataSource ds;
    private ExecutorService exec;

    public Database(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    /** Opens the pool and creates tables (async). */
    public CompletableFuture<Void> init() {
        exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OctaGens-DB");
            t.setDaemon(true);
            return t;
        });
        return CompletableFuture.runAsync(() -> {
            try {
                Settings s = plugin.settings();
                HikariConfig hc = new HikariConfig();
                hc.setPoolName("OctaGens-Hikari");
                hc.setConnectionTimeout(10_000);
                boolean sqlite = !s.dbType.equals("MYSQL") && !s.dbType.equals("MARIADB");
                if (sqlite) {
                    File f = new File(plugin.getDataFolder(), s.sqliteFile);
                    hc.setJdbcUrl("jdbc:sqlite:" + f.getAbsolutePath());
                    hc.setDriverClassName("org.sqlite.JDBC");
                    hc.setMaximumPoolSize(1);
                } else {
                    hc.setJdbcUrl("jdbc:mariadb://" + s.myHost + ":" + s.myPort + "/" + s.myDatabase
                            + "?sslMode=" + (s.mySsl ? "trust" : "disable"));
                    hc.setDriverClassName("org.mariadb.jdbc.Driver");
                    hc.setUsername(s.myUser);
                    hc.setPassword(s.myPass);
                    hc.setMaximumPoolSize(s.myPool);
                }
                ds = new HikariDataSource(hc);
                try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                    if (sqlite) {
                        st.execute("PRAGMA journal_mode=WAL");
                        st.execute("PRAGMA synchronous=NORMAL");
                    }
                    String cols = "world VARCHAR(64) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, "
                            + "owner VARCHAR(36) NOT NULL, owner_name VARCHAR(32), type VARCHAR(64) NOT NULL, "
                            + "placed_at BIGINT NOT NULL, PRIMARY KEY (world, x, y, z)";
                    st.execute("CREATE TABLE IF NOT EXISTS octagens_generators (" + cols + ")");
                    st.execute("CREATE TABLE IF NOT EXISTS octagens_energy (" + cols + ")");
                    st.execute("CREATE TABLE IF NOT EXISTS octagens_players (uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                            + "name VARCHAR(32), bonus_generators INT NOT NULL DEFAULT 0, bonus_energy INT NOT NULL DEFAULT 0, "
                            + "mcu_max DOUBLE NOT NULL DEFAULT 0, mcu_current DOUBLE NOT NULL DEFAULT 0)");
                    for (String col : new String[]{"mcu_max", "mcu_current"}) {
                        try {
                            st.execute("ALTER TABLE octagens_players ADD COLUMN " + col + " DOUBLE NOT NULL DEFAULT 0");
                        } catch (SQLException ignored) {
                            // column already exists
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Gagal membuka database: " + e.getMessage(), e);
            }
        }, exec);
    }

    public CompletableFuture<LoadResult> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = ds.getConnection()) {
                return new LoadResult(readBlocks(c, "octagens_generators"), readBlocks(c, "octagens_energy"), readPlayers(c));
            } catch (SQLException e) {
                throw new RuntimeException("Gagal memuat data: " + e.getMessage(), e);
            }
        }, exec);
    }

    private List<BlockRow> readBlocks(Connection c, String table) throws SQLException {
        List<BlockRow> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT world,x,y,z,owner,owner_name,type,placed_at FROM " + table)) {
            while (rs.next()) {
                try {
                    out.add(new BlockRow(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                            UUID.fromString(rs.getString(5)), rs.getString(6), rs.getString(7), rs.getLong(8)));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Baris rusak di " + table + " dilewati.");
                }
            }
        }
        return out;
    }

    private List<PlayerRow> readPlayers(Connection c) throws SQLException {
        List<PlayerRow> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid,name,bonus_generators,bonus_energy,mcu_max,mcu_current FROM octagens_players")) {
            while (rs.next()) {
                try {
                    out.add(new PlayerRow(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getDouble(5), rs.getDouble(6)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return out;
    }

    private void run(String what, SqlWork work) {
        if (exec == null || exec.isShutdown() || ds == null) return;
        try {
            exec.execute(() -> {
                try (Connection c = ds.getConnection()) {
                    work.run(c);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Database error (" + what + ")", e);
                }
            });
        } catch (RuntimeException ignored) {
            // executor already shut down
        }
    }

    public void saveGenerator(BlockRow r) {
        saveBlock("octagens_generators", r);
    }

    public void saveEnergy(BlockRow r) {
        saveBlock("octagens_energy", r);
    }

    private void saveBlock(String table, BlockRow r) {
        run("save " + table, c -> {
            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + table
                    + " (world,x,y,z,owner,owner_name,type,placed_at) VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, r.world());
                ps.setInt(2, r.x());
                ps.setInt(3, r.y());
                ps.setInt(4, r.z());
                ps.setString(5, r.owner().toString());
                ps.setString(6, r.ownerName());
                ps.setString(7, r.type());
                ps.setLong(8, r.placedAt());
                ps.executeUpdate();
            }
        });
    }

    public void deleteGenerator(BlockKey k) {
        deleteBlock("octagens_generators", k);
    }

    public void deleteEnergy(BlockKey k) {
        deleteBlock("octagens_energy", k);
    }

    private void deleteBlock(String table, BlockKey k) {
        run("delete " + table, c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, k.world());
                ps.setInt(2, k.x());
                ps.setInt(3, k.y());
                ps.setInt(4, k.z());
                ps.executeUpdate();
            }
        });
    }

    public void savePlayer(PlayerRow p) {
        run("save player", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "REPLACE INTO octagens_players (uuid,name,bonus_generators,bonus_energy,mcu_max,mcu_current) VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, p.uuid().toString());
                ps.setString(2, p.name());
                ps.setInt(3, p.bonusGenerators());
                ps.setInt(4, p.bonusEnergy());
                ps.setDouble(5, p.mcuMax());
                ps.setDouble(6, p.mcuCurrent());
                ps.executeUpdate();
            }
        });
    }

    /** Waits for queued writes (max 10s) and closes the pool. Called on disable. */
    public void close() {
        if (exec != null) {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(10, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Antrean database belum selesai setelah 10 detik.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (ds != null) ds.close();
    }
}
