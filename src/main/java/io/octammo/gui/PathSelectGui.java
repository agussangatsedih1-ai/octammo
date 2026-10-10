package io.octammo.gui;

import io.octammo.OctaMMO;
import io.octammo.core.Items;
import io.octammo.core.Lang;
import io.octammo.core.MmoItems;
import io.octammo.upgrade.Cost;
import io.octammo.upgrade.UpgradeFile;
import io.octammo.upgrade.UpgradeRecipe;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Menu listing the paths of a station. Clicking a path opens its Altar Forge. */
public final class PathSelectGui extends OctaGui {
    public static final Set<String> SINGLES = Set.of("info", "close");
    public static final Set<String> LISTS = Set.of("paths");

    private final UpgradeFile file;
    private final GuiLayout layout;
    private final Map<Integer, UpgradeRecipe> bySlot = new HashMap<>();
    private final int closeSlot;

    public PathSelectGui(OctaMMO plugin, UpgradeFile file, Player player) {
        super(plugin, player);
        this.file = file;
        this.layout = plugin.guis().pathSelect();
        this.closeSlot = layout.slot("close");

        Component title = Lang.color(plugin.lang().expand(layout.title(), player, Map.of("station", file.title())));
        this.inventory = Bukkit.createInventory(this, 54, title);

        Set<Integer> functional = layout.functionalSlots();
        for (int i = 0; i < 54; i++) {
            if (functional.contains(i)) continue;
            boolean edge = i < 9 || i % 9 == 0 || i % 9 == 8;
            inventory.setItem(i, Items.make(edge ? layout.border() : layout.filler(), Component.space(), null));
        }
        Map<String, String> ph = Map.of("station", file.title());
        inventory.setItem(layout.slot("info"), layout.item("info", ItemDef.of(Material.ENCHANTING_TABLE, "{station}", "{gui.path.info}")).build(plugin, player, ph));
        inventory.setItem(closeSlot, layout.item("close", ItemDef.of(Material.BARRIER, "{gui.close}")).build(plugin, player, ph));

        List<Integer> slots = layout.slots("paths");
        List<UpgradeRecipe> recipes = file.recipes();
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            if (i < recipes.size()) {
                bySlot.put(slot, recipes.get(i));
                inventory.setItem(slot, entry(recipes.get(i)));
            } else {
                inventory.setItem(slot, Items.make(layout.filler(), Component.space(), null));
            }
        }
        if (recipes.size() > slots.size()) {
            plugin.getLogger().warning("Station '" + file.name() + "' has " + recipes.size() + " paths but guis/path-select.yml only shows "
                    + slots.size() + ".");
        }
    }

    private ItemStack entry(UpgradeRecipe r) {
        ItemStack base = MmoItems.create(r.output());
        if (base == null) base = new ItemStack(Material.BARRIER);
        List<String> costs = new ArrayList<>();
        for (Cost c : r.costs()) costs.add("&7- &f" + plugin.costService().describe(c));
        Map<String, String> ph = new HashMap<>();
        ph.put("input", MmoItems.displayName(r.input()));
        ph.put("output", MmoItems.displayName(r.output()));
        ph.put("chance", ForgeGui.fmt(r.chance()));
        ph.put("fail", plugin.lang().format(r.onFail() == UpgradeRecipe.FailMode.DESTROY ? "fail.destroy" : "fail.keep"));
        ph.put("costs", costs.isEmpty() ? plugin.lang().format("gui.costs.none") : String.join("\n", costs));
        ph.put("description", String.join("\n", r.description()));

        ItemMeta meta = base.getItemMeta();
        meta.displayName(plugin.lang().render(r.name(), player, ph));
        meta.lore(plugin.lang().renderLines(List.of("{gui.path.entry-lore}"), player, ph));
        base.setItemMeta(meta);
        base.setAmount(1);
        return Items.markVirtual(base);
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        e.setCancelled(true);   // nothing can be placed in or taken from this menu
        if (player.getGameMode() == GameMode.CREATIVE || e.getClickedInventory() != inventory) return;
        int slot = e.getRawSlot();
        if (slot == closeSlot) {
            player.closeInventory();
            return;
        }
        UpgradeRecipe recipe = bySlot.get(slot);
        if (recipe == null) return;
        ForgeGui forge = ForgeGui.create(plugin, file, recipe, player);
        if (forge == null) {
            plugin.lang().send(player, "upgrade.too-many-runes", "path", recipe.key());
            return;
        }
        switchTo(forge);
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        // nothing to return
    }
}
