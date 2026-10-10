package io.octammo.update;

import io.octammo.OctaMMOPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A deterministic signature of every MMOItems template, computed from the template's own YAML under
 * plugins/MMOItems/item/ (sorted keys, so the order in the file does not matter). When an admin changes a
 * stat, lore, name or material in that file the signature changes. Scanning runs async; the result is an
 * immutable map that the main thread only reads.
 */
public final class TemplateSignatures {
    private final OctaMMOPlugin plugin;
    private volatile Map<String, String> signatures = Map.of();
    private volatile boolean ready;
    private volatile long fingerprint = Long.MIN_VALUE;
    private volatile Set<String> ignoreStats = Set.of();
    private BukkitTask watcher;
    private boolean warnedMissing;

    public TemplateSignatures(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean ready() { return ready; }
    public int size() { return signatures.size(); }

    /** Signature of TYPE:ID, or null for an unknown template. */
    public String get(String type, String id) {
        return signatures.get(type.toUpperCase(Locale.ROOT) + ":" + id.toUpperCase(Locale.ROOT));
    }

    /** (Re)reads ignore-stats and rescans async. Also (re)starts the optional file watcher. */
    public void reload() {
        Set<String> ignore = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("update.ignore-stats")) ignore.add(normalize(s));
        ignoreStats = ignore;
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::scan);
        int every = plugin.getConfig().getInt("update.watch-interval-seconds", 30);
        if (every > 0 && plugin.getConfig().getBoolean("modules.item-update", true)) {
            long ticks = every * 20L;
            watcher = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                if (currentFingerprint() != fingerprint) scan();
            }, ticks, ticks);
        }
    }

    public void stop() {
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
    }

    private File root() {
        Plugin mmo = Bukkit.getPluginManager().getPlugin("MMOItems");
        return mmo == null ? null : new File(mmo.getDataFolder(), "item");
    }

    private static void collect(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) out.add(f);
        }
    }

    private long currentFingerprint() {
        File root = root();
        if (root == null || !root.isDirectory()) return 0;
        List<File> files = new ArrayList<>();
        collect(root, files);
        long fp = files.size();
        for (File f : files) fp = fp * 31 + f.lastModified() + f.length();
        return fp;
    }

    private void scan() {
        File root = root();
        if (root == null || !root.isDirectory()) {
            if (!warnedMissing) {
                warnedMissing = true;
                plugin.getLogger().warning("Item Update: " + (root == null ? "MMOItems" : root.getPath()) + " not found, no item will be updated.");
            }
            return;
        }
        long fp = currentFingerprint();
        List<File> files = new ArrayList<>();
        collect(root, files);
        Map<String, String> map = new HashMap<>();
        for (File f : files) {
            String type = typeOf(root, f);
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            for (String id : yml.getKeys(false)) {
                ConfigurationSection s = yml.getConfigurationSection(id);
                if (s != null) map.put(type + ":" + id.toUpperCase(Locale.ROOT), hash(canonical(s)));
            }
        }
        signatures = Map.copyOf(map);
        fingerprint = fp;
        ready = true;
    }

    /** item/sword.yml -> SWORD, item/sword/anything.yml -> SWORD */
    private static String typeOf(File root, File f) {
        File parent = f.getParentFile();
        if (parent.equals(root)) return f.getName().substring(0, f.getName().length() - 4).toUpperCase(Locale.ROOT);
        File top = parent;
        while (top.getParentFile() != null && !top.getParentFile().equals(root)) top = top.getParentFile();
        return top.getName().toUpperCase(Locale.ROOT);
    }

    private static String normalize(String key) {
        return key.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    /** Sorted key=value text of a section; ignored stats are skipped at every depth. */
    String canonical(ConfigurationSection s) {
        StringBuilder sb = new StringBuilder();
        List<String> keys = new ArrayList<>(s.getKeys(false));
        Collections.sort(keys);
        for (String key : keys) {
            if (ignoreStats.contains(normalize(key))) continue;
            Object v = s.get(key);
            sb.append(key).append('=');
            if (v instanceof ConfigurationSection child) sb.append('{').append(canonical(child)).append('}');
            else sb.append(v);
            sb.append(';');
        }
        return sb.toString();
    }

    static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
