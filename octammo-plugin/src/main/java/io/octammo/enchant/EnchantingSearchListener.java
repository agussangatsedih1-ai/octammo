package io.octammo.enchant;

import io.octammo.OctaMMOPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Captures one chat message as an enchanting search keyword for a player. */
public final class EnchantingSearchListener implements Listener {
    private final OctaMMOPlugin plugin;
    private final Map<UUID, EnchantingGui> waiting = new ConcurrentHashMap<>();

    public EnchantingSearchListener(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    public void begin(Player player, EnchantingGui gui) {
        waiting.put(player.getUniqueId(), gui);
    }

    public void end(Player player) {
        waiting.remove(player.getUniqueId());
    }

    public void clear() {
        waiting.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        EnchantingGui gui = waiting.remove(player.getUniqueId());
        if (gui == null) return;
        event.setCancelled(true);
        String keyword = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> gui.acceptSearch(keyword));
    }
}
