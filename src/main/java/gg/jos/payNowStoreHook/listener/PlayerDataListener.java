package gg.jos.payNowStoreHook.listener;

import gg.jos.payNowStoreHook.data.PlayerSpendStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDataListener implements Listener {

    private final PlayerSpendStore spendStore;

    public PlayerDataListener(PlayerSpendStore spendStore) {
        this.spendStore = spendStore;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        spendStore.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        spendStore.evict(event.getPlayer().getUniqueId());
    }
}
