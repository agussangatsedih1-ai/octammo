package io.octammo.upgrade;

import io.octammo.core.MmoItems;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** How the item costs of a path are spread over rune slots (one cost may need several slots, by stack size). */
public final class RunePlan {
    /** @param slot GUI slot, or -1 for a GUI-less plan */
    public record Spec(int slot, Cost cost, int required) {}

    private final List<Spec> specs;

    private RunePlan(List<Spec> specs) {
        this.specs = specs;
    }

    public List<Spec> specs() {
        return specs;
    }

    /**
     * @param ring GUI slots in fill order, or null for no limit (GUI-less)
     * @return the plan, or null when the path needs more slots than {@code ring} has
     */
    public static RunePlan of(PathData path, List<Integer> ring) {
        List<Spec> out = new ArrayList<>();
        int next = 0;
        for (Cost c : path.costs()) {
            if (!CostService.isItemCost(c)) continue;
            int total = CostService.amountOf(c);
            int perSlot = stackSize(c);
            while (total > 0) {
                int slot = -1;
                if (ring != null) {
                    if (next >= ring.size()) return null;
                    slot = ring.get(next);
                }
                next++;
                int n = Math.min(perSlot, total);
                out.add(new Spec(slot, c, n));
                total -= n;
            }
        }
        return new RunePlan(out);
    }

    private static int stackSize(Cost c) {
        if (c instanceof Cost.Vanilla v) return Math.max(1, v.material().getMaxStackSize());
        if (c instanceof Cost.MmoItem i) {
            ItemStack s = MmoItems.create(i.ref());
            return s == null ? 64 : Math.max(1, s.getMaxStackSize());
        }
        return 1;
    }
}
