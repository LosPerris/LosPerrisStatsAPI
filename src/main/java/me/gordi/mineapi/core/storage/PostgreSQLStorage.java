package me.gordi.mineapi.core.storage;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PostgreSQLStorage extends AbstractSQLStorage {

    private final String host, database, username, password;
    private final int port;

    public PostgreSQLStorage(JavaPlugin plugin, FileConfiguration dbConfig) {
        super(plugin, dbConfig);
        
        if (!dbConfig.isConfigurationSection("postgres")) {
            plugin.getLogger().warning("No existe la sección 'postgres:' en database.yml; se usarán los valores de 'mysql:'.");
        }
        this.host = dbConfig.getString("postgres.host", dbConfig.getString("mysql.host", "localhost"));
        // Si no hay postgres.port, solo se hereda mysql.port cuando no es el 3306 por defecto de MySQL
        int mysqlPort = dbConfig.getInt("mysql.port", 5432);
        this.port = dbConfig.getInt("postgres.port", mysqlPort == 3306 ? 5432 : mysqlPort);
        this.database = dbConfig.getString("postgres.database", dbConfig.getString("mysql.database", "postgres"));
        this.username = dbConfig.getString("postgres.username", dbConfig.getString("mysql.username", "postgres"));
        this.password = dbConfig.getString("postgres.password", dbConfig.getString("mysql.password", ""));
        this.tablePrefix = sanitizePrefix(dbConfig.getString("postgres.table_prefix", dbConfig.getString("mysql.table_prefix", "stats_")));
    }

    @Override
    protected String label() {
        return "PostgreSQL";
    }

    @Override
    protected String jdbcUrl() {
        // prepareThreshold=0: necesario con el "Transaction pooler" de Supabase (PgBouncer),
        // que no soporta prepared statements del lado del servidor.
        return "jdbc:postgresql://" + host + ":" + port + "/" + database + "?ssl=true&sslmode=require&prepareThreshold=0";
    }

    @Override
    protected void configure(HikariConfig config) {
        config.setDriverClassName("org.postgresql.Driver");
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
        return "JSONB";
    }

    @Override
    protected String upsertPlayerSql(String table) {
        return "INSERT INTO " + table + " (uuid, name, data) VALUES (?, ?, ?::jsonb) " +
                "ON CONFLICT (uuid) DO UPDATE SET name = EXCLUDED.name, data = EXCLUDED.data";
    }

    @Override
    protected String upsertGlobalSql(String table) {
        return "INSERT INTO " + table + " (id, data) VALUES (?, ?::jsonb) " +
                "ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data";
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
