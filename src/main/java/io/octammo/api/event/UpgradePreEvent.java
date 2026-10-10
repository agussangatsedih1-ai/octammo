package io.octammo.api.event;

import io.octammo.upgrade.UpgradeRecipe;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired before costs are taken. Cancel it to stop the upgrade, or change the chance. */
public class UpgradePreEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UpgradeRecipe recipe;
    private double chance;
    private boolean cancelled;

    public UpgradePreEvent(@NotNull Player player, @NotNull UpgradeRecipe recipe, double chance) {
        super(player);
        this.recipe = recipe;
        this.chance = chance;
    }

    public UpgradeRecipe getRecipe() { return recipe; }
    /** Success chance in percent (0-100). */
    public double getChance() { return chance; }
    public void setChance(double chance) { this.chance = Math.max(0, Math.min(100, chance)); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
