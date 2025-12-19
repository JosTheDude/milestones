package gg.jos.payNowStoreHook.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.jos.payNowStoreHook.config.DatabaseConfig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;

public final class PlayerSpendStore {

    private static final String TABLE_NAME = "player_spend";

    private final JavaPlugin plugin;
    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerSpendStore(JavaPlugin plugin, DatabaseConfig config) {
        this.plugin = plugin;
        this.dataSource = createDataSource(config);
        this.executor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            new NamedThreadFactory("PayNowStoreHook-DB")
        );
        createTable();
    }

    public CompletableFuture<PlayerData> load(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        return supplyAsync(connection -> readData(connection, uuid, name))
            .thenApply(data -> {
                cache.put(uuid, data);
                return data;
            });
    }

    public void ensureLoaded(Player player) {
        UUID uuid = player.getUniqueId();
        if (cache.containsKey(uuid)) {
            return;
        }
        load(player).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to load data for " + player.getName(), throwable);
            return null;
        });
    }

    public CompletableFuture<PlayerData> addSpent(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        return supplyAsync(connection -> {
            upsertAdd(connection, uuid, name, Math.max(0.0, amount));
            return readExisting(connection, uuid, name);
        }).thenApply(data -> {
            cache.put(uuid, data);
            return data;
        });
    }

    public CompletableFuture<PlayerData> removeSpent(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        return supplyAsync(connection -> {
            ensureRow(connection, uuid, name);
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + TABLE_NAME + " SET spent = GREATEST(0, spent - ?), name = ? WHERE uuid = ?"
            )) {
                statement.setDouble(1, Math.max(0.0, amount));
                statement.setString(2, name);
                statement.setString(3, uuid.toString());
                statement.executeUpdate();
            }
            return readExisting(connection, uuid, name);
        }).thenApply(data -> {
            cache.put(uuid, data);
            return data;
        });
    }

    public CompletableFuture<Void> updateThresholdIndex(UUID uuid, String name, int newIndex) {
        int sanitizedIndex = Math.max(-1, newIndex);
        return runAsync(connection -> {
            ensureRow(connection, uuid, name);
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + TABLE_NAME + " SET threshold_index = ?, name = ? WHERE uuid = ?"
            )) {
                statement.setInt(1, sanitizedIndex);
                statement.setString(2, name);
                statement.setString(3, uuid.toString());
                statement.executeUpdate();
            }
            cache.compute(uuid, (ignored, previous) -> {
                double spent = previous == null ? 0.0 : previous.spent();
                return new PlayerData(spent, sanitizedIndex);
            });
        });
    }

    public double getCachedSpent(UUID uuid) {
        PlayerData data = cache.get(uuid);
        return data == null ? 0.0 : data.spent();
    }

    public void evict(UUID uuid) {
        cache.remove(uuid);
    }

    public void close() {
        executor.shutdownNow();
        dataSource.close();
    }

    private void createTable() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
                + "name VARCHAR(16) NOT NULL,"
                + "spent DOUBLE NOT NULL DEFAULT 0,"
                + "threshold_index INT NOT NULL DEFAULT -1,"
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ")");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize database", exception);
        }
    }

    private HikariDataSource createDataSource(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setPoolName(config.poolName());
        hikariConfig.setMaximumPoolSize(config.maximumPoolSize());
        hikariConfig.setMinimumIdle(config.minimumIdle());
        hikariConfig.setConnectionTimeout(config.connectionTimeoutMillis());
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(hikariConfig);
    }

    private PlayerData readData(Connection connection, UUID uuid, String name) throws SQLException {
        ensureRow(connection, uuid, name);
        return readExisting(connection, uuid, name);
    }

    private PlayerData readExisting(Connection connection, UUID uuid, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT spent, threshold_index FROM " + TABLE_NAME + " WHERE uuid = ?"
        )) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new PlayerData(resultSet.getDouble("spent"), resultSet.getInt("threshold_index"));
                }
            }
        }
        ensureRow(connection, uuid, name);
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT spent, threshold_index FROM " + TABLE_NAME + " WHERE uuid = ?"
        )) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new PlayerData(resultSet.getDouble("spent"), resultSet.getInt("threshold_index"));
                }
            }
        }
        return new PlayerData(0.0, -1);
    }

    private void ensureRow(Connection connection, UUID uuid, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + TABLE_NAME + " (uuid, name, spent, threshold_index) VALUES (?, ?, 0, -1) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name)"
        )) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    private void upsertAdd(Connection connection, UUID uuid, String name, double amount) throws SQLException {
        if (amount <= 0.0) {
            ensureRow(connection, uuid, name);
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + TABLE_NAME + " (uuid, name, spent, threshold_index) VALUES (?, ?, ?, -1) "
                + "ON DUPLICATE KEY UPDATE spent = spent + VALUES(spent), name = VALUES(name)"
        )) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setDouble(3, amount);
            statement.executeUpdate();
        }
    }

    private CompletableFuture<Void> runAsync(SqlConsumer<Connection> consumer) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                consumer.accept(connection);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
        return future.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Database operation failed", throwable);
            }
        });
    }

    private <T> CompletableFuture<T> supplyAsync(SqlFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return function.apply(connection);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Database query failed", throwable);
            }
        });
    }

    public record PlayerData(double spent, int thresholdIndex) {
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlConsumer<T> {
        void accept(T value) throws SQLException;
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String name;
        private int counter = 0;

        private NamedThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name + "-" + counter++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
