package gg.jos.paynowstorehook.events;

import gg.jos.paynowstorehook.threshold.Threshold;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerChangeThresholdEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private @Nullable Threshold oldThreshold;
    private @Nullable Threshold newThreshold;
    private boolean cancelled = false;

    public PlayerChangeThresholdEvent(@NotNull Player who, @Nullable Threshold oldThreshold, @Nullable Threshold newThreshold) {
        super(who);
        this.oldThreshold = oldThreshold;
        this.newThreshold = newThreshold;
    }

    public void setOldThreshold(@Nullable Threshold oldThreshold) {
        this.oldThreshold = oldThreshold;
    }

    public void setNewThreshold(@Nullable Threshold newThreshold) {
        this.newThreshold = newThreshold;
    }

    public @Nullable Threshold getOldThreshold() {
        return oldThreshold;
    }

    public int getOldThresholdIndex() {
        return oldThreshold == null ? -1 : oldThreshold.index();
    }

    public @Nullable Threshold getNewThreshold() {
        return newThreshold;
    }

    public int getNewThresholdIndex() {
        return newThreshold == null ? -1 : newThreshold.index();
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
