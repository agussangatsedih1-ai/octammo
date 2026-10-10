package io.octammo.core;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Predicate;

/** Counting / taking items from the main storage (slots 0-35) of a player. */
public final class Inv {
    private Inv() {}

    public static int count(Player p, Predicate<ItemStack> match) {
        int total = 0;
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < Math.min(36, c.length); i++) {
            if (!Items.isEmpty(c[i]) && match.test(c[i])) total += c[i].getAmount();
        }
        return total;
    }

    public static void take(Player p, Predicate<ItemStack> match, int amount) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < Math.min(36, c.length) && amount > 0; i++) {
            ItemStack s = c[i];
            if (Items.isEmpty(s) || !match.test(s)) continue;
            int n = Math.min(amount, s.getAmount());
            amount -= n;
            if (n >= s.getAmount()) p.getInventory().setItem(i, null);
            else s.setAmount(s.getAmount() - n);
        }
    }
}
