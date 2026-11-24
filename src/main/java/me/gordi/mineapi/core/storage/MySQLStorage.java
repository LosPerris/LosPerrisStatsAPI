package me.gordi.mineapi.core.storage;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MySQLStorage extends AbstractSQLStorage {

    private final String host, database, username, password;
    private final int port;
    private final boolean useSsl;

    public MySQLStorage(JavaPlugin plugin, FileConfiguration dbConfig) {
        super(plugin, dbConfig);
        
        this.host = dbConfig.getString("mysql.host", "localhost");
        this.port = dbConfig.getInt("mysql.port", 3306);
        this.database = dbConfig.getString("mysql.database", "minecraft_stats");
        this.username = dbConfig.getString("mysql.username", "root");
        this.password = dbConfig.getString("mysql.password", "");
        this.useSsl = dbConfig.getBoolean("mysql.use_ssl", false);
        this.tablePrefix = sanitizePrefix(dbConfig.getString("mysql.table_prefix", "stats_"));
    }

    @Override
    protected String label() {
        return "MySQL";
    }

    @Override
    protected String jdbcUrl() {
        // autoReconnect eliminado: HikariCP ya valida y recicla conexiones.
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl + "&serverTimezone=UTC&characterEncoding=UTF-8";
    }

    @Override
    protected void configure(HikariConfig config) {
        // El driver lo aporta el propio servidor (Spigot/Paper incluyen MySQL Connector/J)
        for (String driver : new String[]{"com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver"}) {
            try {
                Class.forName(driver);
                config.setDriverClassName(driver);
                return;
            } catch (ClassNotFoundException ignored) {
            }
        }
    }

    @Override
    protected String username() {
        return username;
    }

    @Override
    protected String password() {
        return password;
    }

    @Override
    protected String jsonColumnType() {
        return "JSON";
    }

    @Override
    protected String upsertPlayerSql(String table) {
        return "INSERT INTO " + table + " (uuid, name, data) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name), data = VALUES(data)";
    }

    @Override
    protected String upsertGlobalSql(String table) {
        return "INSERT INTO " + table + " (id, data) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE data = VALUES(data)";
    }

    @Override
    protected void bindPlayerUpsert(PreparedStatement ps, String uuid, String name, String json) throws SQLException {
        ps.setString(1, uuid);
        ps.setString(2, name);
        ps.setString(3, json);
    }

    @Override
    protected void bindGlobalUpsert(PreparedStatement ps, String id, String json) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, json);
    }
}
