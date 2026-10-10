package io.octammo.api.event;

import io.octammo.api.MmoItemRef;
import io.octammo.api.UpgradePath;
import io.octammo.api.UpgradeStation;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** After an item was replaced. */
public class ItemUpdateEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ItemStack oldItem;
    private final ItemStack newItem;

    public ItemUpdateEvent(@NotNull Player player, @NotNull ItemStack oldItem, @Nullable ItemStack newItem) {
        super(player);
        this.oldItem = oldItem;
        this.newItem = newItem;
    }

    public ItemStack getOldItem() { return oldItem; }
    /** The new item, or null when it could not be located (it may have been dropped). */
    public @Nullable ItemStack getNewItem() { return newItem; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
