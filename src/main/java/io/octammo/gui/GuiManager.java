package io.octammo.gui;

import io.octammo.OctaMMO;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads and caches the layouts from plugins/OctaMMO/guis/. */
public final class GuiManager {
    private final OctaMMO plugin;
    private final Map<String, GuiLayout> forge = new HashMap<>();
    private GuiLayout pathSelect;
    private GuiLayout confirm;

    public GuiManager(OctaMMO plugin) {
        this.plugin = plugin;
    }

    public void saveDefaults() {
        for (String name : List.of("default-upgrade", "path-select", "confirm")) {
            if (!new File(plugin.getDataFolder(), "guis/" + name + ".yml").exists()) plugin.saveResource("guis/" + name + ".yml", false);
        }
    }

    public void reload() {
        forge.clear();
        pathSelect = null;
        confirm = null;
    }

    public GuiLayout forge(String name) {
        return forge.computeIfAbsent(name, n -> GuiLayout.load(plugin, n, "default-upgrade", ForgeGui.SINGLES, ForgeGui.LISTS));
    }

    public GuiLayout pathSelect() {
        if (pathSelect == null) pathSelect = GuiLayout.load(plugin, "path-select", "path-select", PathSelectGui.SINGLES, PathSelectGui.LISTS);
        return pathSelect;
    }

    public GuiLayout confirm() {
        if (confirm == null) confirm = GuiLayout.load(plugin, "confirm", "confirm", ConfirmGui.SINGLES, java.util.Set.of());
        return confirm;
    }
}
