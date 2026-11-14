package io.octammo.hook;

import io.octammo.core.ItemRef;
import io.octammo.core.MmoItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Optional, reflection-based bridge to the documented AdvancedEnchantments API. */
public final class AdvancedEnchantmentsHook {
    public record EnchantDefinition(String id, String displayName, String group, int maxLevel,
                                    String displayPattern, List<String> applies) {
        public EnchantDefinition {
            applies = List.copyOf(applies);
        }
    }
    public record AppliedEnchant(String id, int level) {}

    private static final Pattern COLOR = Pattern.compile("(?i)§[0-9A-FK-ORX]|&[0-9A-FK-ORX]|&#[0-9A-F]{6}");
    private static final Pattern PLACEHOLDER = Pattern.compile("%[^%]+%");
    private static final Pattern NUMBER = Pattern.compile("(?i)(?:^|\\s)([1-9][0-9]{0,2}|[IVXLCDM]{1,8})\\s*$");

    private final Plugin owner;
    private final Map<String, EnchantDefinition> definitions = new LinkedHashMap<>();
    private Object apiClass;
    private boolean available;

    public AdvancedEnchantmentsHook(Plugin owner) {
        this.owner = owner;
    }

    public void reload() {
        definitions.clear();
        available = Bukkit.getPluginManager().isPluginEnabled("AdvancedEnchantments");
        apiClass = null;
        if (!available) return;
        try {
            apiClass = Class.forName("net.advancedplugins.ae.api.AEAPI");
        } catch (ClassNotFoundException ex) {
            owner.getLogger().warning("AdvancedEnchantments is enabled, but AEAPI was not found. Custom-enchant actions are unavailable.");
            available = false;
            return;
        }
        Plugin ae = Bukkit.getPluginManager().getPlugin("AdvancedEnchantments");
        if (ae == null) return;

        List<File> roots = new ArrayList<>();
        for (String name : List.of("Enchantments", "enchantments", "enchants")) {
            File folder = new File(ae.getDataFolder(), name);
            if (folder.isDirectory()) roots.add(folder);
        }
        File legacyFile = new File(ae.getDataFolder(), "enchantments.yml");
        if (legacyFile.isFile()) roots.add(legacyFile);

        int scanned = 0;
        for (File root : roots) {
            if (root.isFile()) {
                loadFile(root);
                scanned++;
                continue;
            }
            try (Stream<java.nio.file.Path> paths = Files.walk(root.toPath())) {
                for (File file : paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                        .map(java.nio.file.Path::toFile).toList()) {
                    loadFile(file);
                    scanned++;
                }
            } catch (IOException ex) {
                owner.getLogger().warning("Could not scan AdvancedEnchantments folder " + root.getName() + ": " + ex.getMessage());
            }
        }
        owner.getLogger().info("Loaded " + definitions.size() + " AdvancedEnchantments definition(s) from " + scanned + " file(s).");
        if (definitions.isEmpty()) {
            owner.getLogger().warning("No AdvancedEnchantments definitions were found. Check the Enchantments/<folder>/enchantments.yml layout and the configured AE folder.");
        }
    }

    private void loadFile(File file) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (looksLikeDefinition(yaml)) {
                String filename = file.getName();
                String id = filename.substring(0, filename.length() - 4).toLowerCase(Locale.ROOT);
                loadDefinition(id, yaml);
                return;
            }
            for (String id : yaml.getKeys(false)) {
                ConfigurationSection section = yaml.getConfigurationSection(id);
                if (section != null && looksLikeDefinition(section)) loadDefinition(id, section);
            }
        } catch (Exception ex) {
            owner.getLogger().warning("Could not read AdvancedEnchantments definitions in " + file.getPath() + ": " + ex.getMessage());
        }
    }

    private boolean looksLikeDefinition(ConfigurationSection section) {
        return (section.isConfigurationSection("levels") || section.contains("max-level"))
                && (section.contains("group") || section.contains("display") || section.contains("type"));
    }

    private void loadDefinition(String rawId, ConfigurationSection cfg) {
        if (!cfg.getBoolean("enabled", true)) return;
        if (cfg.getConfigurationSection("settings") != null
                && cfg.getConfigurationSection("settings").getBoolean("disable-in-enchanter", false)) return;
        String id = rawId.toLowerCase(Locale.ROOT);
        String display = cfg.getString("display", cfg.getString("name", id));
        display = PLACEHOLDER.matcher(display == null ? id : display).replaceAll("").trim();
        if (display.isBlank()) display = id;
        String group = cfg.getString("group", "SIMPLE").toUpperCase(Locale.ROOT);
        int maxLevel = maximumLevel(cfg);
        List<String> applies = cfg.getStringList("applies").stream()
                .map(value -> value.toUpperCase(Locale.ROOT).trim())
                .filter(value -> !value.isBlank())
                .toList();
        definitions.put(id, new EnchantDefinition(id, display, group, maxLevel, cfg.getString("display", display), applies));
    }

    private int maximumLevel(ConfigurationSection cfg) {
        ConfigurationSection levels = cfg.getConfigurationSection("levels");
        int maximum = 0;
        if (levels != null) {
            for (String key : levels.getKeys(false)) {
                try { maximum = Math.max(maximum, Integer.parseInt(key)); }
                catch (NumberFormatException ignored) {}
            }
        }
        return Math.max(1, maximum > 0 ? maximum : cfg.getInt("max-level", 1));
    }

    public boolean available() { return available && apiClass instanceof Class<?>; }
    public boolean hasDefinitions() { return !definitions.isEmpty(); }

    public List<EnchantDefinition> definitions(String group, ItemStack item) {
        String wanted = group == null ? "" : group.toUpperCase(Locale.ROOT);
        return definitions.values().stream()
                .filter(def -> def.group().equalsIgnoreCase(wanted))
                .filter(def -> canApply(def, item))
                .sorted(Comparator.comparing(EnchantDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<EnchantDefinition> definition(String id) {
        return Optional.ofNullable(definitions.get(id == null ? "" : id.toLowerCase(Locale.ROOT)));
    }

    public boolean canApply(String id, ItemStack item) {
        EnchantDefinition definition = definitions.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        return definition != null && canApply(definition, item);
    }

    private boolean canApply(EnchantDefinition definition, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (definition.applies().isEmpty()) return false;
        String material = item.getType().name();
        ItemRef ref = null;
        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")
                && Bukkit.getPluginManager().isPluginEnabled("MythicLib")) {
            try { ref = MmoItems.identify(item); }
            catch (LinkageError ignored) { /* Vanilla enchant browsing must remain usable without MMOItems. */ }
        }
        String itemType = ref == null ? "" : ref.type().toUpperCase(Locale.ROOT);
        for (String raw : definition.applies()) {
            String rule = raw.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            if (rule.equals("ALL") || rule.equals("ALL_ITEMS") || rule.equals(material) || rule.equals(itemType)) return true;
            if (rule.startsWith("ALL_")) {
                String category = rule.substring(4);
                if (matchesCategory(category, material, itemType)) return true;
            } else if (rule.endsWith("_ARMOR")) {
                String armor = rule.substring(0, rule.length() - "_ARMOR".length());
                if (matchesArmorMaterial(armor, material)) return true;
            } else if (matchesCategory(rule, material, itemType)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCategory(String category, String material, String itemType) {
        if (category.equals("ARMOR")) return isArmor(material) || itemType.equals("ARMOR");
        if (category.equals("WEAPON")) return isWeapon(material) || isWeapon(itemType);
        if (category.equals("HELMET")) return isHelmet(material) || itemType.equals("HELMET");
        if (category.equals("CHESTPLATE")) return isChestplate(material) || itemType.equals("CHESTPLATE");
        if (category.equals("LEGGINGS")) return isLeggings(material) || itemType.equals("LEGGINGS");
        if (category.equals("BOOTS")) return isBoots(material) || itemType.equals("BOOTS");
        if (category.equals("SWORD")) return material.endsWith("_SWORD") || itemType.equals("SWORD");
        if (category.equals("AXE")) return material.endsWith("_AXE") || itemType.equals("AXE");
        if (category.equals("PICKAXE")) return material.endsWith("_PICKAXE") || itemType.equals("PICKAXE");
        if (category.equals("SHOVEL")) return material.endsWith("_SHOVEL") || material.endsWith("_SPADE") || itemType.equals("SHOVEL");
        if (category.equals("HOE")) return material.endsWith("_HOE") || itemType.equals("HOE");
        if (category.equals("BOW")) return material.equals("BOW") || itemType.equals("BOW");
        if (category.equals("CROSSBOW")) return material.equals("CROSSBOW") || itemType.equals("CROSSBOW");
        if (category.equals("TRIDENT")) return material.equals("TRIDENT") || itemType.equals("TRIDENT");
        if (category.equals("FISHING_ROD")) return material.equals("FISHING_ROD") || itemType.equals("FISHING_ROD");
        return material.equals(category) || itemType.equals(category);
    }

    private boolean matchesArmorMaterial(String armor, String material) {
        String prefix = switch (armor) {
            case "GOLD" -> "GOLDEN_";
            case "CHAIN" -> "CHAINMAIL_";
            default -> armor + "_";
        };
        return isArmor(material) && material.startsWith(prefix);
    }

    private boolean isArmor(String material) {
        return isHelmet(material) || isChestplate(material) || isLeggings(material) || isBoots(material);
    }
    private boolean isHelmet(String material) { return material.endsWith("_HELMET") || material.equals("TURTLE_HELMET") || material.equals("CARVED_PUMPKIN") || material.equals("PLAYER_HEAD"); }
    private boolean isChestplate(String material) { return material.endsWith("_CHESTPLATE") || material.equals("ELYTRA"); }
    private boolean isLeggings(String material) { return material.endsWith("_LEGGINGS"); }
    private boolean isBoots(String material) { return material.endsWith("_BOOTS"); }
    private boolean isWeapon(String value) { return value.endsWith("_SWORD") || value.endsWith("_AXE") || value.equals("SWORD") || value.equals("AXE") || value.equals("DAGGER") || value.equals("SPEAR") || value.equals("TRIDENT"); }

    /** Uses AEAPI.applyEnchant(String, int, ItemStack), which returns the modified stack. */
    public ItemStack apply(String id, int level, ItemStack input) {
        if (!available() || input == null) return null;
        EnchantDefinition def = definitions.get(id.toLowerCase(Locale.ROOT));
        if (def == null || !canApply(def, input)) return null;
        try {
            Method method = ((Class<?>) apiClass).getMethod("applyEnchant", String.class, int.class, ItemStack.class);
            Object result = method.invoke(null, def.id(), Math.max(1, Math.min(level, def.maxLevel())), input.clone());
            return result instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException ex) {
            owner.getLogger().warning("Could not apply AdvancedEnchantments enchant '" + id + "': " + ex.getMessage());
            return null;
        }
    }

    /**
     * Reads custom enchant data through an ItemStack-based AEAPI getter when available.
     * A definition/lore fallback is used for AE builds which do not expose a public map getter.
     */
    public List<AppliedEnchant> readEnchants(ItemStack item) {
        if (!available() || item == null) return List.of();
        List<AppliedEnchant> result = readViaApi(item);
        if (!result.isEmpty()) return result;
        return readViaLore(item);
    }

    private List<AppliedEnchant> readViaApi(ItemStack item) {
        Class<?> type = (Class<?>) apiClass;
        for (Method method : type.getMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.contains("enchant") || name.contains("apply") || !Modifier.isStatic(method.getModifiers())) continue;
            if (method.getParameterCount() != 1 || !ItemStack.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
            try {
                Object value = method.invoke(null, item.clone());
                List<AppliedEnchant> parsed = parseEnchantContainer(value);
                if (!parsed.isEmpty()) return parsed;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try another public getter, if the installed AE version exposes one.
            }
        }
        return List.of();
    }

    private List<AppliedEnchant> parseEnchantContainer(Object value) {
        if (!(value instanceof Map<?, ?> map)) return List.of();
        List<AppliedEnchant> out = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String id = objectId(entry.getKey());
            int level = objectLevel(entry.getValue());
            if (id == null || level < 1) continue;
            EnchantDefinition def = definitions.get(id.toLowerCase(Locale.ROOT));
            if (def != null) out.add(new AppliedEnchant(def.id(), Math.min(level, def.maxLevel())));
        }
        return out;
    }

    private String objectId(Object object) {
        if (object == null) return null;
        if (object instanceof String text) return text;
        for (String methodName : List.of("getName", "getId", "getKey", "name")) {
            try {
                Object value = object.getClass().getMethod(methodName).invoke(object);
                if (value != null) return value.toString();
            } catch (ReflectiveOperationException ignored) {}
        }
        return object.toString();
    }

    private int objectLevel(Object object) {
        if (object instanceof Number n) return n.intValue();
        if (object == null) return 0;
        for (String methodName : List.of("getLevel", "getValue", "level")) {
            try {
                Object value = object.getClass().getMethod(methodName).invoke(object);
                if (value instanceof Number n) return n.intValue();
            } catch (ReflectiveOperationException ignored) {}
        }
        try { return Integer.parseInt(object.toString()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private List<AppliedEnchant> readViaLore(ItemStack item) {
        if (!item.hasItemMeta()) return List.of();
        List<String> lore = new ArrayList<>();
        if (item.getItemMeta().displayName() != null) {
            lore.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).trim());
        }
        if (item.getItemMeta().lore() != null) {
            lore.addAll(item.getItemMeta().lore().stream()
                    .map(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line).trim())
                    .toList());
        }
        List<AppliedEnchant> out = new ArrayList<>();
        for (EnchantDefinition def : definitions.values()) {
            for (String line : lore) {
                String lower = line.toLowerCase(Locale.ROOT);
                String idLabel = def.id().replace('_', ' ').replace('-', ' ');
                String display = stripColors(def.displayName()).toLowerCase(Locale.ROOT);
                if (!lower.contains(display) && !lower.contains(idLabel)) continue;
                int level = parseLevel(line);
                if (level < 1) level = 1;
                out.add(new AppliedEnchant(def.id(), Math.min(level, def.maxLevel())));
                break;
            }
        }
        return out;
    }

    private String stripColors(String text) {
        return COLOR.matcher(text == null ? "" : text).replaceAll("").trim();
    }

    private int parseLevel(String line) {
        Matcher matcher = NUMBER.matcher(line.trim());
        if (!matcher.find()) return 1;
        String token = matcher.group(1).toUpperCase(Locale.ROOT);
        try { return Integer.parseInt(token); }
        catch (NumberFormatException ignored) { return romanToInt(token); }
    }

    private int romanToInt(String value) {
        int total = 0, previous = 0;
        for (int i = value.length() - 1; i >= 0; i--) {
            int current = switch (value.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
                case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000; default -> 0;
            };
            total += current < previous ? -current : current;
            previous = current;
        }
        return total;
    }

    public ItemStack createBook(String id, int level) {
        EnchantDefinition definition = definitions.get(id.toLowerCase(Locale.ROOT));
        if (definition == null) return null;
        // AE enchant books are supported by AEAPI even though the enchantment's normal item rule
        // may not include ENCHANTED_BOOK. Apply to a blank book via the same official entry point.
        if (!available()) return null;
        try {
            Method method = ((Class<?>) apiClass).getMethod("applyEnchant", String.class, int.class, ItemStack.class);
            Object result = method.invoke(null, definition.id(), Math.max(1, Math.min(level, definition.maxLevel())), new ItemStack(Material.ENCHANTED_BOOK));
            if (!(result instanceof ItemStack stack) || stack.getType() != Material.ENCHANTED_BOOK) return null;
            boolean confirmed = readViaApi(stack).stream().anyMatch(enchant -> enchant.id().equalsIgnoreCase(definition.id()))
                    || readViaLore(stack).stream().anyMatch(enchant -> enchant.id().equalsIgnoreCase(definition.id()));
            return confirmed ? stack : null;
        } catch (ReflectiveOperationException ex) {
            owner.getLogger().warning("Could not create AdvancedEnchantments book for '" + id + "': " + ex.getMessage());
            return null;
        }
    }
}
