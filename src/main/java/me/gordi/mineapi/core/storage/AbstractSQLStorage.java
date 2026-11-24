package me.gordi.mineapi.core.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lógica común de MySQL y PostgreSQL/Supabase (pool HikariCP, tablas players/global).
 */
public abstract class AbstractSQLStorage implements StorageProvider {

    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9_]{0,32}");

    protected final JavaPlugin plugin;
    protected final FileConfiguration dbConfig;
    protected volatile HikariDataSource dataSource;
    protected String tablePrefix;

    protected AbstractSQLStorage(JavaPlugin plugin, FileConfiguration dbConfig) {
        this.plugin = plugin;
        this.dbConfig = dbConfig;
    }

    /** Nombre legible para logs. */
    protected abstract String label();

    protected abstract String jdbcUrl();

    protected abstract String username();

    protected abstract String password();

    /** Tipo de la columna data (JSON / JSONB). */
    protected abstract String jsonColumnType();

    protected abstract String upsertPlayerSql(String table);

    protected abstract String upsertGlobalSql(String table);

    /** Rellena los parámetros del upsert de jugador. */
    protected abstract void bindPlayerUpsert(PreparedStatement ps, String uuid, String name, String json) throws SQLException;

    /** Rellena los parámetros del upsert global. */
    protected abstract void bindGlobalUpsert(PreparedStatement ps, String id, String json) throws SQLException;

    protected void configure(HikariConfig config) {
    }

    protected String sanitizePrefix(String prefix) {
        if (prefix == null || !SAFE_PREFIX.matcher(prefix).matches()) {
            plugin.getLogger().warning("table_prefix '" + prefix + "' no es válido (solo letras, números y _). Se usará 'stats_'.");
            return "stats_";
        }
        return prefix;
    }

    @Override
    public void init() {
        try {
            openConnection();
            createTables();
        } catch (Exception e) {
            shutdown();
            throw new IllegalStateException("No se pudo inicializar " + label() + ": " + e.getMessage(), e);
        }
    }

    private void openConnection() {
        if (dataSource != null && !dataSource.isClosed()) return;
        
        // Lee los valores del pool desde database.yml
        int maxConnections = Math.max(1, dbConfig.getInt("pool.max_connections", 10));
        int minIdle = Math.max(0, Math.min(maxConnections, dbConfig.getInt("pool.min_idle", 2)));
        long timeout = Math.max(250L, dbConfig.getLong("pool.timeout", 5000));
        
        HikariConfig config = new HikariConfig();
        config.setPoolName("LosPerris-" + label());
        config.setJdbcUrl(jdbcUrl());
        config.setUsername(username());
        config.setPassword(password());
        config.setMaximumPoolSize(maxConnections);
        config.setMinimumIdle(minIdle);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(timeout);
        configure(config);
        
        dataSource = new HikariDataSource(config);
    }

    private void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "name VARCHAR(16), " +
                    "data " + jsonColumnType() +
                    ")");
            
            s.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "global (" +
                    "id VARCHAR(32) PRIMARY KEY, " +
                    "data " + jsonColumnType() +
                    ")");
        }
    }

    @Override
    public void shutdown() {
        HikariDataSource ds = dataSource;
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    @Override
    public boolean ping() {
        if (!ready()) return false;
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean ready() {
        HikariDataSource ds = dataSource;
        return ds != null && !ds.isClosed();
    }

    private JsonObject parse(String raw, String what) {
        if (raw == null) return null;
        try {
            JsonElement element = JsonParser.parseString(raw);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException | IllegalStateException e) {
            plugin.getLogger().severe("JSON inválido en " + label() + " (" + what + "): " + e.getMessage());
            return null;
        }
    }

    @Override
    public JsonObject loadPlayerStats(UUID uuid) {
        if (!ready()) return null;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT data FROM " + tablePrefix + "players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return parse(rs.getString("data"), uuid.toString());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error cargando SQL (" + label() + ") para " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public void savePlayerStats(UUID uuid, String name, JsonObject data) {
        if (!ready()) return;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(upsertPlayerSql(tablePrefix + "players"))) {
            bindPlayerUpsert(ps, uuid.toString(), name, data.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error guardando SQL (" + label() + ") para " + name + ": " + e.getMessage());
        }
    }

    @Override
    public void deletePlayerStats(UUID uuid) {
        if (!ready()) return;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM " + tablePrefix + "players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error borrando SQL (" + label() + ") para " + uuid + ": " + e.getMessage());
        }
    }

    @Override
    public JsonObject loadGlobalStats() {
        if (!ready()) return new JsonObject();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT data FROM " + tablePrefix + "global WHERE id = 'all_stats'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                JsonObject json = parse(rs.getString("data"), "all_stats");
                if (json != null) return json;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error cargando Global SQL (" + label() + "): " + e.getMessage());
        }
        return new JsonObject();
    }

    @Override
    public void saveGlobalStats(JsonObject data) {
        saveGlobalRow("all_stats", data);
    }

    @Override
    public void saveServerInfo(JsonObject data) {
        saveGlobalRow("server_info", data);
    }

    private void saveGlobalRow(String id, JsonObject data) {
        if (!ready()) return;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(upsertGlobalSql(tablePrefix + "global"))) {
            bindGlobalUpsert(ps, id, data.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error guardando " + id + " SQL (" + label() + "): " + e.getMessage());
        }
    }
}
