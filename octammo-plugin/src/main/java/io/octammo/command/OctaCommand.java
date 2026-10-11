package io.octammo.command;

import io.octammo.OctaMMOPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /octammo help | reload | upgrade &lt;station&gt; [player] [path] | list | version (short: ver) */
public final class OctaCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("help", "reload", "upgrade", "list", "version", "ver");
    private final OctaMMOPlugin plugin;

    public OctaCommand(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> {
                if (!sender.hasPermission("octammo.use")) { plugin.lang().send(sender, "no-permission"); return true; }
                plugin.lang().sendList(sender, "commands-help");
            }
            case "version", "ver" -> about(sender);
            case "reload" -> {
                if (!sender.hasPermission("octammo.admin")) { plugin.lang().send(sender, "no-permission"); return true; }
                plugin.reloadAll();
                plugin.lang().send(sender, "reloaded-ok", "files", String.valueOf(plugin.upgrades().stationCount()));
            }
            case "list" -> {
                if (!sender.hasPermission("octammo.admin")) { plugin.lang().send(sender, "no-permission"); return true; }
                if (plugin.upgrades().ids().isEmpty()) plugin.lang().send(sender, "upgrade.list-empty");
                else plugin.lang().send(sender, "upgrade.available", "files", String.join(", ", plugin.upgrades().ids()));
            }
            case "upgrade" -> upgrade(sender, args);
            default -> plugin.lang().sendList(sender, "commands-help");
        }
        return true;
    }

    /** Authors (from plugin.yml), plugin version and a short description. */
    private void about(CommandSender sender) {
        var meta = plugin.getPluginMeta();
        plugin.lang().sendList(sender, "about",
                "authors", joinAnd(meta.getAuthors()),
                "version", meta.getVersion(),
                "description", plugin.lang().format("plugin-description"));
    }

    /** [Alvran, Robozat, Team] -> "Alvran, Robozat and Team" */
    static String joinAnd(List<String> names) {
        if (names.isEmpty()) return "-";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private void upgrade(CommandSender sender, String[] args) {
        if (!plugin.mmoItems() || !plugin.upgrades().enabled()) { plugin.lang().send(sender, "module-disabled"); return; }
        Player target;
        if (args.length >= 3) {
            if (!sender.hasPermission("octammo.admin")) { plugin.lang().send(sender, "no-permission"); return; }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) { plugin.lang().send(sender, "player-not-found", "player", args[2]); return; }
        } else if (sender instanceof Player p) {
            if (!p.hasPermission("octammo.upgrade")) { plugin.lang().send(sender, "no-permission"); return; }
            target = p;
        } else {
            plugin.lang().send(sender, "player-only");
            return;
        }
        String station = args.length >= 2 ? args[1] : null;
        String path = args.length >= 4 ? args[3] : null;
        if (plugin.upgrades().open(sender, station, target, path) && target != sender && station != null) {
            plugin.lang().send(sender, "upgrade.opened-for", "file", station.toLowerCase(Locale.ROOT), "player", target.getName());
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : SUBS) {
                boolean allowed = switch (s) {
                    case "reload", "list" -> sender.hasPermission("octammo.admin");
                    case "upgrade" -> sender.hasPermission("octammo.upgrade");
                    default -> true;
                };
                if (allowed) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("upgrade") && sender.hasPermission("octammo.upgrade")) {
            out.addAll(plugin.upgrades().ids());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("upgrade") && sender.hasPermission("octammo.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("upgrade")) {
            var st = plugin.upgrades().get(args[1]);
            if (st != null) st.pathData().forEach(pd -> out.add(pd.id()));
        }
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
