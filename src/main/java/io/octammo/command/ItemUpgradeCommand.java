package io.octammo.command;

import io.octammo.OctaMMO;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** /itemupgrade <file> [path]  (also reachable as /octammo:itemupgrade) */
public final class ItemUpgradeCommand implements TabExecutor {
    private final OctaMMO plugin;

    public ItemUpgradeCommand(OctaMMO plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("octammo.upgrade")) {
            plugin.lang().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.lang().send(sender, "upgrade.usage");
            plugin.lang().send(sender, "upgrade.available", "files", String.join(", ", plugin.upgrades().names()));
            return true;
        }
        plugin.upgrades().open(player, args[0], player, args.length > 1 ? args[1] : null);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length == 2) {
            var file = plugin.upgrades().get(args[0]);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (file == null) return List.of();
            return file.recipes().stream().map(r -> r.key()).filter(k -> k.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.upgrades().names().stream().filter(n -> n.startsWith(prefix)).toList();
    }
}
