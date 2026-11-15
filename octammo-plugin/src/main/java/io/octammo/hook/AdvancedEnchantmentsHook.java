package io.octammo.hook;

import io.octammo.core.ItemRef;
import io.octammo.core.MmoItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
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
                                    String displayPattern, String description, List<String> applies, List<String> conflicts) {
        public EnchantDefinition {
            applies = List.copyOf(applies);
            conflicts = List.copyOf(conflicts);
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
        Plugin ae = Bukkit.getPluginManager().getPlugin("AdvancedEnchantments");
        if (ae == null) return;
        try {
            // Resolve the API from AdvancedEnchantments' own class loader. Plugin APIs are
            // not guaranteed to be visible through OctaMMO's class loader.
            apiClass = Class.forName("net.advancedplugins.ae.api.AEAPI", true, ae.getClass().getClassLoader());
        } catch (ClassNotFoundException ex) {
            owner.getLogger().warning("AdvancedEnchantments is enabled, but AEAPI was not found. Custom-enchant actions are unavailable.");
            available = false;
            return;
        }
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
        String group = cfg.getString("group", "SIMPLE").toUpperCase(Locale.ROOT).trim();
        int maxLevel = maximumLevel(cfg);
        List<String> applies = cfg.getStringList("applies").stream()
                .map(value -> value.toUpperCase(Locale.ROOT).trim())
                .filter(value -> !value.isBlank())
                .toList();
        // Older/community AE definitions often use applies-to: "Boots" and may omit
        // the newer applies list. Keep both formats usable in the custom enchanter.
        if (applies.isEmpty()) {
            String appliesTo = cfg.getString("applies-to", "");
            if (appliesTo != null && !appliesTo.isBlank()) {
                applies = List.of(appliesTo.toUpperCase(Locale.ROOT).trim());
            }
        }
        String description;
        List<String> descriptionLines = cfg.getStringList("description");
        if (!descriptionLines.isEmpty()) description = String.join(" ", descriptionLines);
        else description = cfg.getString("description", "");
        if (description == null) description = "";
        description = PLACEHOLDER.matcher(description).replaceAll("").trim();
        List<String> conflicts = new ArrayList<>(cfg.getStringList("conflicts").stream()
                .map(value -> value.toLowerCase(Locale.ROOT).trim()).filter(value -> !value.isBlank()).toList());
        String singleConflict = cfg.getString("conflicts", "");
        if (conflicts.isEmpty() && singleConflict != null && !singleConflict.isBlank()) {
            conflicts.addAll(java.util.Arrays.stream(singleConflict.split(","))
                    .map(value -> value.toLowerCase(Locale.ROOT).trim()).filter(value -> !value.isBlank()).toList());
        }
        definitions.put(id, new EnchantDefinition(id, display, group, maxLevel,
                cfg.getString("display", display), description, applies, conflicts));
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

    public List<EnchantDefinition> allDefinitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(EnchantDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<EnchantDefinition> definitions(String group, ItemStack item) {
        String wanted = group == null ? "" : group.toUpperCase(Locale.ROOT);
        return definitions.values().stream()
                .filter(def -> def.group().equalsIgnoreCase(wanted))
                .filter(def -> canApply(def, item))
                .sorted(Comparator.comparing(EnchantDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<EnchantDefinition> definitionsFor(ItemStack item) {
        return definitions.values().stream()
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

    /** Returns the readable custom-enchant levels currently stored on an item. */
    public Map<String, Integer> readEnchantLevels(ItemStack item) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (AppliedEnchant enchant : readEnchants(item)) {
            levels.merge(enchant.id().toLowerCase(Locale.ROOT), enchant.level(), Math::max);
        }
        return Map.copyOf(levels);
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
            if (ref != null && (rule.equals("ALL_MMOITEMS") || rule.equals("MMOITEMS")
                    || rule.equals("MMOITEMS:" + ref.type().toUpperCase(Locale.ROOT) + ":" + ref.id().toUpperCase(Locale.ROOT))
                    || rule.equals(ref.type().toUpperCase(Locale.ROOT) + ":" + ref.id().toUpperCase(Locale.ROOT))
                    || rule.equals("ID:" + ref.id().toUpperCase(Locale.ROOT)))) return true;
            if (rule.startsWith("ALL_")) {
                String category = normalizeCategory(rule.substring(4));
                if (matchesCategory(category, material, itemType)) return true;
            } else if (rule.endsWith("_ARMOR")) {
                String armor = rule.substring(0, rule.length() - "_ARMOR".length());
                if (matchesArmorMaterial(armor, material)) return true;
            } else if (matchesCategory(normalizeCategory(rule), material, itemType)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeCategory(String category) {
        return switch (category) {
            case "SWORDS" -> "SWORD";
            case "AXES" -> "AXE";
            case "PICKAXES" -> "PICKAXE";
            case "SHOVELS" -> "SHOVEL";
            case "SPADES" -> "SPADE";
            case "HOES" -> "HOE";
            case "HELMETS" -> "HELMET";
            case "CHESTPLATES" -> "CHESTPLATE";
            case "LEGGINGS" -> "LEGGINGS";
            case "BOOTS" -> "BOOTS";
            case "ARMORS" -> "ARMOR";
            case "WEAPONS" -> "WEAPON";
            case "TOOLS" -> "TOOL";
            default -> category;
        };
    }

    private boolean matchesCategory(String category, String material, String itemType) {
        Material bukkitMaterial = Material.matchMaterial(material);
        if (category.equals("ARMOR")) return isArmor(material) || itemType.equals("ARMOR");
        if (category.equals("BOOTS")) return isBoots(material) || itemType.equals("BOOTS");
        if (category.equals("HELMET")) return isHelmet(material) || itemType.equals("HELMET");
        if (category.equals("CHESTPLATE")) return isChestplate(material) || itemType.equals("CHESTPLATE");
        if (category.equals("LEGGINGS")) return isLeggings(material) || itemType.equals("LEGGINGS");
        if (category.equals("SWORD")) return material.endsWith("_SWORD") || itemType.equals("SWORD");
        if (category.equals("AXE")) return material.endsWith("_AXE") || itemType.equals("AXE");
        if (category.equals("PICKAXE")) return material.endsWith("_PICKAXE") || itemType.equals("PICKAXE");
        if (category.equals("SHOVEL") || category.equals("SPADE")) return material.endsWith("_SHOVEL") || material.endsWith("_SPADE") || itemType.equals("SHOVEL") || itemType.equals("SPADE");
        if (category.equals("HOE")) return material.endsWith("_HOE") || itemType.equals("HOE");
        if (category.equals("WEAPON")) return isWeapon(material) || isWeapon(itemType);
        if (category.equals("TOOLS") || category.equals("TOOL")) return isTool(material) || isTool(itemType);
        if (category.equals("EDIBLE") || category.equals("FOOD")) return bukkitMaterial != null && bukkitMaterial.isEdible();
        if (category.equals("BLOCK") || category.equals("BLOCKS")) return bukkitMaterial != null && bukkitMaterial.isBlock();
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
    private boolean isWeapon(String value) {
        return value.endsWith("_SWORD") || value.endsWith("_AXE") || value.equals("SWORD")
                || value.equals("AXE") || value.equals("DAGGER") || value.equals("SPEAR")
                || value.equals("TRIDENT") || value.equals("BOW") || value.equals("CROSSBOW");
    }
    private boolean isTool(String value) {
        return value.endsWith("_PICKAXE") || value.endsWith("_SHOVEL") || value.endsWith("_SPADE")
                || value.endsWith("_AXE") || value.endsWith("_HOE") || value.equals("PICKAXE")
                || value.equals("SHOVEL") || value.equals("SPADE") || value.equals("AXE") || value.equals("HOE");
    }

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
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (AppliedEnchant enchant : readViaApi(item)) {
            merged.merge(enchant.id().toLowerCase(Locale.ROOT), enchant.level(), Math::max);
        }
        for (AppliedEnchant enchant : readViaLore(item)) {
            merged.merge(enchant.id().toLowerCase(Locale.ROOT), enchant.level(), Math::max);
        }
        return merged.entrySet().stream()
                .map(entry -> new AppliedEnchant(entry.getKey(), entry.getValue()))
                .toList();
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
        List<AppliedEnchant> out = new ArrayList<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                addParsed(out, objectId(entry.getKey()), objectLevel(entry.getValue()));
            }
        } else if (value instanceof Iterable<?> values) {
            for (Object element : values) {
                String id = objectId(element);
                int level = objectLevel(element);
                if (level < 1) {
                    try {
                        Object name = element.getClass().getMethod("getEnchant").invoke(element);
                        Object amount = element.getClass().getMethod("getLevel").invoke(element);
                        id = objectId(name);
                        level = objectLevel(amount);
                    } catch (ReflectiveOperationException ignored) { }
                }
                addParsed(out, id, level);
            }
        }
        return out;
    }

    private void addParsed(List<AppliedEnchant> out, String rawId, int level) {
        if (rawId == null || level < 1) return;
        String id = rawId.toLowerCase(Locale.ROOT);
        EnchantDefinition def = definitions.get(id);
        if (def != null) out.add(new AppliedEnchant(def.id(), Math.min(level, def.maxLevel())));
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

    /**
     * Requests the actual enchanted-book item from AdvancedEnchantments itself.
     * AE's /ae give command applies the plugin's configured book material, metadata,
     * success/destroy values and current book format. The generated item is captured
     * from the player's storage inventory and removed from the temporary location.
     *
     * The operation is synchronous and only changes inventory slots that AE changed.
     */
    public ItemStack createBook(Player player, String id, int level) {
        if (!available() || player == null || !player.isOnline()) return null;
        EnchantDefinition definition = definitions.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (definition == null) return null;

        ItemStack[] before = cloneStorage(player.getInventory().getStorageContents());
        boolean hasEmptySlot = java.util.Arrays.stream(before).anyMatch(this::isEmpty);
        if (!hasEmptySlot) return null;

        int safeLevel = Math.max(1, Math.min(level, definition.maxLevel()));
        String command = "ae give {player} {enchant} {level}";
        if (owner instanceof JavaPlugin javaPlugin) {
            command = javaPlugin.getConfig().getString("item-fixer.ae-book-command", command);
        }
        if (!definition.id().matches("[A-Za-z0-9_-]+") || !player.getName().matches("[A-Za-z0-9_]+")) return null;
        command = command.replace("{player}", player.getName())
                .replace("{enchant}", definition.id())
                .replace("{level}", Integer.toString(safeLevel));
        if (command.isBlank() || command.startsWith("/")) return null;

        try {
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            Bukkit.dispatchCommand(console, command);
        } catch (RuntimeException ex) {
            owner.getLogger().warning("AdvancedEnchantments book command failed for " + definition.id() + ": " + ex.getMessage());
            return null;
        }

        ItemStack[] after = cloneStorage(player.getInventory().getStorageContents());
        List<InventoryDelta> deltas = new ArrayList<>();
        for (int slot = 0; slot < Math.min(before.length, after.length); slot++) {
            ItemStack oldItem = before[slot];
            ItemStack newItem = after[slot];
            if (sameStack(oldItem, newItem)) continue;

            if (isEmpty(oldItem) && !isEmpty(newItem)) {
                deltas.add(new InventoryDelta(slot, newItem.clone()));
            } else if (!isEmpty(oldItem) && !isEmpty(newItem) && oldItem.isSimilar(newItem)
                    && newItem.getAmount() > oldItem.getAmount()) {
                ItemStack added = newItem.clone();
                added.setAmount(newItem.getAmount() - oldItem.getAmount());
                deltas.add(new InventoryDelta(slot, added));
            } else {
                // A command must not replace or consume an existing inventory item.
                restoreStorage(player, before);
                return null;
            }
        }

        // Restore temporary AE command output to its pre-command state before returning it.
        restoreStorage(player, before);
        int totalAdded = deltas.stream().mapToInt(delta -> delta.item().getAmount()).sum();
        if (deltas.size() != 1 || totalAdded != 1) return null;
        ItemStack generatedBook = deltas.get(0).item();
        generatedBook.setAmount(1);
        return generatedBook;
    }

    private record InventoryDelta(int slot, ItemStack item) {}

    private ItemStack[] cloneStorage(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return copy;
    }

    private void restoreStorage(Player player, ItemStack[] contents) {
        player.getInventory().setStorageContents(cloneStorage(contents));
    }

    private boolean sameStack(ItemStack first, ItemStack second) {
        if (isEmpty(first) && isEmpty(second)) return true;
        if (isEmpty(first) || isEmpty(second)) return false;
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

}
