package io.octammo.hook;

import io.octammo.OctaMMO;
import io.octammo.core.PlayerStats;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** %octammo_upgrade_success%, %octammo_upgrade_fail%, %octammo_upgrade_rate%, %octammo_items_updated% */
public final class PapiHook extends PlaceholderExpansion {
    private final OctaMMO plugin;

    public PapiHook(OctaMMO plugin) {
        this.plugin = plugin;
    }

    /** Resolves %placeholders% of other plugins in a text. */
    public String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    @Override public @NotNull String getIdentifier() { return "octammo"; }
    @Override public @NotNull String getAuthor() { return "OctaMMO"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        PlayerStats stats = plugin.stats();
        int ok = stats.get(player, PlayerStats.UPGRADE_SUCCESS);
        int bad = stats.get(player, PlayerStats.UPGRADE_FAIL);
        return switch (params.toLowerCase()) {
            case "upgrade_success" -> String.valueOf(ok);
            case "upgrade_fail" -> String.valueOf(bad);
            case "upgrade_total" -> String.valueOf(ok + bad);
            case "upgrade_rate" -> ok + bad == 0 ? "0" : String.valueOf(Math.round(100.0 * ok / (ok + bad)));
            case "items_updated" -> String.valueOf(stats.get(player, PlayerStats.ITEMS_UPDATED));
            default -> null;
        };
    }
}
