package io.octammo.upgrade;

import io.octammo.core.ItemRef;
import org.bukkit.Material;

/** One requirement of an upgrade recipe. */
public sealed interface Cost {
    record Money(double amount) implements Cost {}
    record Levels(int amount) implements Cost {}
    record MmoItem(ItemRef ref) implements Cost {}
    record Vanilla(Material material, int amount) implements Cost {}
}
