package gg.jos.paynowstorehook.threshold;

import gg.jos.paynowstorehook.data.PlayerSpendStore;
import gg.jos.paynowstorehook.events.PlayerChangeThresholdEvent;
import gg.jos.paynowstorehook.events.PlayerThresholdClearEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    public void apply(Player player, PlayerSpendStore.PlayerData data) {
        if (thresholds.isEmpty()) {
            if (data.thresholdIndex() != -1) {
                new PlayerThresholdClearEvent(player).callEvent();
                spendStore.updateThresholdIndex(player.getUniqueId(), player.getName(), -1);
            }
            return;
        }

        int previousIndex = Math.min(Math.max(-1, data.thresholdIndex()), thresholds.size() - 1);
        int targetIndex = resolveIndex(data.spent());
        if (previousIndex != targetIndex) {
            PlayerChangeThresholdEvent event = new PlayerChangeThresholdEvent(
                    player,
                    previousIndex == -1 ? null : thresholds.get(previousIndex),
                    targetIndex == -1 ? null : thresholds.get(targetIndex)
            );
            if (!event.callEvent()) {
                return;
            }
            previousIndex = event.getOldThresholdIndex();
            targetIndex = event.getNewThresholdIndex();
        }

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
            spendStore.updateThresholdIndex(player.getUniqueId(), player.getName(), targetIndex);
        }
    }

    public CompletableFuture<Integer> getThresholdIndex(Player player) {
        if (!spendStore.isCached(player.getUniqueId())) {
            return spendStore.load(player).thenApply(PlayerSpendStore.PlayerData::thresholdIndex);
        }
        return CompletableFuture.completedFuture(spendStore.getCachedThresholdIndex(player.getUniqueId()));
    }

    public List<Threshold> getThresholds() {
        return thresholds;
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
