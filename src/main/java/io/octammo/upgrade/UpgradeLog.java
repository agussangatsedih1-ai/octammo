package io.octammo.upgrade;

import io.octammo.OctaMMO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

/** plugins/OctaMMO/logs/upgrades.log, written on the I/O thread only. */
public final class UpgradeLog {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OctaMMO plugin;
    private final ExecutorService io;
    private final Path path;

    public UpgradeLog(OctaMMO plugin, ExecutorService io) {
        this.plugin = plugin;
        this.io = io;
        this.path = plugin.getDataFolder().toPath().resolve("logs").resolve("upgrades.log");
    }

    public void log(String line) {
        String text = TS.format(LocalDateTime.now()) + " | " + line + System.lineSeparator();
        try {
            io.execute(() -> {
                try {
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not write upgrades.log", e);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }
}
