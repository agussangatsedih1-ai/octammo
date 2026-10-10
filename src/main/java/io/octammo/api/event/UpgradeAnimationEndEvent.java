package io.octammo.api.event;

import io.octammo.upgrade.UpgradeRecipe;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired when the animation reached 100% (or was finished early) and the result was resolved. */
public class UpgradeAnimationEndEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UpgradeRecipe recipe;
    private final boolean success;
    private final boolean instant;

    public UpgradeAnimationEndEvent(@NotNull Player player, @NotNull UpgradeRecipe recipe, boolean success, boolean instant) {
        super(player);
        this.recipe = recipe;
        this.success = success;
        this.instant = instant;
    }

    public UpgradeRecipe getRecipe() { return recipe; }
    public boolean isSuccess() { return success; }
    /** true when the animation was skipped (disconnect, shutdown, animation disabled). */
    public boolean isInstant() { return instant; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
