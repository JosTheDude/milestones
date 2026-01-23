package gg.jos.paynowstorehook;

import co.aikar.commands.PaperCommandManager;
import gg.jos.paynowstorehook.command.PayNowStoreCommand;
import gg.jos.paynowstorehook.config.DatabaseConfig;
import gg.jos.paynowstorehook.data.PlayerSpendStore;
import gg.jos.paynowstorehook.listener.PlayerDataListener;
import gg.jos.paynowstorehook.message.Messages;
import gg.jos.paynowstorehook.placeholder.PayNowPlaceholderExpansion;
import gg.jos.paynowstorehook.threshold.ThresholdLoader;
import gg.jos.paynowstorehook.threshold.ThresholdService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class PayNowStoreHook extends JavaPlugin {

    private PlayerSpendStore spendStore;
    private ThresholdService thresholdService;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            DatabaseConfig databaseConfig = DatabaseConfig.from(getConfig());
            spendStore = new PlayerSpendStore(this, databaseConfig);
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize database. Disabling plugin.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        thresholdService = new ThresholdService(spendStore, new ThresholdLoader(getConfig()).load());
        messages = new Messages(getConfig());
        registerCommand();
        registerPlaceholders();
        Bukkit.getPluginManager().registerEvents(new PlayerDataListener(spendStore), this);
        preloadOnlinePlayers();
        getLogger().info("Enabled.");
    }

    @Override
    public void onDisable() {
        if (spendStore != null) {
            spendStore.close();
        }
        getLogger().info("Disabled.");
    }

    public ThresholdService getThresholdService() {
        return thresholdService;
    }

    private void preloadOnlinePlayers() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            spendStore.load(online);
        }
    }

    private void registerCommand() {
        PaperCommandManager commandManager = new PaperCommandManager(this);
        commandManager.registerCommand(new PayNowStoreCommand(this, spendStore, thresholdService, messages));
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not found. Skipping placeholder registration.");
            return;
        }
        boolean registered = new PayNowPlaceholderExpansion(this, spendStore).register();
        if (registered) {
            getLogger().info("Registered PlaceholderAPI expansion.");
        } else {
            getLogger().warning("Failed to register PlaceholderAPI expansion.");
        }
    }
}
