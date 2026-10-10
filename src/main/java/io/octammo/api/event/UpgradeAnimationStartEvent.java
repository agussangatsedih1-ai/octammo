package io.octammo.api.event;

import io.octammo.upgrade.UpgradeRecipe;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired before the loading animation starts, costs not taken yet. Cancel it or change the duration. */
public class UpgradeAnimationStartEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UpgradeRecipe recipe;
    private int durationTicks;
    private boolean cancelled;

    public UpgradeAnimationStartEvent(@NotNull Player player, @NotNull UpgradeRecipe recipe, int durationTicks) {
        super(player);
        this.recipe = recipe;
        this.durationTicks = durationTicks;
    }

    public UpgradeRecipe getRecipe() { return recipe; }
    public int getDurationTicks() { return durationTicks; }
    public void setDurationTicks(int durationTicks) { this.durationTicks = Math.max(1, durationTicks); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
