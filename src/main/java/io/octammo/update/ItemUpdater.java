package io.octammo.update;

import io.octammo.OctaMMO;
import io.octammo.api.event.ItemUpdateEvent;
import io.octammo.core.Items;
import io.octammo.core.ItemRef;
import io.octammo.core.MmoItems;
import io.octammo.core.PlayerStats;
import io.octammo.core.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Item Update module: replaces outdated MMOItems after a nerf/buff, for free. */
public final class ItemUpdater {
    private final OctaMMO plugin;
    private final List<UpdateRule> rules = new ArrayList<>();
    private final List<Pattern> ignore = new ArrayList<>();
    private final Map<UUID, Long> lastScan = new HashMap<>();
    private final Map<String, Boolean> saneCache = new HashMap<>();

    private boolean enabled;
    private boolean useApi;
    private String command = "mmoitems give {type} {id} {player} {amount}";
    private boolean notify;
    private long cooldownMs;
    private int maxPerScan;
    private boolean trigJoin, trigClose, trigPickup;
    private int intervalSeconds;

    public ItemUpdater(OctaMMO plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rules.clear();
        ignore.clear();
        saneCache.clear();
        enabled = plugin.getConfig().getBoolean("modules.item-update", true);

        File file = new File(plugin.getDataFolder(), "updates.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        useApi = cfg.getString("settings.method", "COMMAND").equalsIgnoreCase("API");
        command = cfg.getString("settings.command", command);
        notify = cfg.getBoolean("settings.notify-player", true);
        cooldownMs = cfg.getLong("settings.scan-cooldown-ms", 3000);
        maxPerScan = Math.max(1, cfg.getInt("settings.max-updates-per-scan", 40));
        trigJoin = cfg.getBoolean("settings.triggers.join", true);
        trigClose = cfg.getBoolean("settings.triggers.inventory-close", true);
        trigPickup = cfg.getBoolean("settings.triggers.pickup", true);
        intervalSeconds = cfg.getInt("settings.triggers.interval-seconds", 0);

        for (String regex : cfg.getStringList("settings.ignore-lore-regex")) {
            try {
                ignore.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                plugin.getLogger().warning("updates.yml: invalid ignore-lore-regex '" + regex + "'");
            }
        }

        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                String type = s.getString("type");
                String id = s.getString("id");
                if (type == null || id == null) {
                    plugin.getLogger().warning("updates.yml: '" + key + "' needs both type and id, skipped.");
                    continue;
                }
                rules.add(new UpdateRule(key,
                        type.toUpperCase(Locale.ROOT), id.toUpperCase(Locale.ROOT),
                        normalizeAll(s.getStringList("outdated-lore-contains")),
                        normalizeAll(s.getStringList("required-lore-contains")),
                        s.getBoolean("compare-template", false)));
            }
        }
    }

    private static List<String> normalizeAll(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(MmoItems.normalize(s));
        return out;
    }

    /** Starts the optional repeating scan. Call after load(). */
    public void startTasks() {
        if (!enabled || intervalSeconds <= 0) return;
        long ticks = intervalSeconds * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) scan(p, false);
        }, ticks, ticks);
    }

    public boolean enabled() { return enabled; }
    public int ruleCount() { return rules.size(); }
    public boolean triggerJoin() { return trigJoin; }
    public boolean triggerInventoryClose() { return trigClose; }
    public boolean triggerPickup() { return trigPickup; }

    public void forget(UUID uuid) {
        lastScan.remove(uuid);
    }

    private UpdateRule find(ItemRef ref) {
        for (UpdateRule r : rules) if (r.appliesTo(ref.type(), ref.id())) return r;
        return null;
    }

    public boolean isOutdated(ItemStack stack) {
        if (!enabled) return false;
        ItemRef ref = MmoItems.identify(stack);
        if (ref == null) return false;
        UpdateRule rule = find(ref);
        return rule != null && matchesRule(stack, rule, ref) && sane(rule, ref);
    }

    /** Does this stack look outdated according to the rule? (no sanity check) */
    private boolean matchesRule(ItemStack stack, UpdateRule rule, ItemRef ref) {
        List<String> lines = new ArrayList<>();
        for (String l : MmoItems.plainLore(stack)) lines.add(MmoItems.normalize(l));

        for (String old : rule.outdatedContains()) {
            if (old.isEmpty()) continue;
            if (lines.stream().anyMatch(l -> l.contains(old))) return true;
        }
        for (String needed : rule.requiredContains()) {
            if (needed.isEmpty()) continue;
            if (lines.stream().noneMatch(l -> l.contains(needed))) return true;
        }
        if (rule.compareTemplate()) {
            ItemStack fresh = MmoItems.create(new ItemRef(ref.type(), ref.id(), 1));
            if (fresh != null) return !comparable(MmoItems.plainLore(stack)).equals(comparable(MmoItems.plainLore(fresh)));
        }
        return false;
    }

    private List<String> comparable(List<String> lore) {
        List<String> out = new ArrayList<>();
        for (String raw : lore) {
            String line = MmoItems.normalize(raw);
            boolean skip = false;
            for (Pattern p : ignore) {
                if (p.matcher(line).find()) { skip = true; break; }
            }
            if (!skip) out.add(line);
        }
        return out;
    }

    /**
     * Guards against rules that would loop: a freshly generated item must NOT be flagged outdated,
     * and the template must exist. Cached until the next reload.
     */
    private boolean sane(UpdateRule rule, ItemRef ref) {
        String cacheKey = rule.key() + "|" + ref.key();
        Boolean cached = saneCache.get(cacheKey);
        if (cached != null) return cached;

        boolean ok;
        ItemStack fresh = MmoItems.create(new ItemRef(ref.type(), ref.id(), 1));
        if (fresh == null) {
            plugin.getLogger().warning("[ItemUpdate] Rule '" + rule.key() + "': MMOItem " + ref.key()
                    + " cannot be generated, items of it will not be updated.");
            ok = false;
        } else if (matchesRule(fresh, rule, ref)) {
            plugin.getLogger().warning("[ItemUpdate] Rule '" + rule.key() + "' also flags a FRESH " + ref.key()
                    + " as outdated (old lore text still present, new text missing, or random rolls in compare-template). "
                    + "Fix the rule or add ignore-lore-regex. Items of it will not be updated.");
            ok = false;
        } else {
            ok = true;
        }
        saneCache.put(cacheKey, ok);
        return ok;
    }

    /** Called by the listeners; respects the per-player cooldown. */
    public void requestScan(Player player) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        Long last = lastScan.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) return;
        lastScan.put(player.getUniqueId(), now);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) scan(player, false);
        });
    }

    /** @return number of stacks that were replaced. Must be called on the main thread. */
    public int scan(Player player, boolean force) {
        if (!enabled || rules.isEmpty()) return 0;
        if (!force && player.hasPermission("octammo.update.bypass")) return 0;

        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        int done = 0;

        for (int slot = 0; slot < contents.length && done < maxPerScan; slot++) {
            ItemStack stack = contents[slot];
            if (Items.isEmpty(stack)) continue;
            ItemRef ref = MmoItems.identify(stack);
            if (ref == null) continue;
            UpdateRule rule = find(ref);
            if (rule == null || !matchesRule(stack, rule, ref) || !sane(rule, ref)) continue;

            ItemUpdateEvent event = new ItemUpdateEvent(player, stack.clone(), ref, rule.key());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;

            if (replace(player, slot, stack, ref)) done++;
        }

        if (done > 0) {
            plugin.stats().add(player, PlayerStats.ITEMS_UPDATED, done);
            if (notify) plugin.lang().send(player, "update.updated", "count", String.valueOf(done));
            Sounds.play(plugin, player, "update");
        }
        return done;
    }

    private boolean replace(Player player, int slot, ItemStack old, ItemRef ref) {
        int amount = old.getAmount();
        if (useApi) {
            ItemStack fresh = MmoItems.create(new ItemRef(ref.type(), ref.id(), 1));
            if (fresh == null) return false;
            fresh.setAmount(Math.min(amount, fresh.getMaxStackSize()));
            player.getInventory().setItem(slot, fresh);
            int rest = amount - fresh.getAmount();
            if (rest > 0) MmoItems.give(player, fresh, rest);
        } else {
            player.getInventory().setItem(slot, null);
            String cmd = command
                    .replace("{type}", ref.type())
                    .replace("{id}", ref.id())
                    .replace("{player}", player.getName())
                    .replace("{amount}", String.valueOf(amount));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        return true;
    }
}
