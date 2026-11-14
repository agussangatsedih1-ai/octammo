package io.octammo.enchant;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import io.octammo.gui.OctaGui;
import io.octammo.hook.AdvancedEnchantmentsHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
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
import java.util.Map;

/** Six-row custom enchanting interface inspired by the supplied reference GUI. */
public final class EnchantingGui extends OctaGui {
    private static final int INPUT_SLOT = 28;
    private static final int HEADER_SLOT = 4;
    private static final int ITEM_INFO_SLOT = 10;
    private static final int SEARCH_SLOT = 45;
    private static final int TIER_SLOT = 46;
    private static final int GUIDE_SLOT = 47;
    private static final int CLEAR_SEARCH_SLOT = 48;
    private static final int PREVIOUS_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int TAKE_INPUT_SLOT = 51;
    private static final int CLOSE_SLOT = 53;
    private static final int[] ENTRY_SLOTS = {22, 23, 24, 25, 26, 31, 32, 33, 34, 35, 40, 41, 42, 43, 44};
    private static final List<String> TIERS = List.of(
            "ALL", "VANILLA", "SIMPLE", "UNIQUE", "ELITE", "ULTIMATE", "LEGENDARY", "FABLED", "HEROIC"
    );

    private record Option(String id, String name, String group, int maxLevel, int currentLevel,
                          String description, Enchantment vanilla) {}

    private String tier = "ALL";
    private String search = "";
    private int page;
    private List<Option> shown = List.of();
    private List<Option> filtered = List.of();
    // Keeps the current GUI session responsive even on AE builds whose public API
    // does not expose a readable custom-enchantment map.
    private final Map<String, Integer> sessionEnchantLevels = new java.util.HashMap<>();

    public EnchantingGui(OctaMMOPlugin plugin, Player player) {
        super(plugin, player);
        this.inventory = Bukkit.createInventory(this, 54,
                plugin.lang().render("&8✦ &#36D7D2&lEnchant Item", player, null));
        drawFrame();
        refresh();
    }

    private void drawFrame() {
        // Keep empty slots empty: the reference uses the vanilla inventory surface,
        // rather than a wall of coloured filler panes that competes with enchant icons.
        inventory.clear();
        inventory.setItem(HEADER_SLOT, button(Material.ENCHANTING_TABLE, "&#36D7D2&lENCHANTING TABLE",
                "&7Place an item in the slot on the left.",
                "&7Compatible enchantments will appear on the right.",
                "&8&m--------------------------------"));
        inventory.setItem(ITEM_INFO_SLOT, button(Material.BOOK, "&f&lITEM INFORMATION",
                "&7The item you place will be checked", "&7against each enchantment's rules."));
        inventory.setItem(INPUT_SLOT, inputPlaceholder());
        inventory.setItem(SEARCH_SLOT, searchButton());
        inventory.setItem(TIER_SLOT, tierButton());
        inventory.setItem(GUIDE_SLOT, guideButton());
        inventory.setItem(CLEAR_SEARCH_SLOT, button(Material.MILK_BUCKET, "&c&lCLEAR SEARCH",
                "&7Remove the current search keyword."));
        inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "&e&l« PREVIOUS PAGE", "&7Browse earlier enchantments."));
        inventory.setItem(NEXT_SLOT, button(Material.ARROW, "&e&lNEXT PAGE »", "&7Browse more enchantments."));
        inventory.setItem(TAKE_INPUT_SLOT, button(Material.HOPPER, "&6&lTAKE ITEM",
                "&7Return the item from the input slot."));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "&c&lCLOSE", "&7Close and return your item."));
    }

    private ItemStack button(Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(plugin.lang().render(line, player, null));
        return Items.make(material, plugin.lang().render(name, player, null), lines);
    }

    private ItemStack inputPlaceholder() {
        return button(Material.ENCHANTING_TABLE, "&#36D7D2&lITEM INPUT",
                "&7Place one item here to begin.", "&8Only one item is processed at a time.");
    }

    private ItemStack searchButton() {
        String shownSearch = search.isBlank() ? "None" : search;
        return button(Material.SPYGLASS, "&#36D7D2&lSEARCH ENCHANTMENTS",
                "&7Find enchantments by name or ID.",
                "&7Current keyword: &f" + shownSearch,
                "", "&eClick to type a keyword in chat.");
    }

    private ItemStack tierButton() {
        String label = tier.equals("ALL") ? "All Tiers" : pretty(tier);
        return button(Material.HOPPER, "&#36D7D2&lTIER FILTER: &f" + label,
                "&7Current filter: &f" + label,
                "&8&m--------------------------------",
                "&fAvailable tiers:",
                "&7All Tiers", "&fVanilla", "&aSimple", "&bUnique", "&3Elite",
                "&5Ultimate", "&6Legendary", "&dFabled", "&9Heroic",
                "", "&eLeft-click: &7Next tier",
                "&eRight-click: &7Show all tiers");
    }

    private ItemStack guideButton() {
        return button(Material.ENCHANTED_BOOK, "&#36D7D2&lENCHANTMENT GUIDE",
                "&7Browse every loaded custom enchantment.",
                "&7Read its description, tier and item types.",
                "", "&eClick to open the guide.");
    }

    private ItemStack input() {
        ItemStack current = inventory.getItem(INPUT_SLOT);
        return Items.isReal(current) ? current : null;
    }

    private void refresh() {
        ItemStack current = input();
        if (current == null) inventory.setItem(INPUT_SLOT, inputPlaceholder());

        List<Option> options = new ArrayList<>();
        if (current != null) {
            if (tier.equals("ALL") || tier.equals("VANILLA")) addVanillaOptions(current, options);
            if (!tier.equals("VANILLA")) addAdvancedOptions(current, options);
        }

        String needle = search.toLowerCase(Locale.ROOT).trim();
        if (!needle.isEmpty()) {
            options.removeIf(option -> !option.id().toLowerCase(Locale.ROOT).contains(needle)
                    && !option.name().toLowerCase(Locale.ROOT).contains(needle)
                    && !option.description().toLowerCase(Locale.ROOT).contains(needle));
        }
        options.sort(Comparator.comparing(Option::name, String.CASE_INSENSITIVE_ORDER));
        filtered = List.copyOf(options);

        int maxPage = Math.max(0, (filtered.size() - 1) / ENTRY_SLOTS.length);
        page = Math.min(page, maxPage);
        int start = page * ENTRY_SLOTS.length;
        shown = filtered.stream().skip(start).limit(ENTRY_SLOTS.length).toList();

        for (int i = 0; i < ENTRY_SLOTS.length; i++) {
            int slot = ENTRY_SLOTS[i];
            if (i < shown.size()) inventory.setItem(slot, enchantIcon(shown.get(i)));
            else inventory.setItem(slot, null);
        }
        if (current == null) {
            inventory.setItem(23, button(Material.ENCHANTED_BOOK, "&#36D7D2&lENCHANTMENT LIST",
                    "&7Insert an item in the left slot to view", "&7compatible Vanilla and custom enchants."));
        } else if (shown.isEmpty()) {
            inventory.setItem(23, button(Material.BARRIER, "&c&lNO MATCHING ENCHANTMENTS",
                    "&7Try another tier or clear your search."));
        }

        String itemName = current == null ? "No item inserted" : readableName(current);
        inventory.setItem(HEADER_SLOT, button(Material.ENCHANTING_TABLE, "&#36D7D2&lENCHANTING TABLE",
                "&7Item: &f" + itemName,
                "&7Tier: &f" + pretty(tier),
                "&7Matches: &f" + filtered.size(),
                search.isBlank() ? "&7Search: &fNone" : "&7Search: &f" + search,
                "&8&m--------------------------------",
                "&7Click an enchantment to apply the next level.",
                "&7Shift-click to apply its maximum level."));
        inventory.setItem(ITEM_INFO_SLOT, button(current == null ? Material.BOOK : current.getType(),
                "&f&lITEM INFORMATION",
                "&7Item: &f" + itemName,
                current == null ? "&cInsert an item to view compatible enchants." : "&aItem detected.",
                "&7Only compatible enchantments are listed."));
        inventory.setItem(SEARCH_SLOT, searchButton());
        inventory.setItem(TIER_SLOT, tierButton());
        inventory.setItem(GUIDE_SLOT, guideButton());
        inventory.setItem(CLEAR_SEARCH_SLOT, button(Material.MILK_BUCKET, "&c&lCLEAR SEARCH",
                search.isBlank() ? "&7No search keyword is active." : "&7Current keyword: &f" + search,
                "&eClick to clear the keyword."));
        inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "&e&l« PREVIOUS PAGE",
                "&7Page: &f" + (page + 1) + "&7/&f" + (maxPage + 1)));
        inventory.setItem(NEXT_SLOT, button(Material.ARROW, "&e&lNEXT PAGE »",
                "&7Page: &f" + (page + 1) + "&7/&f" + (maxPage + 1)));
    }

    private void addVanillaOptions(ItemStack current, List<Option> options) {
        ItemMeta meta = current.getItemMeta();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            if (!enchantment.canEnchantItem(current)) continue;
            String id = enchantment.getKey().getKey();
            int existing = meta == null ? 0 : meta.getEnchantLevel(enchantment);
            options.add(new Option(id, pretty(id), "VANILLA", enchantment.getMaxLevel(), existing,
                    "Vanilla Minecraft enchantment.", enchantment));
        }
    }

    private void addAdvancedOptions(ItemStack current, List<Option> options) {
        if (!plugin.advancedEnchantments().available()) return;
        Map<String, Integer> currentEnchants = new java.util.HashMap<>(plugin.advancedEnchantments().readEnchantLevels(current));
        sessionEnchantLevels.forEach((id, level) -> currentEnchants.merge(id, level, Math::max));
        for (AdvancedEnchantmentsHook.EnchantDefinition definition : plugin.advancedEnchantments().definitionsFor(current)) {
            if (!tier.equals("ALL") && !definition.group().equalsIgnoreCase(tier)) continue;
            int level = currentEnchants.getOrDefault(definition.id().toLowerCase(Locale.ROOT), 0);
            options.add(new Option(definition.id(), definition.displayName(), definition.group(),
                    definition.maxLevel(), level, definition.description(), null));
        }
    }

    private ItemStack enchantIcon(Option option) {
        Material material = option.group().equals("VANILLA") ? Material.ENCHANTED_BOOK : Material.BOOK;
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        String color = tierColor(option.group());
        meta.displayName(plugin.lang().render(color + "&l✦ " + option.name(), player, null));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.lang().render("&8&m--------------------------------", player, null));
        lore.add(plugin.lang().render("&7Tier: " + color + pretty(option.group()), player, null));
        lore.add(plugin.lang().render("&7Current Level: &f" + option.currentLevel(), player, null));
        lore.add(plugin.lang().render("&7Maximum Level: &e" + option.maxLevel(), player, null));
        lore.add(plugin.lang().render("&7Cost: &6" + cost(option.group(), Math.min(option.maxLevel(), option.currentLevel() + 1)) + " XP level(s)", player, null));
        if (!option.description().isBlank()) {
            lore.add(plugin.lang().render("", player, null));
            lore.add(plugin.lang().render("&7" + option.description(), player, null));
        }
        lore.add(plugin.lang().render("", player, null));
        lore.add(plugin.lang().render("&e➤ Click: &7Apply next level", player, null));
        lore.add(plugin.lang().render("&e➤ Shift-click: &7Apply maximum level", player, null));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return Items.markVirtual(icon);
    }

    private int cost(String group, int level) {
        String key = group.equals("VANILLA") ? "enchanting.costs.vanilla-per-level"
                : "enchanting.costs.advanced." + group.toLowerCase(Locale.ROOT);
        int perLevel = Math.max(0, plugin.getConfig().getInt(key, group.equals("VANILLA") ? 1 : 2));
        return perLevel * Math.max(1, level);
    }

    private String tierColor(String group) {
        return switch (group.toUpperCase(Locale.ROOT)) {
            case "VANILLA" -> "&f";
            case "SIMPLE" -> "&a";
            case "UNIQUE" -> "&b";
            case "ELITE" -> "&3";
            case "ULTIMATE" -> "&5";
            case "LEGENDARY" -> "&6";
            case "FABLED" -> "&d";
            case "HEROIC" -> "&9";
            default -> "&f";
        };
    }

    private String pretty(String id) {
        if (id == null || id.isBlank()) return "All Tiers";
        String[] parts = id.toLowerCase(Locale.ROOT).split("[_ -]+");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (name.length() > 0) name.append(' ');
            if (!part.isEmpty()) name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    private String readableName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().displayName() != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(stack.getItemMeta().displayName());
        }
        return pretty(stack.getType().name());
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
                if (player.getGameMode() != GameMode.CREATIVE && Items.isReal(source) && input() == null) {
                    ItemStack one = source.clone();
                    one.setAmount(1);
                    inventory.setItem(INPUT_SLOT, one);
                    sessionEnchantLevels.clear();
                    if (source.getAmount() <= 1) e.setCurrentItem(null);
                    else {
                        source.setAmount(source.getAmount() - 1);
                        e.setCurrentItem(source);
                    }
                    refresh();
                }
            }
            return;
        }

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == INPUT_SLOT) {
            swapInput(e);
            refresh();
            return;
        }
        if (slot == SEARCH_SLOT) {
            plugin.enchantingSearch().begin(player, this);
            plugin.lang().send(player, "enchanting.search-prompt");
            return;
        }
        if (slot == TIER_SLOT) {
            if (e.isRightClick()) tier = "ALL";
            else {
                int current = TIERS.indexOf(tier);
                tier = TIERS.get((current + 1) % TIERS.size());
            }
            page = 0;
            refresh();
            return;
        }
        if (slot == GUIDE_SLOT) {
            switchTo(new EnchantmentGuideGui(plugin, player, this, 0));
            return;
        }
        if (slot == CLEAR_SEARCH_SLOT) {
            search = "";
            page = 0;
            refresh();
            return;
        }
        if (slot == PREVIOUS_SLOT) {
            page = Math.max(0, page - 1);
            refresh();
            return;
        }
        if (slot == NEXT_SLOT) {
            if ((long) (page + 1) * ENTRY_SLOTS.length < filtered.size()) page++;
            refresh();
            return;
        }
        if (slot == TAKE_INPUT_SLOT) {
            ItemStack current = input();
            if (current != null) {
                inventory.setItem(INPUT_SLOT, inputPlaceholder());
                sessionEnchantLevels.clear();
                Items.giveOrDrop(player, current);
                refresh();
            }
            return;
        }
        for (int i = 0; i < ENTRY_SLOTS.length; i++) {
            if (slot == ENTRY_SLOTS[i] && i < shown.size()) {
                apply(shown.get(i), e.isShiftClick());
                return;
            }
        }
    }

    private void swapInput(InventoryClickEvent e) {
        ItemStack cursor = e.getCursor();
        ItemStack current = input();
        if (Items.isEmpty(cursor)) {
            if (current != null) {
                inventory.setItem(INPUT_SLOT, inputPlaceholder());
                sessionEnchantLevels.clear();
                e.setCursor(current);
            }
            return;
        }
        if (!Items.isReal(current)) {
            sessionEnchantLevels.clear();
            ItemStack one = cursor.clone();
            one.setAmount(1);
            inventory.setItem(INPUT_SLOT, one);
            if (cursor.getAmount() <= 1) e.setCursor(null);
            else {
                ItemStack remainder = cursor.clone();
                remainder.setAmount(cursor.getAmount() - 1);
                e.setCursor(remainder);
            }
            return;
        }
        if (cursor.getAmount() != 1) {
            plugin.lang().send(player, "enchanting.single-item-only");
            return;
        }
        inventory.setItem(INPUT_SLOT, cursor.clone());
        sessionEnchantLevels.clear();
        e.setCursor(current);
    }

    private void apply(Option option, boolean maximum) {
        ItemStack current = input();
        if (current == null) return;
        int targetLevel = maximum ? option.maxLevel() : Math.min(option.maxLevel(), option.currentLevel() + 1);
        if (option.currentLevel() >= option.maxLevel()) {
            plugin.lang().send(player, "enchanting.max-level", "enchant", option.name());
            return;
        }
        int price = cost(option.group(), targetLevel);
        if (player.getLevel() < price) {
            plugin.lang().send(player, "enchanting.not-enough-xp", "cost", String.valueOf(price));
            return;
        }

        ItemStack updated;
        if (option.vanilla() != null) {
            updated = current.clone();
            ItemMeta meta = updated.getItemMeta();
            if (meta == null) return;
            meta.addEnchant(option.vanilla(), targetLevel, true);
            updated.setItemMeta(meta);
        } else {
            updated = plugin.advancedEnchantments().apply(option.id(), targetLevel, current);
            if (updated == null) {
                plugin.lang().send(player, "enchanting.apply-failed");
                return;
            }
        }
        if (price > 0) player.giveExpLevels(-price);
        if (option.vanilla() == null) {
            sessionEnchantLevels.put(option.id().toLowerCase(Locale.ROOT), targetLevel);
        }
        updated.setAmount(1);
        inventory.setItem(INPUT_SLOT, updated);
        plugin.lang().send(player, "enchanting.applied", "enchant", option.name(), "level", String.valueOf(targetLevel));
        refresh();
    }

    /** Called by the chat listener after the player finishes a search prompt. */
    public void acceptSearch(String keyword) {
        if (!player.isOnline()) return;
        String value = keyword == null ? "" : keyword.trim();
        if (value.equalsIgnoreCase("cancel")) {
            plugin.lang().send(player, "enchanting.search-cancelled");
            return;
        }
        search = value;
        page = 0;
        plugin.lang().send(player, "enchanting.search-updated", "keyword", value.isBlank() ? "None" : value);
        refresh();
    }

    /** Reopens this GUI when a child page returns to it. */
    public void showFromChild() {
        switchTo(this);
    }

    /** Returns the held input when the guide is closed without using its Back button. */
    public void returnInputToPlayer() {
        plugin.enchantingSearch().end(player);
        ItemStack current = input();
        if (current != null) Items.giveOrDrop(player, current);
        sessionEnchantLevels.clear();
        inventory.setItem(INPUT_SLOT, inputPlaceholder());
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        plugin.enchantingSearch().end(player);
        if (transition) return;
        returnInputToPlayer();
    }
}
