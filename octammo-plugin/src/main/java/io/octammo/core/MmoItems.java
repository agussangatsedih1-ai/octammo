package io.octammo.core;

import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/** Thin wrapper around the MMOItems / MythicLib API. Every direct API call lives here. */
public final class MmoItems {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern COLOR = Pattern.compile("(?i)[&\u00a7]#[0-9a-f]{6}|[&\u00a7][0-9a-fk-orx]");
    private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap<>();

    private MmoItems() {}

    /** Result of MMOItems' native revision rebuild, including gemstones returned by its reforge options. */
    public record RebuildResult(ItemStack item, List<ItemStack> extraItems) {
        public RebuildResult {
            item = item.clone();
            extraItems = extraItems.stream().filter(Items::isReal).map(ItemStack::clone).toList();
        }
        @Override public ItemStack item() { return item.clone(); }
    }

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


    /**
     * Rebuilds an existing MMOItem with MMOItems' own Revision/Reforger pipeline.
     * This is preferable to replacing it with a pristine template because MMOItems
     * transfers player data (gems, upgrades, tier, enchants, etc.) according to the
     * server's item-revision.keep-data configuration.
     *
     * @return the refreshed item and any extra items returned by MMOItems, or null if
     * the installed MMOItems build does not expose a compatible revision API.
     */
    public static RebuildResult rebuild(ItemStack original) {
        if (Items.isEmpty(original) || identify(original) == null) return null;
        try {
            ClassLoader loader = MMOItems.class.getClassLoader();
            Class<?> reforgerType = Class.forName("net.Indyuce.mmoitems.api.util.MMOItemReforger", true, loader);
            Constructor<?> constructor = reforgerType.getConstructor(ItemStack.class);
            Object reforger = constructor.newInstance(original.clone());

            Object canReforge = reforgerType.getMethod("canReforge").invoke(reforger);
            if (!Boolean.TRUE.equals(canReforge)) return null;

            Object options = createLoreRepairOptions(loader);
            if (options == null) return null;

            Method reforge = null;
            for (Method candidate : reforgerType.getMethods()) {
                if (candidate.getName().equals("reforge") && candidate.getParameterCount() == 1
                        && candidate.getParameterTypes()[0].isInstance(options)) {
                    reforge = candidate;
                    break;
                }
            }
            if (reforge == null || !Boolean.TRUE.equals(reforge.invoke(reforger, options))) return null;

            Object rawResult = reforgerType.getMethod("getResult").invoke(reforger);
            if (!(rawResult instanceof ItemStack result) || Items.isEmpty(result)) return null;

            List<ItemStack> extraItems = new ArrayList<>();
            try {
                Object rawExtras = reforgerType.getMethod("getReforgingOutput").invoke(reforger);
                if (rawExtras instanceof Collection<?> collection) {
                    for (Object extra : collection) if (extra instanceof ItemStack stack && Items.isReal(stack)) extraItems.add(stack.clone());
                }
            } catch (ReflectiveOperationException ignored) {
                // Older compatible builds may not expose returned-gem output.
            }
            result.setAmount(original.getAmount());
            return new RebuildResult(result, extraItems);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            MMOItems.plugin.getLogger().warning("MMOItems item rebuild was cancelled: " + cause.getMessage());
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            MMOItems.plugin.getLogger().warning("MMOItems item rebuild is unavailable: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Creates MMOItems' native ReforgeOptions from the server's keep-data settings,
     * but deliberately disables old-lore transfer. The rebuilt item therefore receives
     * current template lore while supported gameplay data remains governed by MMOItems.
     */
    private static Object createLoreRepairOptions(ClassLoader loader) throws ReflectiveOperationException {
        Object pluginInstance = MMOItems.plugin;
        Object fileConfig = pluginInstance.getClass().getMethod("getConfig").invoke(pluginInstance);
        Object rawSection = fileConfig.getClass()
                .getMethod("getConfigurationSection", String.class)
                .invoke(fileConfig, "item-revision.keep-data");

        YamlConfiguration optionsConfig = new YamlConfiguration();
        // Mirror MMOItems' normal ReforgeOptions defaults when the server has a
        // partially configured keep-data section, then apply explicit server values.
        optionsConfig.set("display-name", false);
        optionsConfig.set("lore", false);
        optionsConfig.set("enchantments", true);
        optionsConfig.set("upgrades", true);
        optionsConfig.set("gemstones", true);
        optionsConfig.set("skins", true);
        optionsConfig.set("soulbound", true);
        optionsConfig.set("kept-lore-prefix", "§7");
        optionsConfig.set("external-sh", true);
        optionsConfig.set("modifications", true);
        optionsConfig.set("reroll", false);
        optionsConfig.set("advanced-enchantments", true);
        optionsConfig.set("tier", true);
        if (rawSection instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                optionsConfig.set(key, section.get(key));
            }
        }
        // This is the point of the fixer: never transfer the corrupted lore.
        optionsConfig.set("lore", false);

        Class<?> optionsType = Class.forName("net.Indyuce.mmoitems.api.ReforgeOptions", true, loader);
        Constructor<?> optionsConstructor = optionsType.getConstructor(ConfigurationSection.class);
        return optionsConstructor.newInstance(optionsConfig);
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


    /**
     * Best-effort copy of data from the old item to the new one. Raw NBT keys are copied as text, so
     * lore and stats are NOT recomputed (gems / upgrade level may look wrong): this is why every option defaults to off.
     */
    public static ItemStack preserve(ItemStack oldItem, ItemStack fresh, Collection<String> nbtKeys, boolean durability) {
        ItemStack out = fresh;
        try {
            if (!nbtKeys.isEmpty()) {
                NBTItem src = NBTItem.get(oldItem);
                List<ItemTag> tags = new ArrayList<>();
                for (String key : nbtKeys) if (src.hasTag(key)) tags.add(new ItemTag(key, src.getString(key)));
                if (!tags.isEmpty()) out = NBTItem.get(out).addTag(tags).toItem();
            }
        } catch (Throwable t) {
            // keep the item as it is
        }
        if (durability && oldItem.getItemMeta() instanceof Damageable oldDmg && out.getItemMeta() instanceof Damageable newDmg) {
            newDmg.setDamage(oldDmg.getDamage());
            out.setItemMeta(newDmg);
        }
        return out;
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
