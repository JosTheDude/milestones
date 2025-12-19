package gg.jos.payNowStoreHook.command;

import gg.jos.payNowStoreHook.data.PlayerSpendStore;
import gg.jos.payNowStoreHook.message.Messages;
import gg.jos.payNowStoreHook.threshold.ThresholdService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class PayNowStoreCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final PlayerSpendStore spendStore;
    private final ThresholdService thresholdService;
    private final Messages messages;

    public PayNowStoreCommand(JavaPlugin plugin, PlayerSpendStore spendStore, ThresholdService thresholdService, Messages messages) {
        this.plugin = plugin;
        this.spendStore = spendStore;
        this.thresholdService = thresholdService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.usage());
            return true;
        }

        if ("view".equalsIgnoreCase(args[0])) {
            return handleView(sender, args);
        }

        if ("add".equalsIgnoreCase(args[0])) {
            return handleModification(sender, args, true, ModificationType.ADD);
        }

        if ("remove".equalsIgnoreCase(args[0])) {
            return handleModification(sender, args, true, ModificationType.REMOVE);
        }

        return handleModification(sender, args, false, ModificationType.ADD);
    }

    private boolean handleView(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(messages.playerNotOnline());
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(messages.consoleSpecifyPlayer());
            return true;
        }

        spendStore.load(target).whenComplete((data, throwable) -> {
            if (throwable != null) {
                handleDatabaseFailure(sender);
                return;
            }
            runSync(() -> sender.sendMessage(messages.view(target.getName(), formatAmount(data.spent()))));
        });
        return true;
    }

    private boolean handleModification(CommandSender sender, String[] args, boolean explicitCommand, ModificationType type) {
        if (!canModify(sender)) {
            sender.sendMessage(messages.noPermission());
            return true;
        }

        if ((explicitCommand && args.length < 3) || (!explicitCommand && args.length < 2)) {
            sender.sendMessage(messages.usage());
            return true;
        }

        int playerIndex = explicitCommand ? 1 : 0;
        int amountIndex = explicitCommand ? 2 : 1;

        Player target = Bukkit.getPlayer(args[playerIndex]);
        if (target == null) {
            sender.sendMessage(messages.playerNotOnline());
            return true;
        }

        Double amount = parseAmount(args[amountIndex]);
        if (amount == null || amount <= 0) {
            sender.sendMessage(messages.invalidAmount());
            return true;
        }

        CompletableFuture<PlayerSpendStore.PlayerData> future = type == ModificationType.ADD
            ? spendStore.addSpent(target, amount)
            : spendStore.removeSpent(target, amount);

        future.whenComplete((data, throwable) -> {
            if (throwable != null || data == null) {
                handleDatabaseFailure(sender);
                return;
            }
            runSync(() -> {
                thresholdService.apply(target, data);
                sender.sendMessage(formatModificationMessage(type, target, amount, data.spent()));
            });
        });
        return true;
    }

    private boolean canModify(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("paynowstorehook.modify");
    }

    private Double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatAmount(double value) {
        return String.format("%.2f", value);
    }

    private String formatModificationMessage(ModificationType type, Player target, double amount, double newTotal) {
        String amountFormatted = formatAmount(amount);
        String totalFormatted = formatAmount(newTotal);
        if (type == ModificationType.ADD) {
            return messages.added(target.getName(), amountFormatted, totalFormatted);
        }
        return messages.removed(target.getName(), amountFormatted, totalFormatted);
    }

    private void handleDatabaseFailure(CommandSender sender) {
        runSync(() -> sender.sendMessage(messages.databaseError()));
    }

    private void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private enum ModificationType {
        ADD,
        REMOVE
    }
}
