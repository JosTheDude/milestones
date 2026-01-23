package gg.jos.paynowstorehook.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerThresholdClearEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerThresholdClearEvent(@NotNull Player who) {
        super(who);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
