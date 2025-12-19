package gg.jos.payNowStoreHook.message;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public final class Messages {

    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("usage", "Usage: /paynowstorehook view [player] | /paynowstorehook add <player> <amount> | /paynowstorehook remove <player> <amount> | /paynowstorehook <player> <amount>");
        DEFAULTS.put("no_permission", "No permission.");
        DEFAULTS.put("player_not_online", "Player not online.");
        DEFAULTS.put("console_specify_player", "Console must specify a player: /paynowstorehook view <player>");
        DEFAULTS.put("invalid_amount", "Invalid amount.");
        DEFAULTS.put("view", "%player% has spent: %amount%");
        DEFAULTS.put("added", "Added %amount% to %player%. Total: %total%");
        DEFAULTS.put("removed", "Removed %amount% from %player%. Total: %total%");
    }

    private final FileConfiguration configuration;

    public Messages(FileConfiguration configuration) {
        this.configuration = configuration;
    }

    public String usage() {
        return get("usage");
    }

    public String noPermission() {
        return get("no_permission");
    }

    public String playerNotOnline() {
        return get("player_not_online");
    }

    public String consoleSpecifyPlayer() {
        return get("console_specify_player");
    }

    public String invalidAmount() {
        return get("invalid_amount");
    }

    public String view(String playerName, String amountFormatted) {
        return applyPlaceholders(get("view"), Placeholder.of("%player%", playerName), Placeholder.of("%amount%", amountFormatted));
    }

    public String added(String playerName, String amountFormatted, String totalFormatted) {
        return applyPlaceholders(
            get("added"),
            Placeholder.of("%player%", playerName),
            Placeholder.of("%amount%", amountFormatted),
            Placeholder.of("%total%", totalFormatted)
        );
    }

    public String removed(String playerName, String amountFormatted, String totalFormatted) {
        return applyPlaceholders(
            get("removed"),
            Placeholder.of("%player%", playerName),
            Placeholder.of("%amount%", amountFormatted),
            Placeholder.of("%total%", totalFormatted)
        );
    }

    private String get(String key) {
        return configuration.getString("messages." + key, DEFAULTS.getOrDefault(key, ""));
    }

    private String applyPlaceholders(String message, Placeholder... placeholders) {
        String resolved = message;
        for (Placeholder placeholder : placeholders) {
            resolved = resolved.replace(placeholder.token(), placeholder.value());
        }
        return resolved;
    }

    private record Placeholder(String token, String value) {
        static Placeholder of(String token, String value) {
            return new Placeholder(token, value);
        }
    }
}
