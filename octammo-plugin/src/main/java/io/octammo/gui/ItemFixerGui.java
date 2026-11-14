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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Repairs an MMOItems item from its canonical TYPE/ID or extracts AE enchants into book items. */
public final class ItemFixerGui extends OctaGui {
    private final int inputSlot, extractSlot, repairSlot, closeSlot;
    private final FeatureGuiConfig guiConfig;

    public ItemFixerGui(OctaMMOPlugin plugin, Player player) {
        super(plugin, player);
        this.guiConfig = FeatureGuiConfig.load(plugin, "item-fixer.yml", "item-fixer.yml");
        this.inputSlot = guiConfig.slot("input", 20);
        this.extractSlot = guiConfig.slot("extract", 24);
        this.repairSlot = guiConfig.slot("repair", 30);
        this.closeSlot = guiConfig.slot("close", 49);
        this.inventory = Bukkit.createInventory(this, 54, plugin.lang().render(guiConfig.title("&8✦ &#F59E0B&lOCTAMMO &8» &6ITEM FORGE"), player, null));
        draw();
    }

    private void draw() {
        inventory.clear();
        Material pane = Material.matchMaterial(guiConfig.fillerMaterial("PURPLE_STAINED_GLASS_PANE"));
        if (pane == null) pane = Material.PURPLE_STAINED_GLASS_PANE;
        for (int slot : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53}) {
            inventory.setItem(slot, item(pane, guiConfig.fillerName("&#B56CFF&l✦"), guiConfig.fillerLore("&8OctaMMO Item Utility")));
        }
        inventory.setItem(4, item(Material.ANVIL, "&6&lITEM RECONDITIONING", "&7Place one MMOItems item in the slot.", "&7Choose an operation below.", "&8&m                                  ", "&cExtraction creates a clean base item.", "&7Repair refreshes template lore and uses MMOItems keep-data settings."));
        inventory.setItem(inputSlot, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&e&lITEM INPUT", "&7Place one MMOItems item here."));
        inventory.setItem(extractSlot, item(Material.ENCHANTED_BOOK, "&d&l✦ EXTRACT AE ENCHANTS", "&7Extract custom enchantments into", "&7AdvancedEnchantments books.", "&cThe output is a clean base MMOItem."));
        inventory.setItem(repairSlot, item(Material.GRINDSTONE, "&a&l✦ REBUILD ITEM LORE", "&7Refresh the item from its Type and ID.", "&7Old lore is not copied to the rebuilt item.", "&7Other data follows MMOItems keep-data settings."));
        inventory.setItem(closeSlot, item(Material.BARRIER, "&c&l✕ CLOSE", "&7Return the input item."));
    }

    private ItemStack item(Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(plugin.lang().render(line, player, null));
        return Items.make(material, plugin.lang().render(name, player, null), lines);
    }

    private ItemStack input() {
        ItemStack stack = inventory.getItem(inputSlot);
        return Items.isReal(stack) ? stack : null;
    }

    @Override
    protected int inputSlot() { return inputSlot; }

    @Override
    public void handleClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != inventory) {
            if (e.getClickedInventory() == null) { e.setCancelled(true); return; }
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
                    inventory.setItem(inputSlot, one);
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
        int slot = e.getRawSlot();
        if (slot == closeSlot) { player.closeInventory(); return; }
        if (slot == inputSlot) { swapInput(e); return; }
        if (slot == extractSlot) { extract(); return; }
        if (slot == repairSlot) { repair(); }
    }

    private void swapInput(InventoryClickEvent e) {
        // Number-key and offhand swaps are handled explicitly; Bukkit's default
        // inventory transaction is cancelled for every GUI control slot.
        if (e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack external = e.getClick() == ClickType.SWAP_OFFHAND
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItem(e.getHotbarButton());
            ItemStack currentItem = input();
            boolean hotbar = e.getClick() == ClickType.NUMBER_KEY;
            if (!Items.isReal(external)) {
                if (currentItem != null) {
                    if (hotbar) player.getInventory().setItem(e.getHotbarButton(), currentItem);
                    else player.getInventory().setItemInOffHand(currentItem);
                    inventory.setItem(inputSlot, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&e&lITEM INPUT", "&7Place one MMOItems item here."));
                    onInputChanged();
                }
            } else if (currentItem == null) {
                ItemStack one = external.clone(); one.setAmount(1);
                ItemStack remainder = external.clone(); remainder.setAmount(external.getAmount() - 1);
                if (hotbar) player.getInventory().setItem(e.getHotbarButton(), Items.isEmpty(remainder) ? null : remainder);
                else player.getInventory().setItemInOffHand(Items.isEmpty(remainder) ? null : remainder);
                inventory.setItem(inputSlot, one);
                onInputChanged();
            } else if (external.getAmount() == 1) {
                if (hotbar) player.getInventory().setItem(e.getHotbarButton(), currentItem);
                else player.getInventory().setItemInOffHand(currentItem);
                inventory.setItem(inputSlot, external.clone());
                onInputChanged();
            } else {
                plugin.lang().send(player, "fixer.single-item-only");
            }
            return;
        }
        ItemStack cursor = e.getCursor();
        ItemStack current = inventory.getItem(inputSlot);
        if (Items.isEmpty(cursor)) {
            if (Items.isReal(current)) { e.getView().setCursor(current.clone()); inventory.setItem(inputSlot, null); }
        } else if (!Items.isReal(current)) {
            ItemStack one = cursor.clone();
            one.setAmount(1);
            inventory.setItem(inputSlot, one);
            if (cursor.getAmount() <= 1) e.getView().setCursor(null);
            else {
                ItemStack remainder = cursor.clone();
                remainder.setAmount(cursor.getAmount() - 1);
                e.getView().setCursor(remainder);
            }
        } else if (cursor.getAmount() == 1) {
            inventory.setItem(inputSlot, cursor.clone());
            e.getView().setCursor(current.clone());
        } else {
            plugin.lang().send(player, "fixer.single-item-only");
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
        inventory.setItem(inputSlot, null);
        Items.giveOrDrop(player, clean);
        books.forEach(book -> Items.giveOrDrop(player, book));
        plugin.lang().send(player, "fixer.extract-success", "count", String.valueOf(books.size()), "item", MmoItems.displayName(ref));
        player.closeInventory();
    }

    private void repair() {
        ItemRef ref = requireMmoItem();
        if (ref == null) return;
        ItemStack original = input();
        MmoItems.RebuildResult rebuilt = MmoItems.rebuild(original);
        if (rebuilt == null) {
            plugin.lang().send(player, "fixer.rebuild-failed");
            return;
        }
        ItemStack clean = rebuilt.item();
        // Respect MMOItems' own revision transfer settings, then make sure the
        // externally managed enchantments remain on the refreshed item when possible.
        if (plugin.getConfig().getBoolean("item-fixer.preserve-vanilla-enchantments", true)) copyVanillaEnchants(original, clean);
        List<AdvancedEnchantmentsHook.AppliedEnchant> enchants = plugin.advancedEnchantments().readEnchants(original);
        if (!enchants.isEmpty()) {
            if (!plugin.advancedEnchantments().available()) {
                plugin.lang().send(player, "fixer.ae-unavailable");
                return;
            }
            Map<String, Integer> alreadyPresent = plugin.advancedEnchantments().readEnchantLevels(clean);
            for (AdvancedEnchantmentsHook.AppliedEnchant enchant : enchants) {
                if (alreadyPresent.getOrDefault(enchant.id().toLowerCase(java.util.Locale.ROOT), 0) >= enchant.level()) continue;
                ItemStack updated = plugin.advancedEnchantments().apply(enchant.id(), enchant.level(), clean);
                if (updated == null) {
                    plugin.lang().send(player, "fixer.ae-restore-failed", "enchant", enchant.id());
                    return;
                }
                clean = updated;
            }
        }
        inventory.setItem(inputSlot, null);
        Items.giveOrDrop(player, clean);
        rebuilt.extraItems().forEach(extra -> Items.giveOrDrop(player, extra));
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
        ItemStack item = inventory.getItem(inputSlot);
        if (Items.isReal(item)) Items.giveOrDrop(player, item);
        inventory.setItem(inputSlot, null);
    }
}
