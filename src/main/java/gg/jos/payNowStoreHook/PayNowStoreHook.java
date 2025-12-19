package gg.jos.payNowStoreHook;

import gg.jos.payNowStoreHook.command.PayNowStoreCommand;
import gg.jos.payNowStoreHook.data.PlayerSpendStore;
import gg.jos.payNowStoreHook.message.Messages;
import gg.jos.payNowStoreHook.placeholder.PayNowPlaceholderExpansion;
import gg.jos.payNowStoreHook.threshold.ThresholdLoader;
import gg.jos.payNowStoreHook.threshold.ThresholdService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PayNowStoreHook extends JavaPlugin {

    private PlayerSpendStore spendStore;
    private ThresholdService thresholdService;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        spendStore = new PlayerSpendStore(this);
        thresholdService = new ThresholdService(spendStore, new ThresholdLoader(getConfig()).load());
        messages = new Messages(getConfig());
        registerCommand();
        registerPlaceholders();
        getLogger().info("Enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabled.");
    }


    private void registerCommand() {
        PluginCommand command = getCommand("paynowstorehook");
        if (command == null) {
            getLogger().severe("Command paynowstorehook missing in plugin.yml");
            return;
        }
        command.setExecutor(new PayNowStoreCommand(spendStore, thresholdService, messages));
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
