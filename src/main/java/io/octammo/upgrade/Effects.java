package io.octammo.upgrade;

import io.octammo.OctaMMO;
import io.octammo.core.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

/** on-success / on-fail-effects of a path: console commands, a sound and an optional extra title. */
public record Effects(List<String> commands, String sound, String title, String subtitle) {
    public static final Effects NONE = new Effects(List.of(), "", "", "");

    public static Effects parse(ConfigurationSection s) {
        if (s == null) return NONE;
        return new Effects(List.copyOf(s.getStringList("commands")), s.getString("sound", ""),
                s.getString("title", ""), s.getString("subtitle", ""));
    }

    public void run(OctaMMO plugin, Player player) {
        for (String cmd : commands) {
            String line = cmd.replace("{player}", player.getName());
            if (line.startsWith("/")) line = line.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
        Sounds.playRaw(player, sound, 1f, 1f);
        if (!title.isBlank() || !subtitle.isBlank()) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                    plugin.lang().render(title, player, null), plugin.lang().render(subtitle, player, null)));
        }
    }
}
