package io.octammo.api.event;

import io.octammo.core.ItemRef;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Fired before an outdated item is replaced by the Item Update module. */
public class ItemUpdateEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ItemStack oldItem;
    private final ItemRef ref;
    private final String ruleKey;
    private boolean cancelled;

    public ItemUpdateEvent(@NotNull Player player, @NotNull ItemStack oldItem, @NotNull ItemRef ref, @NotNull String ruleKey) {
        super(player);
        this.oldItem = oldItem;
        this.ref = ref;
        this.ruleKey = ruleKey;
    }

    public ItemStack getOldItem() { return oldItem; }
    public ItemRef getItemRef() { return ref; }
    public String getRuleKey() { return ruleKey; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
