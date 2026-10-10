package io.octammo.update;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.MmoItemRef;
import io.octammo.api.UpdateResult;
import io.octammo.api.event.ItemUpdateEvent;
import io.octammo.api.event.ItemUpdatePreEvent;
import io.octammo.core.ItemRef;
import io.octammo.core.Items;
import io.octammo.core.MmoItems;
import io.octammo.core.PlayerStats;
import io.octammo.core.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Item Update: replaces outdated MMOItems, for free, without MMOItems' revision id.
 * An item is outdated when its stored signature differs from the template signature (see {@link TemplateSignatures}).
 *
 * The replacement is a transaction inside one tick: the old stack is detached, the give command runs, and the old stack
 * only stays gone when the new item is verified (inventory or dropped next to the player); otherwise everything is rolled back.
 */
public final class ItemUpdater {
    private enum Decision { SKIP, UPDATE, STAMP }

    private final OctaMMOPlugin plugin;
    private final TemplateSignatures signatures;
    private final Map<UUID, Long> lastScan = new HashMap<>();
    private final Map<UUID, Long> lastOpenScan = new HashMap<>();
    private final Map<UUID, Map<String, Long>> recent = new HashMap<>();
    private BukkitTask intervalTask;

    private boolean enabled, requirePermission, notify, useApi, whitelistMode, enderChest, audit;
    private String giveCommand, unsigned;
    private Set<String> ignoreTypes = Set.of(), ignoreIds = Set.of(), whitelist = Set.of();
    private long cooldownMs, debounceMs;
    private int maxBatch, intervalSeconds;
    private boolean trigJoin, trigOpen, trigPickup;
    private boolean pDurability;
    private List<String> pNbt = List.of();

    public ItemUpdater(OctaMMOPlugin plugin) {
        this.plugin = plugin;
        this.signatures = new TemplateSignatures(plugin);
    }

    public void load() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("modules.item-update", true);
        requirePermission = c.getBoolean("update.require-permission", true);
        notify = c.getBoolean("update.notify-player", true);
        useApi = c.getString("update.method", "COMMAND").equalsIgnoreCase("API");
        giveCommand = c.getString("update.give-command", "mmoitems give {type} {id} {player} {amount}");
        unsigned = c.getString("update.unsigned-items", "UPDATE").toUpperCase(Locale.ROOT);
        whitelistMode = c.getBoolean("update.whitelist-mode", false);
        whitelist = upper(c.getStringList("update.whitelist"));
        ignoreTypes = upper(c.getStringList("update.ignore-types"));
        ignoreIds = upper(c.getStringList("update.ignore-ids"));
        enderChest = c.getBoolean("update.update-ender-chest", false);
        audit = c.getBoolean("update.audit-log", true);
        cooldownMs = Math.max(0, c.getLong("update.cooldown-ms", 3000));
        debounceMs = Math.max(0, c.getLong("update.inventory-open-debounce-ms", 1500));
        maxBatch = Math.max(1, c.getInt("update.max-items-per-batch", 20));
        trigJoin = c.getBoolean("update.triggers.join", true);
        trigOpen = c.getBoolean("update.triggers.inventory-open", true);
        trigPickup = c.getBoolean("update.triggers.pickup", true);
        intervalSeconds = c.getInt("update.triggers.interval-seconds", 0);
        pDurability = c.getBoolean("update.preserve.durability", false);
        List<String> nbt = new ArrayList<>();
        if (c.getBoolean("update.preserve.soulbound", false)) nbt.add("MMOITEMS_SOULBOUND");
        if (c.getBoolean("update.preserve.gems", false)) nbt.add("MMOITEMS_GEM_STONES");
        if (c.getBoolean("update.preserve.upgrade-level", false)) nbt.add("MMOITEMS_UPGRADE");
        nbt.addAll(c.getStringList("update.preserve.custom-nbt-keys"));
        pNbt = List.copyOf(nbt);

        if (intervalTask != null) {
            intervalTask.cancel();
            intervalTask = null;
        }
        if (enabled) {
            signatures.reload();
            if (intervalSeconds > 0) {
                long ticks = intervalSeconds * 20L;
                intervalTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) scan(p, false);
                }, ticks, ticks);
            }
        } else {
            signatures.stop();
        }
    }

    public void stop() {
        signatures.stop();
        if (intervalTask != null) intervalTask.cancel();
    }

    private static Set<String> upper(List<String> in) {
        Set<String> out = new HashSet<>();
        for (String s : in) out.add(s.toUpperCase(Locale.ROOT));
        return out;
    }

    public boolean enabled() { return enabled; }
    public boolean triggerJoin() { return trigJoin; }
    public boolean triggerInventoryOpen() { return trigOpen; }
    public boolean triggerPickup() { return trigPickup; }
    public boolean requirePermission() { return requirePermission; }
    public int templateCount() { return signatures.size(); }

    public void forget(UUID id) {
        lastScan.remove(id);
        lastOpenScan.remove(id);
        recent.remove(id);
    }

    // ------------------------------------------------------------------ detection

    private boolean ignored(ItemRef ref) {
        if (ignoreTypes.contains(ref.type()) || ignoreIds.contains(ref.id()) || ignoreIds.contains(ref.key())) return true;
        return whitelistMode && !whitelist.contains(ref.key()) && !whitelist.contains(ref.type()) && !whitelist.contains(ref.id());
    }

    private Decision decide(ItemStack stack, ItemRef ref, String templateSig) {
        String sig = MmoItems.signatureOf(stack);
        if (sig == null) {
            return switch (unsigned) {
                case "STAMP" -> Decision.STAMP;
                case "SKIP" -> Decision.SKIP;
                default -> Decision.UPDATE;
            };
        }
        return sig.equals(templateSig) ? Decision.SKIP : Decision.UPDATE;
    }

    public boolean needsUpdate(ItemStack stack) {
        if (!enabled || !signatures.ready() || Items.isEmpty(stack) || !stack.hasItemMeta()) return false;
        ItemRef ref = MmoItems.identify(stack);
        if (ref == null || ignored(ref)) return false;
        String sig = signatures.get(ref.type(), ref.id());
        return sig != null && decide(stack, ref, sig) == Decision.UPDATE;
    }

    // ------------------------------------------------------------------ scanning

    /** Respects the per-player cooldown. Used by the triggers. */
    public void requestScan(Player p) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        Long last = lastScan.get(p.getUniqueId());
        if (last != null && now - last < cooldownMs) return;
        lastScan.put(p.getUniqueId(), now);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) scan(p, false);
        });
    }

    /** Debounced variant for the inventory-open trigger. */
    public void requestScanOnOpen(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastOpenScan.get(p.getUniqueId());
        if (last != null && now - last < debounceMs) return;
        lastOpenScan.put(p.getUniqueId(), now);
        requestScan(p);
    }

    /** @return number of replaced stacks. Main thread only. */
    public int scan(Player p, boolean force) {
        if (!enabled || !signatures.ready()) return 0;
        if (!force) {
            if (requirePermission && !p.hasPermission("octammo.update")) return 0;
            if (p.hasPermission("octammo.update.bypass")) return 0;
        }
        if (plugin.loading().isUpgrading(p)) return 0;

        int done = 0;
        Inventory inv = p.getInventory();
        int size = inv.getContents().length;
        for (int slot = 0; slot < size && done < maxBatch; slot++) {
            if (updateSlot(p, inv, slot, force, false) == UpdateResult.UPDATED) done++;
        }
        if (enderChest) {
            Inventory ender = p.getEnderChest();
            for (int slot = 0; slot < ender.getSize() && done < maxBatch; slot++) {
                if (updateSlot(p, ender, slot, force, true) == UpdateResult.UPDATED) done++;
            }
        }
        if (done > 0) {
            plugin.stats().add(p, PlayerStats.ITEMS_UPDATED, done);
            if (notify) plugin.lang().send(p, "update.updated", "count", String.valueOf(done));
            Sounds.play(plugin, p, "update");
        }
        return done;
    }

    /** One slot of the player's main inventory (used by /octammo update hand and the API). */
    public UpdateResult updateInventorySlot(Player p, int slot) {
        UpdateResult r = updateSlot(p, p.getInventory(), slot, true, false);
        if (r == UpdateResult.UPDATED) {
            plugin.stats().add(p, PlayerStats.ITEMS_UPDATED, 1);
            Sounds.play(plugin, p, "update");
        }
        return r;
    }

    private UpdateResult updateSlot(Player p, Inventory inv, int slot, boolean manual, boolean enderOnly) {
        ItemStack old = inv.getItem(slot);
        if (Items.isEmpty(old) || !old.hasItemMeta()) return UpdateResult.NOT_MMOITEM;
        ItemRef ref = MmoItems.identify(old);
        if (ref == null) return UpdateResult.NOT_MMOITEM;
        if (ignored(ref)) return UpdateResult.IGNORED;
        String sig = signatures.get(ref.type(), ref.id());
        if (sig == null) return UpdateResult.NOT_NEEDED;

        Decision decision = decide(old, ref, sig);
        if (decision == Decision.SKIP) return UpdateResult.NOT_NEEDED;
        if (decision == Decision.STAMP) {
            inv.setItem(slot, MmoItems.stamp(old.clone(), sig));
            return UpdateResult.NOT_NEEDED;
        }
        // loop guard: the same item is not replaced again within 30 seconds (e.g. a stack that merged and stayed unsigned)
        Map<String, Long> mine = recent.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        long now = System.currentTimeMillis();
        if (!manual && now - mine.getOrDefault(ref.key(), 0L) < 30_000) return UpdateResult.NOT_NEEDED;

        ItemUpdatePreEvent pre = new ItemUpdatePreEvent(p, old.clone(), new MmoItemRef(ref.type(), ref.id()), sig);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return UpdateResult.CANCELLED;

        ItemStack replacement = (useApi || enderOnly) ? replaceViaApi(p, inv, slot, old, ref, sig) : replaceViaCommand(p, inv, slot, old, ref, sig);
        if (replacement == null) {
            log(p, "FAILED", old, ref, null);
            return UpdateResult.FAILED;
        }
        mine.put(ref.key(), now);
        log(p, "UPDATED", old, ref, sig);
        Bukkit.getPluginManager().callEvent(new ItemUpdateEvent(p, old, replacement));
        return UpdateResult.UPDATED;
    }

    private void log(Player p, String what, ItemStack old, ItemRef ref, String sig) {
        if (audit) plugin.updateLog().log(p.getName() + " | " + what + " | " + ref.key() + " x" + old.getAmount()
                + " | oldSig=" + MmoItems.signatureOf(old) + " | newSig=" + sig);
    }

    private ItemStack finalize(ItemStack old, ItemStack fresh, String sig) {
        ItemStack out = MmoItems.preserve(old, fresh, pNbt, pDurability);
        return MmoItems.stamp(out, sig);
    }

    /** Builds the item through the MMOItems API and puts it in the same slot. */
    private ItemStack replaceViaApi(Player p, Inventory inv, int slot, ItemStack old, ItemRef ref, String sig) {
        ItemStack fresh = MmoItems.create(new ItemRef(ref.type(), ref.id(), 1));
        if (fresh == null) return null;
        fresh.setAmount(Math.min(old.getAmount(), Math.max(1, fresh.getMaxStackSize())));
        ItemStack done = finalize(old, fresh, sig);
        inv.setItem(slot, done);
        int rest = old.getAmount() - done.getAmount();
        if (rest > 0) {
            ItemStack more = done.clone();
            more.setAmount(rest);
            Items.giveOrDrop(p, more);
        }
        return done;
    }

    private int nearbyDrops(Player p, ItemRef ref) {
        int total = 0;
        for (Entity e : p.getNearbyEntities(3, 3, 3)) {
            if (e instanceof Item item && ref.matches(MmoItems.identify(item.getItemStack()))) total += item.getItemStack().getAmount();
        }
        return total;
    }

    /**
     * mmoitems give ... as console. The old stack is detached first, so a full inventory can take the new item,
     * and it is put back when the new item cannot be verified.
     */
    private ItemStack replaceViaCommand(Player p, Inventory inv, int slot, ItemStack old, ItemRef ref, String sig) {
        ItemStack oldCopy = old.clone();
        inv.setItem(slot, null);
        ItemStack[] snap = clone(inv.getContents());
        int dropsBefore = nearbyDrops(p, ref);

        String cmd = giveCommand.replace("{type}", ref.type()).replace("{id}", ref.id())
                .replace("{player}", p.getName()).replace("{amount}", String.valueOf(oldCopy.getAmount()));
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Item Update: give command failed: " + e.getMessage());
        }

        ItemStack[] after = inv.getContents();
        int gained = 0;
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < after.length; i++) {
            ItemStack a = after[i];
            if (Items.isEmpty(a) || !ref.matches(MmoItems.identify(a))) continue;
            ItemStack b = snap[i];
            int before = !Items.isEmpty(b) && b.isSimilar(a) ? b.getAmount() : 0;
            if (a.getAmount() > before) {
                gained += a.getAmount() - before;
                changed.add(i);
            }
        }
        int droppedGain = Math.max(0, nearbyDrops(p, ref) - dropsBefore);

        if (gained + droppedGain >= oldCopy.getAmount()) {
            ItemStack last = null;
            for (int i : changed) {
                last = finalize(oldCopy, after[i].clone(), sig);
                inv.setItem(i, last);
            }
            if (droppedGain > 0) {
                for (Entity e : p.getNearbyEntities(3, 3, 3)) {
                    if (e instanceof Item item && ref.matches(MmoItems.identify(item.getItemStack())) && MmoItems.signatureOf(item.getItemStack()) == null) {
                        item.setItemStack(finalize(oldCopy, item.getItemStack().clone(), sig));
                    }
                }
            }
            return last != null ? last : oldCopy;
        }

        // not verified: undo whatever appeared and give the old item back
        plugin.getLogger().warning("Item Update: '" + cmd + "' did not give " + ref.key() + " to " + p.getName() + ", old item restored.");
        for (int i : changed) inv.setItem(i, snap[i]);
        if (Items.isEmpty(inv.getItem(slot))) inv.setItem(slot, oldCopy);
        else Items.giveOrDrop(p, oldCopy);
        return null;
    }

    private static ItemStack[] clone(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] == null ? null : in[i].clone();
        return out;
    }
}
