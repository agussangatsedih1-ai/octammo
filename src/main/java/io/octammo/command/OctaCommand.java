package io.octammo.command;

import io.octammo.OctaMMO;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /octammo help|reload|update|upgrade|list|version */
public final class OctaCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("help", "reload", "update", "upgrade", "list", "version");
    private final OctaMMO plugin;

    public OctaCommand(OctaMMO plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("version") && !sender.hasPermission("octammo.admin")) {
            plugin.lang().send(sender, "no-permission");
            return true;
        }
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.lang().send(sender, "reloaded",
                        "files", String.valueOf(plugin.upgrades().fileCount()),
                        "rules", String.valueOf(plugin.updater().ruleCount()));
            }
            case "update" -> {
                if (args.length < 2) { plugin.lang().sendList(sender, "help"); return true; }
                if (args[1].equalsIgnoreCase("all")) {
                    int total = 0;
                    for (Player p : Bukkit.getOnlinePlayers()) total += plugin.updater().scan(p, true);
                    plugin.lang().send(sender, "update.scanned-all",
                            "players", String.valueOf(Bukkit.getOnlinePlayers().size()), "count", String.valueOf(total));
                } else {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) { plugin.lang().send(sender, "player-not-found", "player", args[1]); return true; }
                    plugin.lang().send(sender, "update.scanned",
                            "player", target.getName(), "count", String.valueOf(plugin.updater().scan(target, true)));
                }
            }
            case "upgrade" -> {
                if (args.length < 2) { plugin.lang().sendList(sender, "help"); return true; }
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) { plugin.lang().send(sender, "player-not-found", "player", args[2]); return true; }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    plugin.lang().send(sender, "player-only");
                    return true;
                }
                if (plugin.upgrades().open(sender, args[1], target) && target != sender) {
                    plugin.lang().send(sender, "upgrade.opened-for", "file", args[1].toLowerCase(Locale.ROOT), "player", target.getName());
                }
            }
            case "list" -> {
                if (plugin.upgrades().names().isEmpty()) plugin.lang().send(sender, "upgrade.list-empty");
                else plugin.lang().send(sender, "upgrade.available", "files", String.join(", ", plugin.upgrades().names()));
            }
            case "version" -> plugin.lang().send(sender, "version",
                    "version", plugin.getPluginMeta().getVersion(),
                    "mmoitems", String.valueOf(Bukkit.getPluginManager().isPluginEnabled("MMOItems")));
            default -> plugin.lang().sendList(sender, "help");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("octammo.admin")) return List.of();
        List<String> out = new ArrayList<>();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            out.addAll(SUBS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("upgrade")) {
            out.addAll(plugin.upgrades().names());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("update")) {
            out.add("all");
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("upgrade")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
