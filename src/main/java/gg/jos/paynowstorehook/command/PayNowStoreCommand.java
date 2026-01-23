package gg.jos.paynowstorehook.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import gg.jos.paynowstorehook.data.PlayerSpendStore;
import gg.jos.paynowstorehook.message.Messages;
import gg.jos.paynowstorehook.threshold.ThresholdService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@CommandAlias("paynowstorehook|pnstorehook|pnsh")
@CommandPermission("paynowstorehook.use")
public final class PayNowStoreCommand extends BaseCommand {

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

    @Default
    public void usage(CommandSender sender) {
        sender.sendMessage(messages.usage());
    }

    @Subcommand("view")
    @CommandCompletion("@players")
    @Syntax("<player>")
    public void view(CommandSender sender, @Default("$self") String targetPlayer) {
        Player target;
        if (targetPlayer.equals("$self")) {
            if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage(messages.consoleSpecifyPlayer());
                return;
            }
        } else {
            target = Bukkit.getPlayer(targetPlayer);
            if (target == null) {
                sender.sendMessage(messages.playerNotOnline());
                return;
            }
        }

        spendStore.load(target).whenComplete((data, throwable) -> {
            if (throwable != null) {
                handleDatabaseFailure(sender);
                return;
            }
            runSync(() -> sender.sendMessage(messages.view(target.getName(), formatAmount(data.spent()))));
        });
    }

    @Subcommand("add")
    @CommandCompletion("@players <amount>")
    @Syntax("<player> <amount>")
    @CommandPermission("paynowstorehook.modify")
    public void add(CommandSender sender, String targetPlayer, double amount) {
        Player target = Bukkit.getPlayer(targetPlayer);
        if (target == null) {
            sender.sendMessage(messages.playerNotOnline());
            return;
        }

        spendStore.addSpent(target, amount).whenComplete((data, throwable) -> {
            if (throwable != null || data == null) {
                handleDatabaseFailure(sender);
                return;
            }
            runSync(() -> {
                thresholdService.apply(target, data);
                sender.sendMessage(formatModificationMessage(ModificationType.ADD, target, amount, data.spent()));
            });
        });
    }

    @Subcommand("remove")
    @CommandCompletion("@players <amount>")
    @Syntax("<player> <amount>")
    @CommandPermission("paynowstorehook.modify")
    public void remove(CommandSender sender, String targetPlayer, double amount) {
        Player target = Bukkit.getPlayer(targetPlayer);
        if (target == null) {
            sender.sendMessage(messages.playerNotOnline());
            return;
        }

        spendStore.removeSpent(target, amount).whenComplete((data, throwable) -> {
            if (throwable != null || data == null) {
                handleDatabaseFailure(sender);
                return;
            }
            runSync(() -> {
                thresholdService.apply(target, data);
                sender.sendMessage(formatModificationMessage(ModificationType.REMOVE, target, amount, data.spent()));
            });
        });
    }

    private String formatAmount(double value) {
        return String.format("%.2f", value);
    }

    private String formatModificationMessage(ModificationType type, Player target, double amount, double newTotal) {
        String amountFormatted = formatAmount(amount);
        String totalFormatted = formatAmount(newTotal);
        return type == ModificationType.ADD
                ? messages.added(target.getName(), amountFormatted, totalFormatted)
                : messages.removed(target.getName(), amountFormatted, totalFormatted);
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
