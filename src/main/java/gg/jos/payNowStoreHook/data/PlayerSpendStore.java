package gg.jos.payNowStoreHook.data;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerSpendStore {

    private final NamespacedKey spentKey;
    private final NamespacedKey thresholdIndexKey;

    public PlayerSpendStore(JavaPlugin plugin) {
        this.spentKey = new NamespacedKey(plugin, "spent");
        this.thresholdIndexKey = new NamespacedKey(plugin, "threshold_index");
    }

    public double getSpent(Player player) {
        Double value = getContainer(player).get(spentKey, PersistentDataType.DOUBLE);
        return value == null ? 0.0 : value;
    }

    public double addSpent(Player player, double amount) {
        double updated = getSpent(player) + amount;
        setSpent(player, updated);
        return updated;
    }

    public double removeSpent(Player player, double amount) {
        double updated = Math.max(0.0, getSpent(player) - amount);
        setSpent(player, updated);
        return updated;
    }

    public void setSpent(Player player, double value) {
        getContainer(player).set(spentKey, PersistentDataType.DOUBLE, value);
    }

    public int getLastThresholdIndex(Player player) {
        Integer value = getContainer(player).get(thresholdIndexKey, PersistentDataType.INTEGER);
        return value == null ? -1 : value;
    }

    public void updateThresholdIndex(Player player, int index) {
        getContainer(player).set(thresholdIndexKey, PersistentDataType.INTEGER, index);
    }

    private PersistentDataContainer getContainer(Player player) {
        return player.getPersistentDataContainer();
    }
}
