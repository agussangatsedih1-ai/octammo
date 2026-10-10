package io.octammo.command;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.UpdateResult;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /octammo help | reload | update hand|inventory|&lt;player&gt; | upgrade &lt;station&gt; [player] [path] | list | version */
public final class OctaCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("help", "reload", "update", "upgrade", "list", "version");
    private final OctaMMOPlugin plugin;

    public OctaCommand(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean mayUpdate(CommandSender s) {
        return s.hasPermission("octammo.update") || (!plugin.updater().requirePermission() && s.hasPermission("octammo.use"));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> {
                if (!sender.hasPermission("octammo.use")) { plugin.lang().send(sender, "no-permission"); return true; }
                plugin.lang().sendList(sender, "help");
            }
            case "version" -> plugin.lang().send(sender, "version",
                    "version", plugin.getPluginMeta().getVersion(), "mmoitems", String.valueOf(plugin.mmoItems()));
            case "reload" -> {
                if (!sender.hasPermission("octammo.admin")) { plugin.lang().send(sender, "no-permission"); return true; }
                plugin.reloadAll();
                plugin.lang().send(sender, "reloaded", "files", String.valueOf(plugin.upgrades().stationCount()),
                        "rules", String.valueOf(plugin.updater().templateCount()));
            }
            case "list" -> {
                if (!sender.hasPermission("octammo.admin")) { plugin.lang().send(sender, "no-permission"); return true; }
                if (plugin.upgrades().ids().isEmpty()) plugin.lang().send(sender, "upgrade.list-empty");
                else plugin.lang().send(sender, "upgrade.available", "files", String.join(", ", plugin.upgrades().ids()));
            }
            case "update" -> update(sender, args);
            case "upgrade" -> upgrade(sender, args);
            default -> plugin.lang().sendList(sender, "help");
        }
        return true;
    }

    private void update(CommandSender sender, String[] args) {
        if (!plugin.mmoItems() || !plugin.updater().enabled()) { plugin.lang().send(sender, "module-disabled"); return; }
        if (args.length < 2) { plugin.lang().sendList(sender, "help"); return; }
        String what = args[1].toLowerCase(Locale.ROOT);
        if (what.equals("hand") || what.equals("inventory")) {
            if (!(sender instanceof Player p)) { plugin.lang().send(sender, "player-only"); return; }
            if (!mayUpdate(sender)) { plugin.lang().send(sender, "no-permission"); return; }
            if (what.equals("hand")) {
                UpdateResult r = plugin.updater().updateInventorySlot(p, p.getInventory().getHeldItemSlot());
                plugin.lang().send(sender, "update.hand." + r.name().toLowerCase(Locale.ROOT));
            } else {
                int n = plugin.updater().scan(p, true);
                plugin.lang().send(sender, n > 0 ? "update.updated" : "update.none", "count", String.valueOf(n));
            }
            return;
        }
        if (!sender.hasPermission("octammo.update.others")) { plugin.lang().send(sender, "no-permission"); return; }
        if (what.equals("all")) {
            int total = 0;
            for (Player p : Bukkit.getOnlinePlayers()) total += plugin.updater().scan(p, true);
            plugin.lang().send(sender, "update.scanned-all", "players", String.valueOf(Bukkit.getOnlinePlayers().size()), "count", String.valueOf(total));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { plugin.lang().send(sender, "player-not-found", "player", args[1]); return; }
        plugin.lang().send(sender, "update.scanned", "player", target.getName(), "count", String.valueOf(plugin.updater().scan(target, true)));
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
                    case "update" -> mayUpdate(sender) || sender.hasPermission("octammo.update.others");
                    case "upgrade" -> sender.hasPermission("octammo.upgrade");
                    default -> true;
                };
                if (allowed) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("upgrade") && sender.hasPermission("octammo.upgrade")) {
            out.addAll(plugin.upgrades().ids());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("update")) {
            if (mayUpdate(sender)) { out.add("hand"); out.add("inventory"); }
            if (sender.hasPermission("octammo.update.others")) {
                out.add("all");
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("upgrade") && sender.hasPermission("octammo.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("upgrade")) {
            var st = plugin.upgrades().get(args[1]);
            if (st != null) st.pathData().forEach(pd -> out.add(pd.id()));
        }
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
