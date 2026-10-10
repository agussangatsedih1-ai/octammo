package io.octammo.gui;

import io.octammo.OctaMMO;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Base of every OctaMMO inventory. The listener forwards events to the holder. */
public abstract class OctaGui implements InventoryHolder {
    protected final OctaMMO plugin;
    protected final Player player;
    protected Inventory inventory;
    /** true while the plugin itself switches to another OctaMMO inventory (the close event must not return items). */
    protected boolean transition;

    protected OctaGui(OctaMMO plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player player() { return player; }

    public abstract void handleClick(InventoryClickEvent e);

    public abstract void handleClose(InventoryCloseEvent e);

    /** Anything dragged over this inventory is refused. */
    public void handleDrag(InventoryDragEvent e) {
        int size = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < size) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** Called after the inventory is shown to the player. */
    public void onOpened() {}

    public void open() {
        player.openInventory(inventory);
        onOpened();
    }

    /** Shows another OctaMMO inventory without this one returning the items inside it. */
    protected void switchTo(OctaGui other) {
        transition = true;
        try {
            player.openInventory(other.getInventory());
        } finally {
            transition = false;
        }
        other.onOpened();
    }
}
