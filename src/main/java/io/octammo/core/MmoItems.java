package io.octammo.core;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Thin wrapper around the MMOItems / MythicLib API. Every direct API call lives here. */
public final class MmoItems {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern COLOR = Pattern.compile("(?i)[&\u00a7]#[0-9a-f]{6}|[&\u00a7][0-9a-fk-orx]");
    private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap<>();

    private MmoItems() {}

    public static void clearCache() {
        NAME_CACHE.clear();
    }

    /** @return the TYPE/ID of an MMOItem, or null when the stack is not an MMOItem. */
    public static ItemRef identify(ItemStack stack) {
        if (Items.isEmpty(stack)) return null;
        try {
            NBTItem nbt = NBTItem.get(stack);
            if (!nbt.hasTag("MMOITEMS_ITEM_TYPE") || !nbt.hasTag("MMOITEMS_ITEM_ID")) return null;
            String type = nbt.getString("MMOITEMS_ITEM_TYPE");
            String id = nbt.getString("MMOITEMS_ITEM_ID");
            if (type == null || type.isEmpty() || id == null || id.isEmpty()) return null;
            return new ItemRef(type, id, stack.getAmount());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Builds a fresh item of amount 1, or null if the template does not exist. */
    public static ItemStack create(ItemRef ref) {
        try {
            Type type = Type.get(ref.type());
            if (type == null) return null;
            ItemStack stack = MMOItems.plugin.getItem(type, ref.id());
            if (Items.isEmpty(stack)) return null;
            stack = stack.clone();
            stack.setAmount(1);
            return stack;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Plain display name (no colours) of the template, falling back to the id. */
    public static String displayName(ItemRef ref) {
        return NAME_CACHE.computeIfAbsent(ref.key(), k -> {
            ItemStack s = create(ref);
            if (s != null && s.hasItemMeta()) {
                Component c = s.getItemMeta().displayName();
                if (c != null) return PLAIN.serialize(c);
            }
            return ref.id();
        });
    }

    /** Lore lines as plain text, colour codes removed. */
    public static List<String> plainLore(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return List.of();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) return List.of();
        List<String> out = new ArrayList<>(lore.size());
        for (Component c : lore) out.add(PLAIN.serialize(c));
        return out;
    }

    /** Strips &-codes and hex codes, lower-cases and trims (for config text comparison). */
    public static String normalize(String s) {
        return COLOR.matcher(s == null ? "" : s).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }

    /** Gives {@code amount} copies, splitting by max stack size and dropping what does not fit. */
    public static void give(Player player, ItemStack single, int amount) {
        int max = Math.max(1, single.getMaxStackSize());
        while (amount > 0) {
            int n = Math.min(max, amount);
            ItemStack copy = single.clone();
            copy.setAmount(n);
            player.getInventory().addItem(copy).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            amount -= n;
        }
    }
}
