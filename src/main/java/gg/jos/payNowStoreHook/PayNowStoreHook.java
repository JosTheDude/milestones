package gg.jos.payNowStoreHook;

 import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PayNowStoreHook extends JavaPlugin implements CommandExecutor {

    private NamespacedKey spentKey;
    private NamespacedKey thresholdIndexKey;

    private final List<Threshold> thresholds = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.getLogger().info("Enabled.");

        // NamespacedKey's key must only contain [a-z0-9._-], the namespace is handled by Bukkit.
        spentKey = new NamespacedKey(this, "spent");
        thresholdIndexKey = new NamespacedKey(this, "threshold_index");
        loadThresholds();
        if (getCommand("paynowstorehook") != null) {
            getCommand("paynowstorehook").setExecutor(this);
        }
    }

    @Override
    public void onDisable() {
        this.getLogger().info("Disabled.");
    }

    private void loadThresholds() {
        thresholds.clear();
        List<Map<?, ?>> list = getConfig().getMapList("thresholds");
        int idx = 0;
        for (Map<?, ?> map : list) {
            Object amountObj = map.get("amount");
            Object commandsObj = map.get("commands");
            double amount = 0.0;
            if (amountObj instanceof Number) {
                amount = ((Number) amountObj).doubleValue();
            } else if (amountObj instanceof String) {
                try {
                    amount = Double.parseDouble((String) amountObj);
                } catch (NumberFormatException ignored) {}
            }
            List<String> cmds = new ArrayList<>();
            if (commandsObj instanceof List) {
                for (Object o : (List<?>) commandsObj) {
                    if (o != null) cmds.add(String.valueOf(o));
                }
            }
            thresholds.add(new Threshold(amount, cmds, idx++));
        }
        // sort ascending by amount
        thresholds.sort(Comparator.comparingDouble(t -> t.amount));
    }

    private double getSpent(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Double d = pdc.get(spentKey, PersistentDataType.DOUBLE);
        return d == null ? 0.0 : d;
    }

    private void setSpent(Player player, double value) {
        player.getPersistentDataContainer().set(spentKey, PersistentDataType.DOUBLE, value);
    }

    private int getThresholdIndex(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Integer i = pdc.get(thresholdIndexKey, PersistentDataType.INTEGER);
        return i == null ? -1 : i;
    }

    private void setThresholdIndex(Player player, int index) {
        player.getPersistentDataContainer().set(thresholdIndexKey, PersistentDataType.INTEGER, index);
    }

    private void checkAndApplyThresholds(Player player, double currentSpent) {
        int lastIndex = getThresholdIndex(player);
        int appliedIndex = lastIndex;
        // iterate thresholds in ascending order and apply any not yet applied
        for (Threshold t : thresholds) {
            if (t.index > lastIndex && currentSpent >= t.amount) {
                // execute commands for this threshold
                for (String cmd : t.commands) {
                    if (cmd == null || cmd.trim().isEmpty()) continue;
                    String resolved = cmd.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                }
                appliedIndex = t.index;
            }
        }
        if (appliedIndex != lastIndex) {
            setThresholdIndex(player, appliedIndex);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // permission check for adding: console always allowed, players need permission
        boolean isConsole = !(sender instanceof Player);
        boolean canModify = isConsole || sender.hasPermission("paynowstorehook.modify");

        if (args.length == 0) {
            sender.sendMessage("Usage: /paynowstorehook view [player] | /paynowstorehook add <player> <amount> | /paynowstorehook <player> <amount>");
            return true;
        }

        if (args[0].equalsIgnoreCase("view")) {
            // view [player]
            Player target;
            if (args.length >= 2) {
                target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("Player not online.");
                    return true;
                }
            } else {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Console must specify a player: /paynowstorehook view <player>");
                    return true;
                }
                target = (Player) sender;
            }
            double spent = getSpent(target);
            sender.sendMessage(target.getName() + " has spent: " + String.format("%.2f", spent));
            return true;
        }

        // support: /paynowstorehook add <player> <amount>
        if (args[0].equalsIgnoreCase("add") && args.length >= 3) {
            if (!canModify) {
                sender.sendMessage("No permission.");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("Player not online.");
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("Invalid amount.");
                return true;
            }
            double newSpent = getSpent(target) + amount;
            setSpent(target, newSpent);
            checkAndApplyThresholds(target, newSpent);
            sender.sendMessage("Added " + String.format("%.2f", amount) + " to " + target.getName() + ". Total: " + String.format("%.2f", newSpent));
            return true;
        }

        // legacy: /paynowstorehook <player> <amount>
        if (args.length >= 2) {
            if (!canModify) {
                sender.sendMessage("No permission.");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("Player not online.");
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("Invalid amount.");
                return true;
            }
            double newSpent = getSpent(target) + amount;
            setSpent(target, newSpent);
            checkAndApplyThresholds(target, newSpent);
            sender.sendMessage("Added " + String.format("%.2f", amount) + " to " + target.getName() + ". Total: " + String.format("%.2f", newSpent));
            return true;
        }

        sender.sendMessage("Usage: /paynowstorehook view [player] | /paynowstorehook add <player> <amount>");
        return true;
    }

    private static class Threshold {
        final double amount;
        final List<String> commands;
        final int index;

        Threshold(double amount, List<String> commands, int index) {
            this.amount = amount;
            this.commands = commands;
            this.index = index;
        }
    }
}
