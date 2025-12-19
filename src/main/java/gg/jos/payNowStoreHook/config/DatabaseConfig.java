package gg.jos.payNowStoreHook.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Objects;

public final class DatabaseConfig {

    private final String host;
    private final int port;
    private final String name;
    private final String username;
    private final String password;
    private final boolean useSSL;
    private final int maximumPoolSize;
    private final int minimumIdle;
    private final long connectionTimeoutMillis;

    private DatabaseConfig(
        String host,
        int port,
        String name,
        String username,
        String password,
        boolean useSSL,
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeoutMillis
    ) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.username = username;
        this.password = password;
        this.useSSL = useSSL;
        this.maximumPoolSize = maximumPoolSize;
        this.minimumIdle = minimumIdle;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }

    public static DatabaseConfig from(FileConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("database");
        if (section == null) {
            throw new IllegalStateException("database section missing from config.yml");
        }
        ConfigurationSection pool = section.getConfigurationSection("pool");
        return new DatabaseConfig(
            section.getString("host", "127.0.0.1"),
            section.getInt("port", 3306),
            sanitizeDbName(section.getString("name", "paynow")),
            section.getString("username", "paynow"),
            Objects.requireNonNullElse(section.getString("password"), ""),
            section.getBoolean("use_ssl", true),
            pool != null ? pool.getInt("maximum_size", 10) : 10,
            pool != null ? pool.getInt("minimum_idle", 2) : 2,
            pool != null ? pool.getLong("connection_timeout_millis", 10_000L) : 10_000L
        );
    }

    private static String sanitizeDbName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "paynow";
        }
        return raw.replace('`', '_');
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + name
            + "?useSSL=" + useSSL
            + "&allowPublicKeyRetrieval=true"
            + "&characterEncoding=UTF-8"
            + "&serverTimezone=UTC";
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public int maximumPoolSize() {
        return Math.max(1, maximumPoolSize);
    }

    public int minimumIdle() {
        return Math.max(0, Math.min(minimumIdle, maximumPoolSize()));
    }

    public long connectionTimeoutMillis() {
        return Math.max(1000L, connectionTimeoutMillis);
    }

    public String poolName() {
        return "PayNowStoreHook-" + name.toLowerCase(Locale.ENGLISH);
    }
}
