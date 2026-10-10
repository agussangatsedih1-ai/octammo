package io.octammo.upgrade;

import io.octammo.core.ItemRef;

import java.util.List;

/** A loaded upgrades/<name>.yml file ("station") holding one or more paths. */
public record UpgradeFile(String name,
                          String title,
                          String permission,
                          String gui,
                          Integer animationDuration,
                          List<Booster> boosters,
                          List<Protection> protections,
                          List<UpgradeRecipe> recipes) {

    /** Item placed in the booster slot: adds {@code bonus} percentage points, one item used per attempt. */
    public record Booster(String key, ItemRef item, double bonus) {}

    /** Item placed in the protection slot: used up only when it saves an item that would be destroyed. */
    public record Protection(String key, ItemRef item) {}

    /** First path whose input is the given MMOItem, or null. */
    public UpgradeRecipe find(ItemRef ref) {
        if (ref == null) return null;
        for (UpgradeRecipe r : recipes) if (r.input().matches(ref)) return r;
        return null;
    }

    public UpgradeRecipe path(String key) {
        if (key == null) return null;
        for (UpgradeRecipe r : recipes) if (r.key().equalsIgnoreCase(key)) return r;
        return null;
    }

    public Booster boosterFor(ItemRef ref) {
        if (ref == null) return null;
        for (Booster b : boosters) if (b.item().matches(ref)) return b;
        return null;
    }

    public Protection protectionFor(ItemRef ref) {
        if (ref == null) return null;
        for (Protection p : protections) if (p.item().matches(ref)) return p;
        return null;
    }
}
