package io.octammo.upgrade;

import io.octammo.OctaMMO;
import io.octammo.gui.ForgeGui;
import io.octammo.gui.OctaGui;
import io.octammo.gui.PathSelectGui;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads upgrades/*.yml (stations with paths) and opens the GUIs. */
public final class UpgradeManager {
    private final OctaMMO plugin;
    private final Map<String, UpgradeFile> files = new LinkedHashMap<>();
    private boolean enabled;

    public UpgradeManager(OctaMMO plugin) {
        this.plugin = plugin;
    }

    public void load() {
        files.clear();
        enabled = plugin.getConfig().getBoolean("modules.item-upgrade", true);

        File dir = new File(plugin.getDataFolder(), "upgrades");
        if (!dir.exists()) {
            dir.mkdirs();
            plugin.saveResource("upgrades/corrupted-sword.yml", false);
        }
        File[] list = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (list == null) return;
        Arrays.sort(list, Comparator.comparing(File::getName));

        for (File f : list) {
            String name = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);

            List<UpgradeFile.Booster> boosters = new ArrayList<>();
            ConfigurationSection bs = cfg.getConfigurationSection("boosters");
            if (bs != null) {
                for (String key : bs.getKeys(false)) {
                    try {
                        boosters.add(new UpgradeFile.Booster(key,
                                RecipeParser.parseItem(bs.getString(key + ".item", ""), "booster"), bs.getDouble(key + ".bonus", 10)));
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning(f.getName() + ": booster '" + key + "' skipped: " + e.getMessage());
                    }
                }
            }
            List<UpgradeFile.Protection> protections = new ArrayList<>();
            ConfigurationSection ps = cfg.getConfigurationSection("protections");
            if (ps != null) {
                for (String key : ps.getKeys(false)) {
                    try {
                        protections.add(new UpgradeFile.Protection(key, RecipeParser.parseItem(ps.getString(key + ".item", ""), "protection")));
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning(f.getName() + ": protection '" + key + "' skipped: " + e.getMessage());
                    }
                }
            }

            List<UpgradeRecipe> recipes = new ArrayList<>();
            ConfigurationSection sec = cfg.getConfigurationSection("upgrades");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    ConfigurationSection s = sec.getConfigurationSection(key);
                    String line = s == null ? null : s.getString("recipe");
                    if (line == null) {
                        plugin.getLogger().warning(f.getName() + ": '" + key + "' has no 'recipe', skipped.");
                        continue;
                    }
                    try {
                        RecipeParser.Parsed p = RecipeParser.parse(line);
                        UpgradeRecipe.FailMode mode = "DESTROY".equalsIgnoreCase(s.getString("on-fail", "KEEP"))
                                ? UpgradeRecipe.FailMode.DESTROY : UpgradeRecipe.FailMode.KEEP;
                        recipes.add(new UpgradeRecipe(name, key, s.getString("name", key), List.copyOf(s.getStringList("description")),
                                p.input(), List.copyOf(p.costs()), p.output(), p.chance(), mode, s.getString("permission", ""),
                                Effects.parse(s.getConfigurationSection("on-success")),
                                Effects.parse(s.getConfigurationSection("on-fail-effects")),
                                s.contains("animation.duration-ticks") ? Integer.valueOf(s.getInt("animation.duration-ticks")) : null));
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning(f.getName() + ": '" + key + "' skipped: " + e.getMessage());
                    }
                }
            }
            files.put(name, new UpgradeFile(name, cfg.getString("title", "&8Item Upgrade"), cfg.getString("permission", ""),
                    cfg.getString("gui", "default-upgrade"),
                    cfg.contains("animation.duration-ticks") ? Integer.valueOf(cfg.getInt("animation.duration-ticks")) : null,
                    List.copyOf(boosters), List.copyOf(protections), List.copyOf(recipes)));
        }
        plugin.getLogger().info("Loaded " + files.size() + " upgrade file(s).");
    }

    public boolean enabled() { return enabled; }
    public int fileCount() { return files.size(); }
    public Set<String> names() { return Collections.unmodifiableSet(files.keySet()); }
    public UpgradeFile get(String name) { return name == null ? null : files.get(name.toLowerCase(Locale.ROOT)); }

    public boolean open(CommandSender sender, String fileName, Player target) {
        return open(sender, fileName, target, null);
    }

    /**
     * Opens the path menu (several paths) or the Altar Forge (one path, or {@code pathKey} given) for {@code target}.
     * {@code sender} receives the error messages.
     */
    public boolean open(CommandSender sender, String fileName, Player target, String pathKey) {
        if (!enabled) { plugin.lang().send(sender, "module-disabled"); return false; }
        UpgradeFile file = get(fileName);
        if (file == null) {
            plugin.lang().send(sender, "upgrade.file-not-found", "file", fileName);
            plugin.lang().send(sender, "upgrade.available", "files", String.join(", ", files.keySet()));
            return false;
        }
        if (sender == target && !canUse(target, file)) {
            plugin.lang().send(sender, "no-permission");
            return false;
        }
        if (target.getGameMode() == GameMode.CREATIVE && !plugin.getConfig().getBoolean("upgrade.allow-creative", false)) {
            plugin.lang().send(sender, "upgrade.creative-denied");
            return false;
        }
        if (plugin.service().isAnimating(target)) {
            plugin.lang().send(sender, "upgrade.busy");
            return false;
        }
        if (!regionAllowed(target)) {
            plugin.lang().send(sender, "upgrade.region-denied");
            return false;
        }
        if (file.recipes().isEmpty()) {
            plugin.lang().send(sender, "upgrade.no-paths");
            return false;
        }
        if (pathKey != null) {
            UpgradeRecipe r = file.path(pathKey);
            if (r == null) {
                plugin.lang().send(sender, "upgrade.path-not-found", "path", pathKey);
                return false;
            }
            return openForge(sender, file, r, target);
        }
        if (file.recipes().size() == 1) return openForge(sender, file, file.recipes().get(0), target);
        new PathSelectGui(plugin, file, target).open();
        return true;
    }

    private boolean openForge(CommandSender sender, UpgradeFile file, UpgradeRecipe recipe, Player target) {
        ForgeGui gui = ForgeGui.create(plugin, file, recipe, target);
        if (gui == null) {
            plugin.lang().send(sender, "upgrade.too-many-runes", "path", recipe.key());
            return false;
        }
        gui.open();
        return true;
    }

    public boolean canUse(Player p, UpgradeFile file) {
        if (!p.hasPermission("octammo.upgrade")) return false;
        if (plugin.getConfig().getBoolean("upgrade.per-file-permission", false)
                && !p.hasPermission("octammo.upgrade." + file.name())) return false;
        return file.permission() == null || file.permission().isBlank() || p.hasPermission(file.permission());
    }

    public boolean regionAllowed(Player p) {
        if (plugin.worldGuard() == null) return true;
        List<String> allowed = plugin.getConfig().getStringList("hooks.worldguard.allowed-regions");
        List<String> blocked = plugin.getConfig().getStringList("hooks.worldguard.blocked-regions");
        if (allowed.isEmpty() && blocked.isEmpty()) return true;
        Set<String> here = plugin.worldGuard().regionsAt(p.getLocation());
        for (String b : blocked) if (here.contains(b.toLowerCase(Locale.ROOT))) return false;
        if (allowed.isEmpty()) return true;
        for (String a : allowed) if (here.contains(a.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    /** Closes every open OctaMMO GUI (the close handler hands the items back). */
    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof OctaGui) p.closeInventory();
        }
    }
}
