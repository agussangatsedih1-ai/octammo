package io.octammo.core;

import io.octammo.OctaMMO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads lang/<code>.yml (en, id) with fallback to the built-in English file. */
public final class Lang {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private static final Pattern TOKEN = Pattern.compile("\\{([a-z0-9_.\\-]+)\\}");
    private static final Pattern LIST_TOKEN = Pattern.compile("^\\{([a-z0-9_.\\-]+)\\}$");

    private final OctaMMO plugin;
    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();

    public Lang(OctaMMO plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File dir = new File(plugin.getDataFolder(), "lang");
        for (String code : List.of("en", "id")) {
            if (!new File(dir, code + ".yml").exists()) plugin.saveResource("lang/" + code + ".yml", false);
        }
        String code = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        File file = new File(dir, code + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Language file lang/" + code + ".yml not found, using en.");
            file = new File(dir, "en.yml");
        }
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("lang/en.yml")) {
            if (in != null) {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    fallback = YamlConfiguration.loadConfiguration(r);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read built-in lang: " + e.getMessage());
        }
    }

    public String raw(String key) {
        String v = messages.getString(key);
        if (v == null) v = fallback.getString(key);
        return v == null ? key : v;
    }

    public List<String> rawList(String key) {
        List<String> v = messages.isList(key) ? messages.getStringList(key) : null;
        if (v == null) v = fallback.getStringList(key);
        return v;
    }

    public String format(String key, String... kv) {
        return apply(raw(key), kv);
    }

    public Component component(String key, String... kv) {
        return color(format(key, kv));
    }

    public List<Component> components(String key, String... kv) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(key)) out.add(color(apply(line, kv)));
        return out;
    }


    public boolean has(String key) {
        return messages.isString(key) || fallback.isString(key);
    }

    public boolean hasList(String key) {
        return messages.isList(key) || fallback.isList(key);
    }

    /**
     * Expands {lang.key} tokens, then {placeholders} from {@code ph}, then PlaceholderAPI.
     * Unknown tokens are left untouched.
     */
    public String expand(String text, Player player, Map<String, String> ph) {
        if (text == null) return "";
        Matcher m = TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String repl = has(key) ? raw(key) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        String out = sb.toString();
        if (ph != null) {
            for (Map.Entry<String, String> e : ph.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        if (player != null && plugin.papi() != null) out = plugin.papi().apply(player, out);
        return out;
    }

    public Component render(String text, Player player, Map<String, String> ph) {
        return color(expand(text, player, ph));
    }

    /** A line that is exactly one {list.key} expands to the whole list; a value containing \n becomes several lines. */
    public List<Component> renderLines(List<String> rawLines, Player player, Map<String, String> ph) {
        List<Component> out = new ArrayList<>();
        for (String line : rawLines) {
            Matcher m = LIST_TOKEN.matcher(line.trim());
            if (m.matches() && hasList(m.group(1))) {
                for (String inner : rawList(m.group(1))) addSplit(out, expand(inner, player, ph));
            } else {
                addSplit(out, expand(line, player, ph));
            }
        }
        return out;
    }

    private static void addSplit(List<Component> out, String expanded) {
        for (String part : expanded.split("\n", -1)) out.add(color(part));
    }

    /** Sends a message prefixed with the plugin prefix. */
    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(color(raw("prefix") + format(key, kv)));
    }

    /** Sends every line of a list without prefix. */
    public void sendList(CommandSender to, String key, String... kv) {
        for (Component c : components(key, kv)) to.sendMessage(c);
    }

    public static Component color(String s) {
        return LEGACY.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String apply(String s, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return s;
    }
}
