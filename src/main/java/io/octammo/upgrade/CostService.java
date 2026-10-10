package io.octammo.upgrade;

import io.octammo.OctaMMO;
import io.octammo.core.MmoItems;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Money and XP levels are charged from the player; item costs are NOT taken from the
 * inventory any more, the player puts them into the rune slots of the GUI.
 */
public final class CostService {
    private final OctaMMO plugin;

    public CostService(OctaMMO plugin) {
        this.plugin = plugin;
    }

    public static boolean isItemCost(Cost c) {
        return c instanceof Cost.MmoItem || c instanceof Cost.Vanilla;
    }

    public static int amountOf(Cost c) {
        return switch (c) {
            case Cost.MmoItem i -> i.ref().amount();
            case Cost.Vanilla v -> v.amount();
            default -> 0;
        };
    }

    /** Does this stack count as the item cost? (plain vanilla item: no meta, not an MMOItem) */
    public static boolean matches(Cost cost, ItemStack s) {
        if (s == null) return false;
        return switch (cost) {
            case Cost.MmoItem i -> i.ref().matches(MmoItems.identify(s));
            case Cost.Vanilla v -> s.getType() == v.material() && !s.hasItemMeta() && MmoItems.identify(s) == null;
            default -> false;
        };
    }

    /** Money / levels check. Item costs always return true here (they live in the rune slots). */
    public boolean hasNonItem(Player p, Cost cost) {
        return switch (cost) {
            case Cost.Money m -> m.amount() <= 0 || (plugin.economyAvailable() && plugin.economy().has(p, m.amount()));
            case Cost.Levels l -> p.getLevel() >= l.amount();
            default -> true;
        };
    }

    /** Takes money (the only step that can fail) and then levels. */
    public boolean consumeNonItems(Player p, List<Cost> costs) {
        for (Cost c : costs) {
            if (c instanceof Cost.Money m && m.amount() > 0 && (!plugin.economyAvailable() || !plugin.economy().withdraw(p, m.amount()))) {
                return false;
            }
        }
        for (Cost c : costs) {
            if (c instanceof Cost.Levels l) p.setLevel(Math.max(0, p.getLevel() - l.amount()));
        }
        return true;
    }

    /** Short plain text, e.g. "5000", "3x Corrupted Shard". */
    public String describe(Cost cost) {
        return switch (cost) {
            case Cost.Money m -> plugin.lang().format("cost.money", "amount",
                    plugin.economyAvailable() ? plugin.economy().format(m.amount()) : String.valueOf(m.amount()));
            case Cost.Levels l -> plugin.lang().format("cost.levels", "amount", String.valueOf(l.amount()));
            case Cost.MmoItem i -> plugin.lang().format("cost.item", "amount", String.valueOf(i.ref().amount()),
                    "name", MmoItems.displayName(i.ref()));
            case Cost.Vanilla v -> plugin.lang().format("cost.item", "amount", String.valueOf(v.amount()),
                    "name", pretty(v.material().name()));
        };
    }

    /** Name of the item a cost asks for (no amount). */
    public String nameOf(Cost cost) {
        return switch (cost) {
            case Cost.MmoItem i -> MmoItems.displayName(i.ref());
            case Cost.Vanilla v -> pretty(v.material().name());
            default -> "";
        };
    }

    private static String pretty(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String w : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

}
