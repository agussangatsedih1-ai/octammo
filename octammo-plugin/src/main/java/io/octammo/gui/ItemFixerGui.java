package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.ItemRef;
import io.octammo.core.Items;
import io.octammo.core.MmoItems;
import io.octammo.hook.AdvancedEnchantmentsHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Repairs an MMOItems item from its canonical TYPE/ID or extracts AE enchants into book items. */
public final class ItemFixerGui extends OctaGui {
    private static final int INPUT_SLOT = 20;
    private static final int EXTRACT_SLOT = 24;
    private static final int REPAIR_SLOT = 30;
    private static final int CLOSE_SLOT = 49;

    public ItemFixerGui(OctaMMOPlugin plugin, Player player) {
        super(plugin, player);
        this.inventory = Bukkit.createInventory(this, 54, plugin.lang().render("&8✦ &#F59E0B&lOctaMMO &8» &6Item Fixer", player, null));
        draw();
    }

    private void draw() {
        for (int slot = 0; slot < 54; slot++) {
            boolean edge = slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
            inventory.setItem(slot, item(edge ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(4, item(Material.ANVIL, "&6&lITEM RECONDITIONING", "&7Place one MMOItems item in the slot.", "&7Choose an operation below.", "&8&m                                  ", "&cWarning: repair rebuilds the base item."));
        inventory.setItem(INPUT_SLOT, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&e&lITEM INPUT", "&7Place one MMOItems item here."));
        inventory.setItem(EXTRACT_SLOT, item(Material.ENCHANTED_BOOK, "&d&l✦ EXTRACT AE ENCHANTS", "&7Extract custom enchantments into", "&7enchanted books and clean the item."));
        inventory.setItem(REPAIR_SLOT, item(Material.GRINDSTONE, "&a&l✦ REBUILD ITEM LORE", "&7Rebuild the original MMOItems template", "&7using its stored Type and Item ID.", "&7Vanilla and readable AE enchants are restored."));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "&c&l✕ CLOSE", "&7Return the input item."));
    }

    private ItemStack item(Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(plugin.lang().render(line, player, null));
        return Items.make(material, plugin.lang().render(name, player, null), lines);
    }

    private ItemStack input() {
        ItemStack stack = inventory.getItem(INPUT_SLOT);
        return Items.isReal(stack) ? stack : null;
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
                    int remaining = source.getAmount() - 1;
                    if (remaining <= 0) e.setCurrentItem(null);
                    else { source.setAmount(remaining); e.setCurrentItem(source); }
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
        if (slot == INPUT_SLOT) { swapInput(e); return; }
        if (slot == EXTRACT_SLOT) { extract(); return; }
        if (slot == REPAIR_SLOT) { repair(); }
    }

    private void swapInput(InventoryClickEvent e) {
        ItemStack cursor = e.getCursor();
        ItemStack current = inventory.getItem(INPUT_SLOT);
        if (Items.isEmpty(cursor)) {
            if (Items.isReal(current)) { e.setCursor(current.clone()); inventory.setItem(INPUT_SLOT, null); }
        } else if (!Items.isReal(current)) {
            inventory.setItem(INPUT_SLOT, cursor.clone()); e.setCursor(null);
        } else {
            inventory.setItem(INPUT_SLOT, cursor.clone()); e.setCursor(current.clone());
        }
    }

    private ItemRef requireMmoItem() {
        ItemStack item = input();
        if (item == null) {
            plugin.lang().send(player, "fixer.insert-item");
            return null;
        }
        if (item.getAmount() != 1) {
            plugin.lang().send(player, "fixer.single-item-only");
            return null;
        }
        ItemRef ref = MmoItems.identify(item);
        if (ref == null) plugin.lang().send(player, "fixer.not-mmoitem");
        return ref;
    }

    private void extract() {
        ItemRef ref = requireMmoItem();
        if (ref == null) return;
        if (!plugin.advancedEnchantments().available()) {
            plugin.lang().send(player, "fixer.ae-unavailable");
            return;
        }
        ItemStack original = input();
        List<AdvancedEnchantmentsHook.AppliedEnchant> enchants = plugin.advancedEnchantments().readEnchants(original);
        if (enchants.isEmpty()) {
            plugin.lang().send(player, "fixer.no-ae-enchants");
            return;
        }
        ItemStack clean = MmoItems.create(ref);
        if (clean == null) {
            plugin.lang().send(player, "fixer.template-missing", "type", ref.type(), "id", ref.id());
            return;
        }
        if (plugin.getConfig().getBoolean("item-fixer.preserve-vanilla-enchantments", true)) copyVanillaEnchants(original, clean);
        List<ItemStack> books = new ArrayList<>();
        for (AdvancedEnchantmentsHook.AppliedEnchant enchant : enchants) {
            ItemStack book = plugin.advancedEnchantments().createBook(enchant.id(), enchant.level());
            if (Items.isEmpty(book)) {
                plugin.lang().send(player, "fixer.book-create-failed", "enchant", enchant.id());
                return;
            }
            book.setAmount(1);
            books.add(book);
        }
        inventory.setItem(INPUT_SLOT, null);
        Items.giveOrDrop(player, clean);
        books.forEach(book -> Items.giveOrDrop(player, book));
        plugin.lang().send(player, "fixer.extract-success", "count", String.valueOf(books.size()), "item", MmoItems.displayName(ref));
        player.closeInventory();
    }

    private void repair() {
        ItemRef ref = requireMmoItem();
        if (ref == null) return;
        boolean aeInstalled = Bukkit.getPluginManager().isPluginEnabled("AdvancedEnchantments");
        if (aeInstalled && (!plugin.advancedEnchantments().available() || !plugin.advancedEnchantments().hasDefinitions())) {
            plugin.lang().send(player, "fixer.ae-definitions-unavailable");
            return;
        }
        ItemStack original = input();
        ItemStack clean = MmoItems.create(ref);
        if (clean == null) {
            plugin.lang().send(player, "fixer.template-missing", "type", ref.type(), "id", ref.id());
            return;
        }
        if (plugin.getConfig().getBoolean("item-fixer.preserve-vanilla-enchantments", true)) copyVanillaEnchants(original, clean);
        List<AdvancedEnchantmentsHook.AppliedEnchant> enchants = plugin.advancedEnchantments().readEnchants(original);
        if (!enchants.isEmpty()) {
            if (!plugin.advancedEnchantments().available()) {
                plugin.lang().send(player, "fixer.ae-unavailable");
                return;
            }
            for (AdvancedEnchantmentsHook.AppliedEnchant enchant : enchants) {
                ItemStack updated = plugin.advancedEnchantments().apply(enchant.id(), enchant.level(), clean);
                if (updated == null) {
                    plugin.lang().send(player, "fixer.ae-restore-failed", "enchant", enchant.id());
                    return;
                }
                clean = updated;
            }
        }
        inventory.setItem(INPUT_SLOT, null);
        Items.giveOrDrop(player, clean);
        plugin.lang().send(player, "fixer.repair-success", "item", MmoItems.displayName(ref));
        player.closeInventory();
    }

    private void copyVanillaEnchants(ItemStack source, ItemStack target) {
        if (!source.hasItemMeta() || !target.hasItemMeta()) return;
        ItemMeta from = source.getItemMeta();
        ItemMeta to = target.getItemMeta();
        for (var entry : from.getEnchants().entrySet()) to.addEnchant(entry.getKey(), entry.getValue(), true);
        target.setItemMeta(to);
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        ItemStack item = inventory.getItem(INPUT_SLOT);
        if (Items.isReal(item)) Items.giveOrDrop(player, item);
        inventory.setItem(INPUT_SLOT, null);
    }
}
