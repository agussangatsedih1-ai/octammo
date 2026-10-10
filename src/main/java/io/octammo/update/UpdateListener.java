package io.octammo.update;

import io.octammo.OctaMMO;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class UpdateListener implements Listener {
    private final OctaMMO plugin;

    public UpdateListener(OctaMMO plugin) {
        this.plugin = plugin;
    }

    private ItemUpdater updater() {
        return plugin.updater();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!updater().triggerJoin()) return;
        Player p = e.getPlayer();
        // give other plugins a moment to finish loading the inventory
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) updater().requestScan(p);
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!updater().triggerInventoryClose()) return;
        if (e.getPlayer() instanceof Player p) updater().requestScan(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!updater().triggerPickup()) return;
        if (e.getEntity() instanceof Player p) {
            // the item is added to the inventory after the event
            Bukkit.getScheduler().runTask(plugin, () -> updater().requestScan(p));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        updater().forget(e.getPlayer().getUniqueId());
    }
}
