package io.octammo.api;

import io.octammo.OctaMMO;
import io.octammo.core.ItemRef;
import io.octammo.core.MmoItems;
import io.octammo.upgrade.UpgradeFile;
import io.octammo.upgrade.UpgradeRecipe;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * Public API for other plugins.
 *
 * <pre>
 * RegisteredServiceProvider&lt;OctaMMOAPI&gt; rsp = Bukkit.getServicesManager().getRegistration(OctaMMOAPI.class);
 * OctaMMOAPI api = rsp.getProvider();
 * </pre>
 * Events: {@code UpgradePreEvent}, {@code UpgradeResultEvent}, {@code ItemUpdateEvent}.
 */
public final class OctaMMOAPI {
    private final OctaMMO plugin;

    public OctaMMOAPI(OctaMMO plugin) {
        this.plugin = plugin;
    }

    public static OctaMMOAPI get() {
        return OctaMMO.getInstance().api();
    }

    /** TYPE/ID of an MMOItem stack, empty if it is not an MMOItem. */
    public Optional<ItemRef> identify(@Nullable ItemStack stack) {
        return Optional.ofNullable(MmoItems.identify(stack));
    }

    /** Names of loaded upgrade files (lowercase). */
    public @NotNull Set<String> getUpgradeFiles() {
        return plugin.upgrades().names();
    }

    public Optional<UpgradeFile> getUpgradeFile(@NotNull String name) {
        return Optional.ofNullable(plugin.upgrades().get(name));
    }

    /** The recipe of a file that would be used for this input item. */
    public Optional<UpgradeRecipe> findRecipe(@NotNull String file, @Nullable ItemStack input) {
        UpgradeFile f = plugin.upgrades().get(file);
        ItemRef ref = MmoItems.identify(input);
        if (f == null || ref == null) return Optional.empty();
        return Optional.ofNullable(f.find(ref));
    }

    /** Opens the path menu (or the Altar Forge for single-path stations). Permission and region checks apply. */
    public boolean openUpgrade(@NotNull Player player, @NotNull String file) {
        return plugin.upgrades().open(player, file, player);
    }

    /** Opens the Altar Forge of one path directly. */
    public boolean openUpgrade(@NotNull Player player, @NotNull String file, @NotNull String path) {
        return plugin.upgrades().open(player, file, player, path);
    }

    /** True while the loading animation of an upgrade runs for this player. */
    public boolean isAnimating(@NotNull Player player) {
        return plugin.upgrades().enabled() && plugin.service().isAnimating(player);
    }

    /** True if the Item Update module considers this stack outdated. */
    public boolean isOutdated(@Nullable ItemStack stack) {
        return plugin.updater().isOutdated(stack);
    }

    /** Runs an update scan on the player now (ignores the cooldown). @return replaced stacks. */
    public int updatePlayer(@NotNull Player player) {
        return plugin.updater().scan(player, true);
    }
}
