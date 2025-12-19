package gg.jos.payNowStoreHook.placeholder;

import gg.jos.payNowStoreHook.PayNowStoreHook;
import gg.jos.payNowStoreHook.data.PlayerSpendStore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class PayNowPlaceholderExpansion extends PlaceholderExpansion {

    private static final String IDENTIFIER = "paynowstorehook";

    private final PayNowStoreHook plugin;
    private final PlayerSpendStore spendStore;

    public PayNowPlaceholderExpansion(PayNowStoreHook plugin, PlayerSpendStore spendStore) {
        this.plugin = plugin;
        this.spendStore = spendStore;
    }

    @Override
    public @NotNull String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        Player online = player.getPlayer();
        if (online == null) {
            return "";
        }
        String identifier = params.toLowerCase(Locale.ENGLISH);
        if (identifier.equals("spent") || identifier.equals("total_spent")) {
            return formatAmount(spendStore.getSpent(online));
        }
        return "";
    }

    private String formatAmount(double amount) {
        return String.format(Locale.ENGLISH, "%.2f", amount);
    }
}
