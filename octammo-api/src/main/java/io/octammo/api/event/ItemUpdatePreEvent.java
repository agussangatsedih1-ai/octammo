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

/** Before an outdated item is replaced. Cancel it to keep the old item. */
public class ItemUpdatePreEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ItemStack oldItem;
    private final MmoItemRef ref;
    private final String templateSignature;
    private boolean cancelled;

    public ItemUpdatePreEvent(@NotNull Player player, @NotNull ItemStack oldItem, @NotNull MmoItemRef ref, @NotNull String templateSignature) {
        super(player);
        this.oldItem = oldItem;
        this.ref = ref;
        this.templateSignature = templateSignature;
    }

    public ItemStack getOldItem() { return oldItem; }
    public MmoItemRef getItemRef() { return ref; }
    /** Signature of the template as it is now. */
    public String getTemplateSignature() { return templateSignature; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
