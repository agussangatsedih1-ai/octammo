package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** External per-feature GUI settings stored under plugins/OctaMMO/guis/. */
public final class FeatureGuiConfig {
    private final YamlConfiguration yaml;
    private FeatureGuiConfig(YamlConfiguration yaml) { this.yaml = yaml; }
    public static FeatureGuiConfig load(OctaMMOPlugin plugin, String file, String resource) {
        File folder = new File(plugin.getDataFolder(), "guis");
        if (!folder.exists()) folder.mkdirs();
        File target = new File(folder, file);
        if (!target.exists()) {
            try { plugin.saveResource("guis/" + resource, false); }
            catch (IllegalArgumentException ignored) {
                try { target.createNewFile(); } catch (IOException ex) { plugin.getLogger().warning("Could not create " + target.getName() + ": " + ex.getMessage()); }
            } catch (Exception ex) { plugin.getLogger().warning("Could not copy GUI defaults: " + ex.getMessage()); }
        }
        return new FeatureGuiConfig(YamlConfiguration.loadConfiguration(target));
    }
    public String title(String fallback) { return yaml.getString("title", fallback); }
    public int slot(String key, int fallback) {
        int value = yaml.getInt("slots." + key, fallback);
        return value >= 0 && value < 54 ? value : fallback;
    }
    public int[] slots(String key, int[] fallback) {
        List<Integer> values = yaml.getIntegerList("slots." + key);
        if (values.isEmpty()) return fallback;
        int[] checked = values.stream().filter(value -> value >= 0 && value < 54).distinct().mapToInt(Integer::intValue).toArray();
        return checked.length == 0 ? fallback : checked;
    }
    public String fillerMaterial(String fallback) { return yaml.getString("decoration.material", fallback); }
    public String fillerName(String fallback) { return yaml.getString("decoration.name", fallback); }
    public String fillerLore(String fallback) { return yaml.getString("decoration.lore", fallback); }
}
