package io.octammo.recipe;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.ItemRef;
import io.octammo.core.Items;
import io.octammo.core.MmoItems;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads recipe-driven Combine and Crafting Station definitions. */
public final class RecipeManager {
    public enum Kind { COMBINE, CRAFT }

    public record Ingredient(String type, Material material, String itemType, String itemId, int amount) {
        public boolean matches(ItemStack stack) {
            if (!Items.isReal(stack) || stack.getAmount() < amount) return false;
            if (type.equals("MMOITEMS")) {
                ItemRef ref = MmoItems.identify(stack);
                return ref != null && ref.type().equalsIgnoreCase(itemType) && ref.id().equalsIgnoreCase(itemId);
            }
            return stack.getType() == material;
        }

        public ItemStack sample() {
            if (type.equals("MMOITEMS")) {
                ItemStack item = MmoItems.create(new ItemRef(itemType, itemId, 1));
                return item == null ? new ItemStack(Material.BARRIER) : item;
            }
            return new ItemStack(material == null ? Material.BARRIER : material);
        }

        public String displayName() {
            if (type.equals("MMOITEMS")) return MmoItems.displayName(new ItemRef(itemType, itemId, 1));
            return material == null ? "Unknown item" : material.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        }
    }

    public record Recipe(Kind kind, String id, String name, List<String> description,
                         List<Ingredient> ingredients, ItemStack result,
                         String currencyId, double currencyAmount) {
        public Recipe {
            description = List.copyOf(description);
            ingredients = List.copyOf(ingredients);
            result = result.clone();
        }
        public ItemStack result() { return result.clone(); }
    }

    private final OctaMMOPlugin plugin;
    private final Map<String, Recipe> combine = new LinkedHashMap<>();
    private final Map<String, Recipe> craft = new LinkedHashMap<>();

    public RecipeManager(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveDefaults() {
        File dir = new File(plugin.getDataFolder(), "recipes");
        if (!dir.exists()) dir.mkdirs();
        saveIfMissing("recipes/combine.yml");
        saveIfMissing("recipes/crafting.yml");
    }

    private void saveIfMissing(String path) {
        File target = new File(plugin.getDataFolder(), path);
        if (!target.exists()) plugin.saveResource(path, false);
    }

    public void reload() {
        combine.clear();
        craft.clear();
        load(new File(plugin.getDataFolder(), "recipes/combine.yml"), Kind.COMBINE, combine);
        load(new File(plugin.getDataFolder(), "recipes/crafting.yml"), Kind.CRAFT, craft);
    }

    public List<Recipe> recipes(Kind kind) {
        return List.copyOf((kind == Kind.COMBINE ? combine : craft).values());
    }

    private void load(File file, Kind kind, Map<String, Recipe> target) {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("recipes");
        if (section == null) {
            plugin.getLogger().warning(file.getName() + ": missing recipes section.");
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection def = section.getConfigurationSection(id);
            if (def == null) continue;
            try {
                Recipe recipe = parse(def, kind, id);
                if (recipe != null) target.put(id.toLowerCase(Locale.ROOT), recipe);
            } catch (Exception ex) {
                plugin.getLogger().warning("Invalid recipe " + id + " in " + file.getName() + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + target.size() + " " + kind.name().toLowerCase(Locale.ROOT) + " recipe(s).");
    }

    private Recipe parse(ConfigurationSection section, Kind kind, String id) {
        String name = section.getString("name", id);
        List<String> description = section.getStringList("description");
        List<Ingredient> ingredients = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("ingredients")) {
            String type = String.valueOf(raw.containsKey("type") ? raw.get("type") : "MATERIAL").toUpperCase(Locale.ROOT);
            int amount = Math.max(1, integer(raw.get("amount"), 1));
            if (type.equals("MMOITEMS")) {
                String itemType = String.valueOf(raw.containsKey("item-type") ? raw.get("item-type") : "").toUpperCase(Locale.ROOT);
                String itemId = String.valueOf(raw.containsKey("item-id") ? raw.get("item-id") : "").toUpperCase(Locale.ROOT);
                if (itemType.isBlank() || itemId.isBlank()) throw new IllegalArgumentException("MMOITEMS ingredient needs item-type and item-id");
                ingredients.add(new Ingredient("MMOITEMS", null, itemType, itemId, amount));
            } else if (type.equals("MATERIAL")) {
                String materialName = String.valueOf(raw.containsKey("material") ? raw.get("material") : "").toUpperCase(Locale.ROOT);
                Material material = Material.matchMaterial(materialName);
                if (material == null || material.isAir()) throw new IllegalArgumentException("unknown ingredient material: " + materialName);
                ingredients.add(new Ingredient("MATERIAL", material, "", "", amount));
            } else {
                throw new IllegalArgumentException("unknown ingredient type: " + type);
            }
        }
        if (ingredients.isEmpty()) throw new IllegalArgumentException("at least one ingredient is required");
        if (ingredients.size() > 6) throw new IllegalArgumentException("a recipe may have at most six ingredient slots");

        ConfigurationSection resultSection = section.getConfigurationSection("result");
        if (resultSection == null) throw new IllegalArgumentException("missing result section");
        String resultType = resultSection.getString("type", "MMOITEMS").toUpperCase(Locale.ROOT);
        int resultAmount = Math.max(1, resultSection.getInt("amount", 1));
        ItemStack result;
        if (resultType.equals("MMOITEMS")) {
            String type = resultSection.getString("item-type", "").toUpperCase(Locale.ROOT);
            String itemId = resultSection.getString("item-id", "").toUpperCase(Locale.ROOT);
            if (type.isBlank() || itemId.isBlank()) throw new IllegalArgumentException("MMOItems result needs item-type and item-id");
            result = MmoItems.create(new ItemRef(type, itemId, resultAmount));
            if (result == null) throw new IllegalArgumentException("MMOItems result does not exist: " + type + ":" + itemId);
        } else if (resultType.equals("MATERIAL")) {
            Material material = Material.matchMaterial(resultSection.getString("material", ""));
            if (material == null || material.isAir()) throw new IllegalArgumentException("unknown result material");
            result = new ItemStack(material, resultAmount);
        } else {
            throw new IllegalArgumentException("unknown result type: " + resultType);
        }
        result.setAmount(resultAmount);

        String currencyId = "";
        double currencyAmount = 0;
        ConfigurationSection currency = section.getConfigurationSection("currency");
        if (kind == Kind.CRAFT && currency != null) {
            currencyId = currency.getString("id", "").trim();
            currencyAmount = Math.max(0, currency.getDouble("amount", 0));
            if (currencyAmount > 0 && currencyId.isBlank()) throw new IllegalArgumentException("currency.id is required when currency.amount is above zero");
        }
        return new Recipe(kind, id, name, description, ingredients, result, currencyId, currencyAmount);
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
