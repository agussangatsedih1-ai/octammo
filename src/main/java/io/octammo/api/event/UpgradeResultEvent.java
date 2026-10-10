package io.octammo.api.event;

import io.octammo.upgrade.UpgradeRecipe;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.inventory.ItemStack;

/** Fired after an upgrade attempt was resolved (costs already taken). */
public class UpgradeResultEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UpgradeRecipe recipe;
    private final boolean success;
    private final ItemStack output;

    public UpgradeResultEvent(@NotNull Player player, @NotNull UpgradeRecipe recipe, boolean success, @Nullable ItemStack output) {
        super(player);
        this.recipe = recipe;
        this.success = success;
        this.output = output;
    }

    public UpgradeRecipe getRecipe() { return recipe; }
    public boolean isSuccess() { return success; }
    /** The item that was given on success, otherwise null. */
    public @Nullable ItemStack getOutput() { return output; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
