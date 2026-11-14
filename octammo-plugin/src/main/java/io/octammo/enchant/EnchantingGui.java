package io.octammo.enchant;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import io.octammo.gui.OctaGui;
import io.octammo.hook.AdvancedEnchantmentsHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Six-row custom enchanting interface for vanilla and AdvancedEnchantments enchantments. */
public final class EnchantingGui extends OctaGui {
    private static final int INPUT_SLOT = 10;
    private static final int PREVIOUS_SLOT = 4;
    private static final int NEXT_SLOT = 5;
    private static final int CLOSE_SLOT = 53;
    private static final int[] ENTRY_SLOTS = {12, 13, 14, 15, 16, 21, 22, 23, 24, 25, 30, 31, 32, 33, 34, 39, 40, 41, 42, 43};
    private static final List<String> GROUPS = List.of("VANILLA", "SIMPLE", "UNIQUE", "ELITE", "ULTIMATE", "LEGENDARY", "FABLED");
    private static final int[] GROUP_SLOTS = {45, 46, 47, 48, 49, 50, 51};

    private record Option(String id, String name, String group, int maxLevel, Enchantment vanilla) {}

    private String group = "VANILLA";
    private int page;
    private List<Option> shown = List.of();
    private List<Option> availableOptions = List.of();

    public EnchantingGui(OctaMMOPlugin plugin, Player player) {
        super(plugin, player);
        this.inventory = Bukkit.createInventory(this, 54, plugin.lang().render("&8✦ &#A855F7&lOctaMMO &8» &dEnchanting", player, null));
        drawFrame();
        refresh();
    }

    private void drawFrame() {
        for (int slot = 0; slot < 54; slot++) {
            boolean edge = slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
            inventory.setItem(slot, Items.make(edge ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, Component.space(), null));
        }
        inventory.setItem(PREVIOUS_SLOT, make(Material.ARROW, "&e&l« Previous Page", "&7Browse earlier enchantments."));
        inventory.setItem(NEXT_SLOT, make(Material.ARROW, "&e&lNext Page »", "&7Browse more enchantments."));
        inventory.setItem(CLOSE_SLOT, make(Material.BARRIER, "&c&l✕ Close", "&7Return the item to your inventory."));
        inventory.setItem(4, make(Material.BOOK, "&d&lENCHANTMENT INDEX", "&7Place an item in the left slot.", "&7Choose a tier to browse enchantments.", "&8&m                                  ", "&7Normal click: apply level I", "&7Shift click: apply maximum level"));
        inventory.setItem(INPUT_SLOT, make(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&e&lITEM SLOT", "&7Place the item you want to enchant here."));
        for (int i = 0; i < GROUPS.size(); i++) {
            String selected = GROUPS.get(i);
            Material icon = switch (selected) {
                case "VANILLA" -> Material.ENCHANTED_BOOK;
                case "SIMPLE" -> Material.BOOK;
                case "UNIQUE" -> Material.IRON_SWORD;
                case "ELITE" -> Material.DIAMOND_SWORD;
                case "ULTIMATE" -> Material.NETHER_STAR;
                case "LEGENDARY" -> Material.END_CRYSTAL;
                default -> Material.DRAGON_HEAD;
            };
            inventory.setItem(GROUP_SLOTS[i], make(icon, (selected.equals(group) ? "&a&l➤ " : "&e&l") + selected,
                    "&7Click to view " + selected.toLowerCase(Locale.ROOT) + " enchantments."));
        }
    }

    private ItemStack make(Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(plugin.lang().render(line, player, null));
        return Items.make(material, plugin.lang().render(name, player, null), lines);
    }

    private ItemStack input() {
        ItemStack current = inventory.getItem(INPUT_SLOT);
        return Items.isReal(current) ? current : null;
    }

    private void refresh() {
        ItemStack current = input();
        if (current == null) {
            inventory.setItem(INPUT_SLOT, make(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&e&lITEM SLOT", "&7Place the item you want to enchant here."));
        }
        List<Option> options = new ArrayList<>();
        if (current != null) {
            if (group.equals("VANILLA")) {
                for (Enchantment enchantment : Registry.ENCHANTMENT) {
                    if (!enchantment.canEnchantItem(current)) continue;
                    String id = enchantment.getKey().getKey();
                    options.add(new Option(id, pretty(id), "VANILLA", enchantment.getMaxLevel(), enchantment));
                }
            } else if (plugin.advancedEnchantments().available()) {
                for (AdvancedEnchantmentsHook.EnchantDefinition definition : plugin.advancedEnchantments().definitions(group, current)) {
                    options.add(new Option(definition.id(), definition.displayName(), definition.group(), definition.maxLevel(), null));
                }
            }
        }
        options.sort(Comparator.comparing(Option::name, String.CASE_INSENSITIVE_ORDER));
        availableOptions = List.copyOf(options);
        int maxPage = Math.max(0, (availableOptions.size() - 1) / ENTRY_SLOTS.length);
        page = Math.min(page, maxPage);
        int start = page * ENTRY_SLOTS.length;
        shown = availableOptions.stream().skip(start).limit(ENTRY_SLOTS.length).toList();
        for (int i = 0; i < ENTRY_SLOTS.length; i++) {
            int slot = ENTRY_SLOTS[i];
            if (i >= shown.size()) {
                inventory.setItem(slot, Items.make(Material.GRAY_STAINED_GLASS_PANE, Component.space(), null));
                continue;
            }
            Option option = shown.get(i);
            inventory.setItem(slot, enchantIcon(option));
        }
        inventory.setItem(PREVIOUS_SLOT, make(Material.ARROW, "&e&l« Previous Page", "&7Current page: &f" + (page + 1), "&7Click to go back.") );
        inventory.setItem(NEXT_SLOT, make(Material.ARROW, "&e&lNext Page »", "&7Current page: &f" + (page + 1), "&7Click to continue."));
        inventory.setItem(4, make(Material.BOOK, "&d&lENCHANTMENT INDEX", "&7Selected tier: &f" + group,
                current == null ? "&cPlace an item in the left slot first." : "&aItem detected. Select an enchantment.",
                "&8&m                                  ", "&7Normal click: apply level I", "&7Shift click: apply maximum level"));
    }

    private ItemStack enchantIcon(Option option) {
        ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(plugin.lang().render("&d&l✦ " + option.name(), player, null));
        meta.lore(List.of(
                plugin.lang().render("&8&m                              ", player, null),
                plugin.lang().render("&7Tier: &f" + option.group(), player, null),
                plugin.lang().render("&7Maximum Level: &e" + option.maxLevel(), player, null),
                plugin.lang().render("&7Cost: &6" + cost(option.group(), 1) + " XP Level(s)", player, null),
                plugin.lang().render("", player, null),
                plugin.lang().render("&e➤ Click to apply", player, null),
                plugin.lang().render("&7Shift-click for max level", player, null)));
        icon.setItemMeta(meta);
        return Items.markVirtual(icon);
    }

    private int cost(String tier, int level) {
        String key = tier.equals("VANILLA") ? "enchanting.costs.vanilla-per-level" : "enchanting.costs.advanced." + tier.toLowerCase(Locale.ROOT);
        return Math.max(0, plugin.getConfig().getInt(key, tier.equals("VANILLA") ? 1 : 2) * level);
    }

    private String pretty(String id) {
        String[] parts = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (name.length() > 0) name.append(' ');
            if (!part.isEmpty()) name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != inventory) {
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack source = e.getCurrentItem();
                if (Items.isReal(source) && input() == null && player.getGameMode() != GameMode.CREATIVE) {
                    ItemStack one = source.clone();
                    one.setAmount(1);
                    inventory.setItem(INPUT_SLOT, one);
                    if (source.getAmount() <= 1) e.setCurrentItem(null);
                    else { source.setAmount(source.getAmount() - 1); e.setCurrentItem(source); }
                    refresh();
                }
            } else {
                e.setCancelled(false);
            }
            return;
        }
        e.setCancelled(true);
        if (player.getGameMode() == GameMode.CREATIVE) return;
        int slot = e.getRawSlot();
        if (slot == CLOSE_SLOT) { player.closeInventory(); return; }
        if (slot == INPUT_SLOT) { swapInput(e); refresh(); return; }
        if (slot == PREVIOUS_SLOT) { page = Math.max(0, page - 1); refresh(); return; }
        if (slot == NEXT_SLOT) { if ((long) (page + 1) * ENTRY_SLOTS.length < availableOptions.size()) page++; refresh(); return; }
        for (int i = 0; i < GROUP_SLOTS.length; i++) {
            if (slot == GROUP_SLOTS[i]) { group = GROUPS.get(i); page = 0; refresh(); return; }
        }
        for (int i = 0; i < ENTRY_SLOTS.length; i++) {
            if (slot == ENTRY_SLOTS[i] && i < shown.size()) { apply(shown.get(i), e.isShiftClick()); return; }
        }
    }

    private void swapInput(InventoryClickEvent e) {
        ItemStack cursor = e.getCursor();
        ItemStack current = inventory.getItem(INPUT_SLOT);
        if (Items.isEmpty(cursor)) {
            if (Items.isReal(current)) { e.setCursor(current.clone()); inventory.setItem(INPUT_SLOT, null); }
        } else if (!Items.isReal(current)) {
            ItemStack one = cursor.clone();
            one.setAmount(1);
            inventory.setItem(INPUT_SLOT, one);
            if (cursor.getAmount() <= 1) e.setCursor(null);
            else { ItemStack rest = cursor.clone(); rest.setAmount(cursor.getAmount() - 1); e.setCursor(rest); }
        } else if (cursor.getAmount() == 1) {
            inventory.setItem(INPUT_SLOT, cursor.clone());
            e.setCursor(current.clone());
        } else {
            plugin.lang().send(player, "enchanting.single-item-only");
        }
    }

    private void apply(Option option, boolean maximum) {
        ItemStack current = input();
        if (current == null) return;
        if (current.getAmount() != 1) {
            plugin.lang().send(player, "enchanting.single-item-only");
            return;
        }
        int level = maximum ? option.maxLevel() : 1;
        int cost = cost(option.group(), level);
        if (player.getLevel() < cost) {
            plugin.lang().send(player, "enchanting.not-enough-xp", "cost", String.valueOf(cost));
            return;
        }
        ItemStack updated;
        if (option.vanilla() != null) {
            updated = current.clone();
            ItemMeta meta = updated.getItemMeta();
            if (meta == null) return;
            meta.addEnchant(option.vanilla(), level, true);
            updated.setItemMeta(meta);
        } else {
            updated = plugin.advancedEnchantments().apply(option.id(), level, current);
            if (updated == null) {
                plugin.lang().send(player, "enchanting.apply-failed");
                return;
            }
        }
        if (cost > 0) player.giveExpLevels(-cost);
        updated.setAmount(current.getAmount());
        inventory.setItem(INPUT_SLOT, updated);
        plugin.lang().send(player, "enchanting.applied", "enchant", option.name(), "level", String.valueOf(level));
        refresh();
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        ItemStack current = inventory.getItem(INPUT_SLOT);
        if (Items.isReal(current)) Items.giveOrDrop(player, current);
        inventory.setItem(INPUT_SLOT, null);
    }
}
