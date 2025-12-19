package gg.jos.payNowStoreHook.threshold;

import gg.jos.payNowStoreHook.data.PlayerSpendStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class ThresholdService {

    private final PlayerSpendStore spendStore;
    private List<Threshold> thresholds;

    public ThresholdService(PlayerSpendStore spendStore, List<Threshold> thresholds) {
        this.spendStore = spendStore;
        this.thresholds = List.copyOf(thresholds);
    }

    public void reload(List<Threshold> thresholds) {
        this.thresholds = List.copyOf(thresholds);
    }

    public void apply(Player player, double currentSpent) {
        if (thresholds.isEmpty()) {
            if (spendStore.getLastThresholdIndex(player) != -1) {
                spendStore.updateThresholdIndex(player, -1);
            }
            return;
        }

        int previousIndex = Math.min(spendStore.getLastThresholdIndex(player), thresholds.size() - 1);
        int targetIndex = resolveIndex(currentSpent);

        if (targetIndex > previousIndex) {
            for (int i = previousIndex + 1; i <= targetIndex; i++) {
                executeCommands(player, thresholds.get(i).commands());
            }
        } else if (targetIndex < previousIndex) {
            for (int i = previousIndex; i > targetIndex; i--) {
                executeCommands(player, thresholds.get(i).undoCommands());
            }
        }

        if (targetIndex != previousIndex) {
            spendStore.updateThresholdIndex(player, targetIndex);
        }
    }

    private int resolveIndex(double spent) {
        int index = -1;
        for (int i = 0; i < thresholds.size(); i++) {
            if (spent >= thresholds.get(i).amount()) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private void executeCommands(Player player, List<String> commands) {
        for (String command : commands) {
            if (command.isBlank()) {
                continue;
            }
            String resolved = command.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }
}
