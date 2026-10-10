package io.octammo.update;

import io.octammo.OctaMMOPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Item Update triggers: join, opening an inventory (debounced) and picking up an item. */
public final class UpdateListener implements Listener {
    private final OctaMMOPlugin plugin;

    public UpdateListener(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.updater().triggerJoin()) return;
        Player p = e.getPlayer();
        // give other plugins a moment to finish loading the inventory
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.updater().requestScan(p);
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!plugin.updater().triggerInventoryOpen()) return;
        if (e.getPlayer() instanceof Player p && e.getInventory().getHolder(false) != null && !(e.getInventory().getHolder(false) instanceof io.octammo.gui.OctaGui)) {
            plugin.updater().requestScanOnOpen(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!plugin.updater().triggerPickup()) return;
        if (e.getEntity() instanceof Player p) {
            // the item is added to the inventory after the event
            Bukkit.getScheduler().runTask(plugin, () -> plugin.updater().requestScan(p));
        }
    }
}
