package io.octammo.enchant;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import io.octammo.gui.OctaGui;
import io.octammo.hook.AdvancedEnchantmentsHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Paged reference browser for the loaded AdvancedEnchantments definitions. */
public final class EnchantmentGuideGui extends OctaGui {
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final EnchantingGui parent;
    private final List<AdvancedEnchantmentsHook.EnchantDefinition> definitions;
    private final int page;

    public EnchantmentGuideGui(OctaMMOPlugin plugin, Player player, EnchantingGui parent, int page) {
        super(plugin, player);
        this.parent = parent;
        this.definitions = plugin.advancedEnchantments().allDefinitions();
        int maxPage = Math.max(0, (definitions.size() - 1) / ENTRY_SLOTS.length);
        this.page = Math.max(0, Math.min(page, maxPage));
        this.inventory = Bukkit.createInventory(this, 54,
                plugin.lang().render("&8✦ &#36D7D2&lEnchantment Guide", player, null));
        draw();
    }

    private ItemStack button(Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(plugin.lang().render(line, player, null));
        return Items.make(material, plugin.lang().render(name, player, null), lines);
    }

    private void draw() {
        inventory.clear();
        Material pane = Material.CYAN_STAINED_GLASS_PANE;
        for (int slot : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,46,47,48,50,51,52}) {
            inventory.setItem(slot, button(pane, "&#36D7D2&l✦", "&8OctaMMO Enchantment Archive"));
        }

        int pages = Math.max(1, (definitions.size() + ENTRY_SLOTS.length - 1) / ENTRY_SLOTS.length);
        inventory.setItem(4, button(Material.BOOKSHELF, "&#36D7D2&lCUSTOM ENCHANTMENT GUIDE",
                "&7Browse every enchantment loaded from AdvancedEnchantments.",
                "&7Total enchantments: &f" + definitions.size(),
                "&7Page: &f" + (page + 1) + "&7/&f" + pages));

        int start = page * ENTRY_SLOTS.length;
        for (int i = 0; i < ENTRY_SLOTS.length; i++) {
            int index = start + i;
            if (index >= definitions.size()) continue;
            AdvancedEnchantmentsHook.EnchantDefinition definition = definitions.get(index);
            ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = icon.getItemMeta();
            String color = tierColor(definition.group());
            meta.displayName(plugin.lang().render(color + "&l✦ " + definition.displayName(), player, null));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.lang().render("&8&m--------------------------------", player, null));
            lore.add(plugin.lang().render("&7ID: &f" + definition.id(), player, null));
            lore.add(plugin.lang().render("&7Tier: " + color + pretty(definition.group()), player, null));
            lore.add(plugin.lang().render("&7Maximum Level: &e" + definition.maxLevel(), player, null));
            if (!definition.applies().isEmpty()) {
                lore.add(plugin.lang().render("&7Applies to: &f" + String.join(", ", definition.applies()), player, null));
            }
            if (!definition.description().isBlank()) {
                lore.add(plugin.lang().render("", player, null));
                for (String line : wrap(definition.description(), 42)) {
                    lore.add(plugin.lang().render("&7" + line, player, null));
                }
            }
            lore.add(plugin.lang().render("", player, null));
            lore.add(plugin.lang().render("&eLoaded from AdvancedEnchantments", player, null));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(ENTRY_SLOTS[i], Items.markVirtual(icon));
        }

        inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "&e&l« PREVIOUS PAGE", "&7Go to the previous page."));
        inventory.setItem(BACK_SLOT, button(Material.WRITABLE_BOOK, "&#36D7D2&lBACK TO ENCHANTING",
                "&7Return to your item and enchantment list."));
        inventory.setItem(NEXT_SLOT, button(Material.ARROW, "&e&lNEXT PAGE »", "&7Go to the next page."));
        if (definitions.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, "&c&lNO ENCHANTMENTS LOADED",
                    "&7AdvancedEnchantments is unavailable, or no valid definitions were found.",
                    "&7Check plugins/AdvancedEnchantments/Enchantments/"));
        }
    }

    private String tierColor(String group) {
        return switch (group.toUpperCase(Locale.ROOT)) {
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
        String[] parts = id.toLowerCase(Locale.ROOT).split("[_ -]+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.replace('\n', ' ').split("\\s+")) {
            if (word.isBlank()) continue;
            if (line.length() > 0 && line.length() + word.length() + 1 > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        int slot = event.getRawSlot();
        int pages = Math.max(1, (definitions.size() + ENTRY_SLOTS.length - 1) / ENTRY_SLOTS.length);
        if (slot == BACK_SLOT) {
            parent.showFromChild();
        } else if (slot == PREVIOUS_SLOT && page > 0) {
            switchTo(new EnchantmentGuideGui(plugin, player, parent, page - 1));
        } else if (slot == NEXT_SLOT && page + 1 < pages) {
            switchTo(new EnchantmentGuideGui(plugin, player, parent, page + 1));
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (transition) return;
        parent.returnInputToPlayer();
    }
}
