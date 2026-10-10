package io.octammo.upgrade;

import io.octammo.OctaMMO;
import org.bukkit.configuration.file.FileConfiguration;

/** Snapshot of gui-animation.* taken when an upgrade starts (a reload cannot change a running animation). */
public record AnimConfig(boolean enabled, int duration, int interval, String title, String subtitle,
                         boolean soundEnabled, String tickSound, boolean closeAfterResult) {

    public static AnimConfig read(OctaMMO plugin, UpgradeFile file, UpgradeRecipe recipe) {
        FileConfiguration c = plugin.getConfig();
        int duration = c.getInt("gui-animation.duration-ticks", 100);
        if (file.animationDuration() != null) duration = file.animationDuration();
        if (recipe.animationDuration() != null) duration = recipe.animationDuration();
        String title = c.getString("gui-animation.title", "");
        String subtitle = c.getString("gui-animation.subtitle-format", "");
        return new AnimConfig(
                c.getBoolean("gui-animation.enabled", true),
                Math.max(0, duration),
                Math.max(1, c.getInt("gui-animation.update-interval-ticks", 1)),
                title == null || title.isBlank() ? "{upgrade.animation.title}" : title,
                subtitle == null || subtitle.isBlank() ? "{upgrade.animation.subtitle}" : subtitle,
                c.getBoolean("gui-animation.sound.enabled", true),
                c.getString("gui-animation.sound.tick", "BLOCK_NOTE_BLOCK_CHIME"),
                c.getBoolean("gui-animation.close-after-result", false));
    }

    public AnimConfig withDuration(int ticks) {
        return new AnimConfig(enabled, ticks, interval, title, subtitle, soundEnabled, tickSound, closeAfterResult);
    }
}
