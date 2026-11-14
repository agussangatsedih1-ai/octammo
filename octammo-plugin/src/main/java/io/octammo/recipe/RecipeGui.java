package io.octammo.recipe;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import io.octammo.gui.OctaGui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Recipe browser and input GUI shared by /octammo combine and /octammo crafting. */
public final class RecipeGui extends OctaGui {
    private static final int[] ENTRY_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
    private static final int[] INPUT_SLOTS = {19, 20, 21, 28, 29, 30};
    private static final int OUTPUT_SLOT = 24;
    private static final int ACTION_SLOT = 40;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private final RecipeManager.Kind kind;
    private final List<RecipeManager.Recipe> recipes;
    private final RecipeManager.Recipe selected;
    private final int page;
    private final Map<Integer, RecipeManager.Recipe> bySlot = new HashMap<>();

    public RecipeGui(OctaMMOPlugin plugin, Player player, RecipeManager.Kind kind) {
        this(plugin, player, kind, null, 0);
    }

    private RecipeGui(OctaMMOPlugin plugin, Player player, RecipeManager.Kind kind, RecipeManager.Recipe selected, int page) {
        super(plugin, player);
        this.kind = kind;
        this.recipes = plugin.recipes().recipes(kind);
        this.selected = selected;
        this.page = Math.max(0, page);
        String title = selected == null
                ? (kind == RecipeManager.Kind.COMBINE ? "&8✦ &d&lCombine Station" : "&8✦ &b&lCrafting Station")
                : (kind == RecipeManager.Kind.COMBINE ? "&8✦ &d&lCombine &8» &f" : "&8✦ &b&lCrafting &8» &f") + selected.name();
        this.inventory = Bukkit.createInventory(this, 54, plugin.lang().render(title, player, null));
        drawFrame();
        if (selected == null) drawMenu(); else drawRecipe();
    }

    private void drawFrame() {
        for (int slot = 0; slot < 54; slot++) {
            boolean edge = slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
            inventory.setItem(slot, Items.make(edge ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, Component.space(), null));
        }
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "&c&l✕ Close", "&7Close this menu."));
    }

    private ItemStack button(Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(plugin.lang().render(line, player, null));
        return Items.make(material, plugin.lang().render(name, player, null), lines);
    }

    private void drawMenu() {
        inventory.setItem(4, button(Material.BOOK, kind == RecipeManager.Kind.COMBINE ? "&d&lCOMBINE RECIPES" : "&b&lCRAFTING RECIPES",
                "&7Select a recipe to view its ingredients.", "&8Page: " + (page + 1) + " | Recipes: " + recipes.size()));
        inventory.setItem(46, button(Material.ARROW, "&e&l« Previous Page"));
        inventory.setItem(52, button(Material.ARROW, "&e&lNext Page »"));
        if (recipes.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, "&cNo recipes configured", "&7Ask an administrator to add recipes."));
            return;
        }
        int index = 0;
        for (RecipeManager.Recipe recipe : recipes.stream().skip((long) page * ENTRY_SLOTS.length).limit(ENTRY_SLOTS.length).toList()) {
            int slot = ENTRY_SLOTS[index++];
            bySlot.put(slot, recipe);
            ItemStack icon = recipe.result();
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(plugin.lang().render("&f&l" + recipe.name(), player, null));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.lang().render("&8&m                                  ", player, null));
            for (String line : recipe.description()) lore.add(plugin.lang().render("&7" + line, player, null));
            lore.add(plugin.lang().render("&7Ingredients: &f" + recipe.ingredients().size(), player, null));
            if (recipe.currencyAmount() > 0) lore.add(plugin.lang().render("&7Cost: &e" + recipe.currencyAmount() + " " + recipe.currencyId(), player, null));
            lore.add(plugin.lang().render("", player, null));
            lore.add(plugin.lang().render("&e➤ Click to select", player, null));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(slot, Items.markVirtual(icon));
        }
    }

    private void drawRecipe() {
        inventory.setItem(4, button(Material.WRITABLE_BOOK, "&e&l" + selected.name(),
                "&7Place each required ingredient", "&7into its matching slot."));
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            int slot = INPUT_SLOTS[i];
            if (i >= selected.ingredients().size()) {
                inventory.setItem(slot, Items.make(Material.GRAY_STAINED_GLASS_PANE, Component.space(), null));
                continue;
            }
            RecipeManager.Ingredient ingredient = selected.ingredients().get(i);
            ItemStack current = inventory.getItem(slot);
            if (Items.isReal(current)) continue;
            ItemStack display = ingredient.sample();
            ItemMeta meta = display.getItemMeta();
            meta.displayName(plugin.lang().render("&e&l" + ingredient.displayName(), player, null));
            meta.lore(List.of(
                    plugin.lang().render("&7Required: &f" + ingredient.amount() + "x", player, null),
                    plugin.lang().render("&7Inserted: &c0&7/&f" + ingredient.amount(), player, null),
                    plugin.lang().render("", player, null),
                    plugin.lang().render("&e➤ Place the ingredient here", player, null)));
            display.setItemMeta(meta);
            inventory.setItem(slot, Items.markVirtual(display));
        }
        ItemStack result = selected.result();
        int resultAmount = result.getAmount();
        result.setAmount(Math.min(resultAmount, result.getMaxStackSize()));
        ItemMeta resultMeta = result.getItemMeta();
        resultMeta.displayName(plugin.lang().render("&a&l✦ RESULT", player, null));
        resultMeta.lore(List.of(plugin.lang().render("&7Output: &f" + resultAmount + "x", player, null)));
        result.setItemMeta(resultMeta);
        inventory.setItem(OUTPUT_SLOT, Items.markVirtual(result));
        if (selected.currencyAmount() > 0) {
            inventory.setItem(38, button(Material.GOLD_INGOT, "&6&lCURRENCY COST", "&7" + selected.currencyAmount() + " " + selected.currencyId()));
        } else {
            inventory.setItem(38, button(Material.LIME_STAINED_GLASS_PANE, "&a&lNO CURRENCY COST"));
        }
        inventory.setItem(ACTION_SLOT, button(kind == RecipeManager.Kind.COMBINE ? Material.ANVIL : Material.SMITHING_TABLE,
                kind == RecipeManager.Kind.COMBINE ? "&d&l✦ COMBINE NOW" : "&b&l✦ CRAFT NOW",
                "&7Click when all ingredients are ready."));
        inventory.setItem(BACK_SLOT, button(Material.ARROW, "&e&l« Back to recipes"));
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
                shiftIngredientFromPlayerInventory(e);
            } else {
                e.setCancelled(false);
            }
            return;
        }
        e.setCancelled(true);
        if (player.getGameMode() == GameMode.CREATIVE) return;
        int slot = e.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (selected == null) {
            if (slot == 46 && page > 0) { switchTo(new RecipeGui(plugin, player, kind, null, page - 1)); return; }
            if (slot == 52 && (long) (page + 1) * ENTRY_SLOTS.length < recipes.size()) { switchTo(new RecipeGui(plugin, player, kind, null, page + 1)); return; }
            RecipeManager.Recipe recipe = bySlot.get(slot);
            if (recipe != null) switchTo(new RecipeGui(plugin, player, kind, recipe, page));
            return;
        }
        if (slot == BACK_SLOT) {
            returnInputs();
            switchTo(new RecipeGui(plugin, player, kind, null, page));
            return;
        }
        if (slot == ACTION_SLOT) {
            craftOrCombine();
            return;
        }
        for (int i = 0; i < Math.min(INPUT_SLOTS.length, selected.ingredients().size()); i++) {
            if (slot == INPUT_SLOTS[i]) {
                swapCursor(slot, e);
                return;
            }
        }
    }


    private void shiftIngredientFromPlayerInventory(InventoryClickEvent e) {
        if (selected == null || player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack source = e.getCurrentItem();
        if (!Items.isReal(source)) return;
        for (int i = 0; i < selected.ingredients().size(); i++) {
            int slot = INPUT_SLOTS[i];
            if (Items.isReal(inventory.getItem(slot))) continue;
            RecipeManager.Ingredient ingredient = selected.ingredients().get(i);
            if (!ingredient.matches(source)) continue;
            ItemStack placed = source.clone();
            placed.setAmount(ingredient.amount());
            inventory.setItem(slot, placed);
            int remaining = source.getAmount() - ingredient.amount();
            if (remaining <= 0) e.setCurrentItem(null);
            else {
                source.setAmount(remaining);
                e.setCurrentItem(source);
            }
            redrawInputHints();
            return;
        }
    }

    private void swapCursor(int slot, InventoryClickEvent e) {
        ItemStack cursor = e.getCursor();
        ItemStack current = inventory.getItem(slot);
        if (Items.isEmpty(cursor)) {
            if (Items.isReal(current)) {
                e.setCursor(current.clone());
                inventory.setItem(slot, null);
            }
        } else if (!Items.isReal(current)) {
            inventory.setItem(slot, cursor.clone());
            e.setCursor(null);
        } else {
            inventory.setItem(slot, cursor.clone());
            e.setCursor(current.clone());
        }
        redrawInputHints();
    }

    private void redrawInputHints() {
        for (int i = 0; i < Math.min(INPUT_SLOTS.length, selected.ingredients().size()); i++) {
            int slot = INPUT_SLOTS[i];
            ItemStack current = inventory.getItem(slot);
            if (Items.isReal(current)) continue;
            RecipeManager.Ingredient ingredient = selected.ingredients().get(i);
            ItemStack display = ingredient.sample();
            ItemMeta meta = display.getItemMeta();
            meta.displayName(plugin.lang().render("&e&l" + ingredient.displayName(), player, null));
            meta.lore(List.of(plugin.lang().render("&7Required: &f" + ingredient.amount() + "x", player, null),
                    plugin.lang().render("&7Inserted: &c0&7/&f" + ingredient.amount(), player, null),
                    plugin.lang().render("&e➤ Place the ingredient here", player, null)));
            display.setItemMeta(meta);
            inventory.setItem(slot, Items.markVirtual(display));
        }
    }

    private void craftOrCombine() {
        for (int i = 0; i < selected.ingredients().size(); i++) {
            ItemStack input = inventory.getItem(INPUT_SLOTS[i]);
            RecipeManager.Ingredient ingredient = selected.ingredients().get(i);
            if (!ingredient.matches(input)) {
                plugin.lang().send(player, "recipe.missing-ingredient", "ingredient", ingredient.displayName(), "amount", String.valueOf(ingredient.amount()));
                return;
            }
        }
        if (selected.currencyAmount() > 0) {
            if (!plugin.excellentEconomy().canWithdraw(player, selected.currencyId(), selected.currencyAmount())) {
                plugin.lang().send(player, "recipe.currency-unavailable", "currency", selected.currencyId(), "amount", String.valueOf(selected.currencyAmount()));
                return;
            }
            if (!plugin.excellentEconomy().withdraw(player, selected.currencyId(), selected.currencyAmount())) {
                plugin.lang().send(player, "recipe.payment-failed");
                return;
            }
        }
        for (int i = 0; i < selected.ingredients().size(); i++) {
            int slot = INPUT_SLOTS[i];
            ItemStack stack = inventory.getItem(slot);
            int remaining = stack.getAmount() - selected.ingredients().get(i).amount();
            if (remaining <= 0) inventory.setItem(slot, null);
            else stack.setAmount(remaining);
        }
        Items.giveOrDrop(player, selected.result());
        plugin.lang().send(player, kind == RecipeManager.Kind.COMBINE ? "recipe.combine-success" : "recipe.craft-success", "recipe", selected.name());
        player.closeInventory();
        // The GUI is intentionally closed after a successful transaction; inputs/results cannot be duplicated by a stale view.
    }

    private void returnInputs() {
        for (int slot : INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (Items.isReal(item)) Items.giveOrDrop(player, item);
            inventory.setItem(slot, null);
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        if (transition || selected == null) return;
        returnInputs();
    }
}
