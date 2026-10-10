package io.octammo.gui;

import io.octammo.OctaMMO;
import io.octammo.core.Items;
import io.octammo.core.Lang;
import io.octammo.core.MmoItems;
import io.octammo.core.Sounds;
import io.octammo.upgrade.Cost;
import io.octammo.upgrade.CostService;
import io.octammo.upgrade.UpgradeFile;
import io.octammo.upgrade.UpgradeRecipe;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * "Altar Forge": 6-row upgrade GUI for ONE path of a station.
 * The player puts the item to upgrade and every item cost into the GUI before pressing the upgrade button.
 */
public final class ForgeGui extends OctaGui {
    public static final Set<String> SINGLES = Set.of("info", "input", "output-preview", "requirements", "chance",
            "booster", "protection", "upgrade-button", "back", "cancel");
    public static final Set<String> LISTS = Set.of("rune-ring", "progress-bar");

    public enum Problem { NONE, NO_INPUT, RUNES, COSTS, ECONOMY, PERMISSION, REGION }

    /** One rune slot: it asks for {@code required} pieces of one item cost. */
    public record RuneSpec(int slot, Cost cost, int required) {}

    private static final ItemDef D_INFO = ItemDef.of(Material.ENCHANTING_TABLE, "{station}", "&7Path: &f{path}", "{description}");
    private static final ItemDef D_INPUT = ItemDef.of(Material.ITEM_FRAME, "{gui.input.empty}", "{gui.input.empty-lore}");
    private static final ItemDef D_RUNE_EMPTY = ItemDef.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "{gui.rune.empty}", "{gui.rune.empty-lore}");
    private static final ItemDef D_RUNE_LOCKED = ItemDef.of(Material.BLACK_STAINED_GLASS_PANE, " ");
    private static final ItemDef D_RUNE_MISSING = ItemDef.of(Material.RED_STAINED_GLASS_PANE, " ");
    private static final ItemDef D_RUNE_OK = ItemDef.of(Material.LIME_STAINED_GLASS_PANE, " ");
    private static final ItemDef D_BOOSTER = ItemDef.of(Material.GLOWSTONE_DUST, "{gui.booster.empty}", "{gui.booster.empty-lore}");
    private static final ItemDef D_BOOSTER_LOCKED = ItemDef.of(Material.BLACK_STAINED_GLASS_PANE, "{gui.booster.locked}");
    private static final ItemDef D_PROTECTION = ItemDef.of(Material.SHIELD, "{gui.protection.empty}", "{gui.protection.empty-lore}");
    private static final ItemDef D_PROTECTION_LOCKED = ItemDef.of(Material.BLACK_STAINED_GLASS_PANE, "{gui.protection.locked}");
    private static final ItemDef D_REQUIREMENTS = ItemDef.of(Material.PAPER, "{gui.requirements.name}", "{lines}");
    private static final ItemDef D_CHANCE = ItemDef.of(Material.CLOCK, "{gui.chance.name}", "{gui.chance.lore}");
    private static final ItemDef D_OUTPUT_MISSING = ItemDef.of(Material.BARRIER, "{gui.output.missing}");
    private static final ItemDef D_GO_READY = ItemDef.of(Material.LIME_CONCRETE, "{gui.upgrade.ready}", "{gui.upgrade.ready-lore}").withGlow();
    private static final ItemDef D_GO_NOT_READY = ItemDef.of(Material.RED_CONCRETE, "{gui.upgrade.not-ready}", "{gui.upgrade.not-ready-lore}");
    private static final ItemDef D_GO_ANIMATING = ItemDef.of(Material.ORANGE_CONCRETE, "{gui.upgrade.animating}");
    private static final ItemDef D_BACK = ItemDef.of(Material.ARROW, "{gui.back}");
    private static final ItemDef D_CANCEL = ItemDef.of(Material.BARRIER, "{gui.cancel}");

    private final UpgradeFile file;
    private final UpgradeRecipe recipe;
    private final GuiLayout layout;
    private final List<RuneSpec> runes;
    private final Set<Integer> interactive = new HashSet<>();
    private final List<Integer> borderSlots = new ArrayList<>();
    private final Map<Integer, String> states = new HashMap<>();
    private final Map<Material, ItemStack> panes = new EnumMap<>(Material.class);

    private final int inSlot, infoSlot, outSlot, reqSlot, chanceSlot, boosterSlot, protectionSlot, goSlot, backSlot, cancelSlot;
    private final List<Integer> ring, progress, runeStatus;

    private ItemStack outputPreview;
    private ItemStack inputCopy;
    private boolean locked;
    private BukkitTask shimmer;
    private int shimmerTick;

    /** @return the GUI, or null when the path needs more rune slots than the layout offers. */
    public static ForgeGui create(OctaMMO plugin, UpgradeFile file, UpgradeRecipe recipe, org.bukkit.entity.Player player) {
        GuiLayout layout = plugin.guis().forge(file.gui());
        List<RuneSpec> runes = planRunes(recipe, layout.slots("rune-ring"));
        return runes == null ? null : new ForgeGui(plugin, file, recipe, layout, runes, player);
    }

    /** Splits every item cost over as many rune slots as its stack size needs. */
    private static List<RuneSpec> planRunes(UpgradeRecipe recipe, List<Integer> ring) {
        List<RuneSpec> out = new ArrayList<>();
        int next = 0;
        for (Cost c : recipe.costs()) {
            if (!CostService.isItemCost(c)) continue;
            int total = CostService.amountOf(c);
            int perSlot = stackSize(c);
            while (total > 0) {
                if (next >= ring.size()) return null;
                int n = Math.min(perSlot, total);
                out.add(new RuneSpec(ring.get(next++), c, n));
                total -= n;
            }
        }
        return out;
    }

    private static int stackSize(Cost c) {
        if (c instanceof Cost.Vanilla v) return Math.max(1, v.material().getMaxStackSize());
        if (c instanceof Cost.MmoItem i) {
            ItemStack s = MmoItems.create(i.ref());
            return s == null ? 64 : Math.max(1, s.getMaxStackSize());
        }
        return 1;
    }

    private ForgeGui(OctaMMO plugin, UpgradeFile file, UpgradeRecipe recipe, GuiLayout layout,
                     List<RuneSpec> runes, org.bukkit.entity.Player player) {
        super(plugin, player);
        this.file = file;
        this.recipe = recipe;
        this.layout = layout;
        this.runes = runes;
        inSlot = layout.slot("input");
        infoSlot = layout.slot("info");
        outSlot = layout.slot("output-preview");
        reqSlot = layout.slot("requirements");
        chanceSlot = layout.slot("chance");
        boosterSlot = layout.slot("booster");
        protectionSlot = layout.slot("protection");
        goSlot = layout.slot("upgrade-button");
        backSlot = layout.slot("back");
        cancelSlot = layout.slot("cancel");
        ring = layout.slots("rune-ring");
        progress = layout.slots("progress-bar");
        runeStatus = layout.slots("rune-status");

        interactive.add(inSlot);
        for (RuneSpec r : runes) interactive.add(r.slot());
        if (!file.boosters().isEmpty()) interactive.add(boosterSlot);
        if (!file.protections().isEmpty()) interactive.add(protectionSlot);

        Component title = Lang.color(plugin.lang().expand(layout.title(), player, Map.of("station", file.title())));
        this.inventory = Bukkit.createInventory(this, 54, title);
        this.outputPreview = buildOutputPreview();
        drawStatic();
        refresh();
    }

    public UpgradeFile file() { return file; }
    public UpgradeRecipe recipe() { return recipe; }
    public boolean locked() { return locked; }

    // ------------------------------------------------------------------ drawing

    private Map<String, String> base() {
        Map<String, String> m = new HashMap<>();
        m.put("station", file.title());
        m.put("path", recipe.name());
        m.put("description", String.join("\n", recipe.description()));
        return m;
    }

    private Map<String, String> with(Map<String, String> m, String... kv) {
        Map<String, String> copy = new HashMap<>(m);
        for (int i = 0; i + 1 < kv.length; i += 2) copy.put(kv[i], kv[i + 1]);
        return copy;
    }

    private ItemStack item(String path, ItemDef fallback, Map<String, String> ph) {
        return layout.item(path, fallback).build(plugin, player, ph);
    }

    private ItemStack pane(Material m) {
        return panes.computeIfAbsent(m, k -> Items.make(k, Component.space(), null));
    }

    private Material configMaterial(String path, Material fallback) {
        Material m = Material.matchMaterial(plugin.getConfig().getString(path, ""));
        return m == null || m.isAir() ? fallback : m;
    }

    private ItemStack buildOutputPreview() {
        ItemStack real = MmoItems.create(recipe.output());
        if (real == null) return item("output-missing", D_OUTPUT_MISSING, base());
        real.setAmount(Math.min(recipe.output().amount(), real.getMaxStackSize()));
        ItemMeta meta = real.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.addAll(plugin.lang().components("gui.output.footer"));
        meta.lore(lore);
        real.setItemMeta(meta);
        return Items.markVirtual(real);
    }

    private void drawStatic() {
        Set<Integer> functional = layout.functionalSlots();
        for (int i = 0; i < 54; i++) {
            if (functional.contains(i)) continue;
            boolean edge = i < 9 || i % 9 == 0 || i % 9 == 8;
            if (edge) borderSlots.add(i);
            inventory.setItem(i, pane(edge ? layout.border() : layout.filler()));
        }
        inventory.setItem(infoSlot, item("info", D_INFO, base()));
        inventory.setItem(backSlot, item("back", D_BACK, base()));
        inventory.setItem(cancelSlot, item("cancel", D_CANCEL, base()));
        inventory.setItem(outSlot, outputPreview);
        Material pending = configMaterial("gui-animation.progress-bar.pending-material", Material.GRAY_STAINED_GLASS_PANE);
        for (int s : progress) inventory.setItem(s, pane(pending));
    }

    /** Sets a virtual item only when it is missing or its state changed, never over a real item. */
    private void put(int slot, String state, Supplier<ItemStack> supplier) {
        ItemStack cur = inventory.getItem(slot);
        if (Items.isReal(cur)) {
            states.remove(slot);
            return;
        }
        if (state.equals(states.get(slot)) && Items.isVirtual(cur)) return;
        inventory.setItem(slot, supplier.get());
        states.put(slot, state);
    }

    private ItemStack real(int slot) {
        ItemStack s = inventory.getItem(slot);
        return Items.isReal(s) ? s : null;
    }

    /** Re-renders only what changed. Does nothing while an upgrade is running. */
    public void refresh() {
        if (locked) return;
        Map<String, String> base = base();
        Problem problem = problem();

        if (real(inSlot) == null) {
            String name = MmoItems.displayName(recipe.input());
            put(inSlot, "in|" + name, () -> item("input-empty", D_INPUT, with(base, "name", name)));
        }

        Set<Integer> active = new HashSet<>();
        for (int i = 0; i < runes.size(); i++) {
            RuneSpec r = runes.get(i);
            active.add(r.slot());
            String name = plugin.costService().nameOf(r.cost());
            put(r.slot(), "rune|" + name + "|" + r.required(),
                    () -> item("rune-empty", D_RUNE_EMPTY, with(base, "name", name, "required", String.valueOf(r.required()))));
        }
        for (int s : ring) {
            if (!active.contains(s)) put(s, "rune-locked", () -> item("rune-locked", D_RUNE_LOCKED, base));
        }
        for (int i = 0; i < runeStatus.size(); i++) {
            int s = runeStatus.get(i);
            if (i >= runes.size()) {
                put(s, "st-locked", () -> item("rune-locked", D_RUNE_LOCKED, base));
                continue;
            }
            RuneSpec r = runes.get(i);
            int have = have(r);
            if (have <= 0) put(s, "st-empty", () -> item("rune-status-empty", D_RUNE_LOCKED, base));
            else if (have < r.required()) put(s, "st-missing", () -> item("rune-missing", D_RUNE_MISSING, base));
            else put(s, "st-ok", () -> item("rune-ok", D_RUNE_OK, base));
        }

        if (interactive.contains(boosterSlot)) {
            put(boosterSlot, "booster", () -> item("booster-empty", D_BOOSTER, base));
        } else {
            put(boosterSlot, "booster-locked", () -> item("booster-locked", D_BOOSTER_LOCKED, base));
        }
        if (interactive.contains(protectionSlot)) {
            put(protectionSlot, "prot", () -> item("protection-empty", D_PROTECTION, base));
        } else {
            put(protectionSlot, "prot-locked", () -> item("protection-locked", D_PROTECTION_LOCKED, base));
        }

        // requirements checklist
        List<String> lines = new ArrayList<>();
        for (Cost c : recipe.costs()) {
            boolean ok;
            String text;
            if (CostService.isItemCost(c)) {
                int need = CostService.amountOf(c);
                int have = 0;
                for (RuneSpec r : runes) if (r.cost() == c) have += have(r);
                ok = have >= need;
                text = plugin.lang().format("gui.costs.progress", "cost", plugin.costService().describe(c),
                        "have", String.valueOf(have), "need", String.valueOf(need));
            } else {
                ok = plugin.costService().hasNonItem(player, c);
                text = plugin.costService().describe(c);
            }
            lines.add(plugin.lang().format(ok ? "gui.costs.line-ok" : "gui.costs.line-missing", "cost", text));
        }
        if (lines.isEmpty()) lines.add(plugin.lang().format("gui.costs.none"));
        String joined = String.join("\n", lines);
        put(reqSlot, "req|" + joined, () -> item("requirements", D_REQUIREMENTS, with(base, "lines", joined)));

        // chance
        UpgradeFile.Booster booster = activeBooster();
        double bonus = booster == null ? 0 : booster.bonus();
        double finalChance = Math.max(0, Math.min(100, recipe.chance() + bonus));
        String fail = plugin.lang().format(recipe.onFail() == UpgradeRecipe.FailMode.DESTROY ? "fail.destroy" : "fail.keep");
        String chanceState = "ch|" + recipe.chance() + "|" + bonus + "|" + fail;
        put(chanceSlot, chanceState, () -> item("chance", D_CHANCE, with(base,
                "base", fmt(recipe.chance()), "bonus", fmt(bonus), "percent", fmt(finalChance),
                "bar", bar(finalChance), "fail", fail)));

        // upgrade button
        boolean ready = problem == Problem.NONE;
        String reason = plugin.lang().format("upgrade.problem." + problem.name().toLowerCase().replace('_', '-'));
        put(goSlot, "go|" + problem, () -> ready
                ? item("upgrade-button.ready", D_GO_READY, base)
                : item("upgrade-button.not-ready", D_GO_NOT_READY, with(base, "reason", reason)));
    }

    public static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static String bar(double percent) {
        int filled = (int) Math.round(percent / 5.0);
        return "&a" + "|".repeat(Math.max(0, filled)) + "&7" + "|".repeat(Math.max(0, 20 - filled));
    }

    private void refreshLater() {
        Bukkit.getScheduler().runTask(plugin, this::refresh);
    }

    private void syncLater() {
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    // ------------------------------------------------------------------ state

    private int have(RuneSpec r) {
        ItemStack s = real(r.slot());
        return s != null && CostService.matches(r.cost(), s) ? s.getAmount() : 0;
    }

    public UpgradeFile.Booster activeBooster() {
        if (!interactive.contains(boosterSlot)) return null;
        ItemStack s = real(boosterSlot);
        return s == null ? null : file.boosterFor(MmoItems.identify(s));
    }

    public UpgradeFile.Protection activeProtection() {
        if (!interactive.contains(protectionSlot)) return null;
        ItemStack s = real(protectionSlot);
        return s == null ? null : file.protectionFor(MmoItems.identify(s));
    }

    public Problem problem() {
        ItemStack in = real(inSlot);
        if (in == null || !recipe.input().matches(MmoItems.identify(in))) return Problem.NO_INPUT;
        for (RuneSpec r : runes) if (have(r) < r.required()) return Problem.RUNES;
        if (!plugin.upgrades().canUse(player, file)
                || (!recipe.permission().isBlank() && !player.hasPermission(recipe.permission()))) return Problem.PERMISSION;
        if (!plugin.upgrades().regionAllowed(player)) return Problem.REGION;
        boolean needsMoney = recipe.costs().stream().anyMatch(c -> c instanceof Cost.Money m && m.amount() > 0);
        if (needsMoney && !plugin.economyAvailable()) return Problem.ECONOMY;
        for (Cost c : recipe.costs()) if (!plugin.costService().hasNonItem(player, c)) return Problem.COSTS;
        return Problem.NONE;
    }

    // ------------------------------------------------------------------ transaction (used by UpgradeService)

    public void lock() {
        locked = true;
    }

    /** Aborts a start that failed before anything was taken. */
    public void unlock() {
        locked = false;
        refresh();
    }

    /**
     * Takes everything out of the interactive slots. What is not consumed (leftover stacks, an input that
     * survives a failure, an unused protection) is returned as the list of rewards.
     */
    public List<ItemStack> settle(boolean success, boolean protectionUsed) {
        List<ItemStack> rewards = new ArrayList<>();
        boolean consumeInput = success || (recipe.onFail() == UpgradeRecipe.FailMode.DESTROY && !protectionUsed);

        ItemStack in = real(inSlot);
        inputCopy = in == null ? null : in.clone();
        if (in != null) addLeft(rewards, in, consumeInput ? 1 : 0);
        inventory.setItem(inSlot, null);

        for (RuneSpec r : runes) {
            ItemStack s = real(r.slot());
            if (s != null) addLeft(rewards, s, r.required());
            inventory.setItem(r.slot(), null);
        }
        if (interactive.contains(boosterSlot)) {
            ItemStack s = real(boosterSlot);
            if (s != null) addLeft(rewards, s, activeBooster() != null ? 1 : 0);
            inventory.setItem(boosterSlot, null);
        }
        if (interactive.contains(protectionSlot)) {
            ItemStack s = real(protectionSlot);
            if (s != null) addLeft(rewards, s, protectionUsed ? 1 : 0);
            inventory.setItem(protectionSlot, null);
        }
        return rewards;
    }

    private static void addLeft(List<ItemStack> rewards, ItemStack stack, int consumed) {
        int left = stack.getAmount() - consumed;
        if (left <= 0) return;
        ItemStack copy = stack.clone();
        copy.setAmount(left);
        rewards.add(copy);
    }

    /** Draws the locked look used while the animation runs. */
    public void renderAnimating() {
        states.clear();
        Map<String, String> base = base();
        ItemStack locked = item("rune-locked", D_RUNE_LOCKED, base);
        inventory.setItem(inSlot, inputCopy == null ? locked : Items.markVirtual(inputCopy.clone()));
        for (int s : ring) inventory.setItem(s, locked);
        for (int s : runeStatus) inventory.setItem(s, locked);
        inventory.setItem(boosterSlot, locked);
        inventory.setItem(protectionSlot, locked);
        inventory.setItem(goSlot, item("upgrade-button.animating", D_GO_ANIMATING, base));
    }

    /** Fills the progress panels: done / active (blinking) / pending. */
    public void setProgress(int percent, int tick) {
        int n = progress.size();
        if (n == 0) return;
        Material done = configMaterial("gui-animation.progress-bar.done-material", Material.LIME_STAINED_GLASS_PANE);
        Material pending = configMaterial("gui-animation.progress-bar.pending-material", Material.GRAY_STAINED_GLASS_PANE);
        Material active = configMaterial("gui-animation.progress-bar.active-material", Material.YELLOW_STAINED_GLASS_PANE);
        int filled = percent * n / 100;
        for (int i = 0; i < n; i++) {
            Material m;
            if (i < filled) m = done;
            else if (i == filled && percent < 100) m = (tick / 4) % 2 == 0 ? active : pending;
            else m = pending;
            setIfChanged(progress.get(i), pane(m));
        }
    }

    public void paintBorder(int tick) {
        List<Material> colors = layout.shimmerColors();
        if (colors.isEmpty()) return;
        for (int i = 0; i < borderSlots.size(); i++) {
            setIfChanged(borderSlots.get(i), pane(colors.get(Math.floorMod(tick + i, colors.size()))));
        }
    }

    private void setIfChanged(int slot, ItemStack want) {
        if (!java.util.Objects.equals(inventory.getItem(slot), want)) inventory.setItem(slot, want);
    }

    /** The upgrade is over: unlock and draw the normal look again. */
    public void endAnimation() {
        inputCopy = null;
        states.clear();
        locked = false;
        Material pending = configMaterial("gui-animation.progress-bar.pending-material", Material.GRAY_STAINED_GLASS_PANE);
        for (int s : progress) setIfChanged(s, pane(pending));
        Material border = layout.border();
        for (int s : borderSlots) setIfChanged(s, pane(border));
        refresh();
    }

    public boolean isViewing() {
        return player.isOnline() && player.getOpenInventory().getTopInventory() == inventory;
    }

    // ------------------------------------------------------------------ idle shimmer

    private void startShimmer() {
        if (shimmer != null || layout.shimmerColors().isEmpty()) return;
        if (!plugin.getConfig().getBoolean("gui-animation.idle-shimmer", true)) return;
        shimmer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isViewing()) {
                stopShimmer();
                return;
            }
            if (locked) return;
            paintBorder(++shimmerTick);
        }, 10L, 10L);
    }

    private void stopShimmer() {
        if (shimmer != null) {
            shimmer.cancel();
            shimmer = null;
        }
    }

    @Override
    public void onOpened() {
        startShimmer();
        refresh();
    }

    // ------------------------------------------------------------------ clicks

    private boolean accepts(int slot, ItemStack item) {
        if (!Items.isReal(item)) return false;
        if (slot == inSlot) return recipe.input().matches(MmoItems.identify(item));
        if (slot == boosterSlot) return interactive.contains(slot) && file.boosterFor(MmoItems.identify(item)) != null;
        if (slot == protectionSlot) return interactive.contains(slot) && file.protectionFor(MmoItems.identify(item)) != null;
        for (RuneSpec r : runes) if (r.slot() == slot) return CostService.matches(r.cost(), item);
        return false;
    }

    private void reject(int slot) {
        if (slot == inSlot) {
            plugin.lang().send(player, "upgrade.gui.reject-input", "name", MmoItems.displayName(recipe.input()));
        } else if (slot == boosterSlot) {
            plugin.lang().send(player, "upgrade.gui.reject-booster");
        } else if (slot == protectionSlot) {
            plugin.lang().send(player, "upgrade.gui.reject-protection");
        } else {
            for (RuneSpec r : runes) {
                if (r.slot() == slot) {
                    plugin.lang().send(player, "upgrade.gui.reject-rune", "name", plugin.costService().nameOf(r.cost()));
                    return;
                }
            }
        }
    }

    /** The item that the click would move INTO the slot. */
    private ItemStack incoming(InventoryClickEvent e) {
        ClickType click = e.getClick();
        if (click == ClickType.LEFT || click == ClickType.RIGHT) return e.getCursor();
        if (click == ClickType.NUMBER_KEY) {
            int b = e.getHotbarButton();
            return b >= 0 ? player.getInventory().getItem(b) : null;
        }
        if (click == ClickType.SWAP_OFFHAND) return player.getInventory().getItemInOffHand();
        return null;
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        if (locked || player.getGameMode() == GameMode.CREATIVE) {
            e.setCancelled(true);
            return;
        }
        InventoryAction action = e.getAction();
        if (action == InventoryAction.NOTHING) return;
        if (action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.CLONE_STACK
                || action == InventoryAction.UNKNOWN) {
            e.setCancelled(true);
            return;
        }

        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        if (clicked != inventory) {   // the player's own inventory
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
                quickPlace(e);
                return;
            }
            // Bedrock (Floodgate): a plain tap moves the item into the GUI
            if (plugin.floodgate() != null && plugin.getConfig().getBoolean("hooks.floodgate.quick-place", true)
                    && e.getClick() == ClickType.LEFT && Items.isEmpty(e.getCursor()) && Items.isReal(e.getCurrentItem())
                    && plugin.floodgate().isBedrock(player.getUniqueId())) {
                e.setCancelled(true);
                quickPlace(e);
                return;
            }
            refreshLater();
            return;
        }

        int slot = e.getRawSlot();
        if (slot == goSlot) {
            e.setCancelled(true);
            Sounds.play(plugin, player, "click");
            plugin.service().tryStart(this);
            return;
        }
        if (slot == backSlot) {
            e.setCancelled(true);
            back();
            return;
        }
        if (slot == cancelSlot) {
            e.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (!interactive.contains(slot)) {
            e.setCancelled(true);
            return;
        }

        ItemStack current = inventory.getItem(slot);
        if (!Items.isReal(current)) {      // empty or a placeholder: place by hand, never swap with the placeholder
            e.setCancelled(true);
            manualPlace(e, slot);
            return;
        }

        switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_SOME, PICKUP_ONE, DROP_ALL_SLOT, DROP_ONE_SLOT,
                 MOVE_TO_OTHER_INVENTORY -> refreshLater();
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                ItemStack in = incoming(e);
                if (!Items.isEmpty(in) && !accepts(slot, in)) {
                    e.setCancelled(true);
                    reject(slot);
                } else {
                    refreshLater();
                }
            }
            default -> e.setCancelled(true);
        }
    }

    /** Places the incoming item into an empty slot by hand (the event is already cancelled). */
    private void manualPlace(InventoryClickEvent e, int slot) {
        ItemStack src = incoming(e);
        if (!Items.isReal(src)) return;
        if (!accepts(slot, src)) {
            reject(slot);
            return;
        }
        ClickType click = e.getClick();
        if (click == ClickType.LEFT) {
            inventory.setItem(slot, src.clone());
            player.setItemOnCursor(null);
        } else if (click == ClickType.RIGHT) {
            ItemStack one = src.clone();
            one.setAmount(1);
            inventory.setItem(slot, one);
            ItemStack rest = src.clone();
            rest.setAmount(src.getAmount() - 1);
            player.setItemOnCursor(rest.getAmount() > 0 ? rest : null);
        } else if (click == ClickType.NUMBER_KEY) {
            inventory.setItem(slot, src.clone());
            player.getInventory().setItem(e.getHotbarButton(), null);
        } else if (click == ClickType.SWAP_OFFHAND) {
            inventory.setItem(slot, src.clone());
            player.getInventory().setItemInOffHand(null);
        } else {
            return;
        }
        states.remove(slot);
        refresh();
        syncLater();
    }

    /** Shift-click from the player's inventory: input first, then runes, booster, protection. */
    private void quickPlace(InventoryClickEvent e) {
        ItemStack src = e.getCurrentItem();
        if (!Items.isReal(src)) return;
        ItemStack remaining = src.clone();

        List<Integer> order = new ArrayList<>();
        order.add(inSlot);
        for (RuneSpec r : runes) order.add(r.slot());
        if (interactive.contains(boosterSlot)) order.add(boosterSlot);
        if (interactive.contains(protectionSlot)) order.add(protectionSlot);

        for (int slot : order) {
            if (remaining.getAmount() <= 0) break;
            if (!accepts(slot, remaining)) continue;
            int max = Math.max(1, remaining.getMaxStackSize());
            ItemStack cur = real(slot);
            if (cur == null) {
                int n = Math.min(max, remaining.getAmount());
                ItemStack put = remaining.clone();
                put.setAmount(n);
                inventory.setItem(slot, put);
                remaining.setAmount(remaining.getAmount() - n);
                states.remove(slot);
            } else if (cur.isSimilar(remaining) && cur.getAmount() < max) {
                int n = Math.min(max - cur.getAmount(), remaining.getAmount());
                ItemStack merged = cur.clone();
                merged.setAmount(cur.getAmount() + n);
                inventory.setItem(slot, merged);
                remaining.setAmount(remaining.getAmount() - n);
            }
        }
        if (remaining.getAmount() != src.getAmount()) {
            e.setCurrentItem(remaining.getAmount() > 0 ? remaining : null);
            refresh();
            syncLater();
        } else {
            plugin.lang().send(player, "upgrade.gui.reject-quick");
        }
    }

    /** Gives every real item in the interactive slots back to the player. */
    public void returnAll() {
        for (int slot : interactive) {
            ItemStack s = inventory.getItem(slot);
            if (Items.isReal(s)) Items.giveOrDrop(player, s);
            inventory.setItem(slot, null);
        }
        states.clear();
    }

    private void back() {
        returnAll();
        if (file.recipes().size() > 1) {
            switchTo(new PathSelectGui(plugin, file, player));
        } else {
            player.closeInventory();
        }
    }

    public void switchToConfirm() {
        switchTo(new ConfirmGui(plugin, this));
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        stopShimmer();
        if (transition || locked) return;   // while locked everything was already taken by the upgrade
        returnAll();
    }
}
