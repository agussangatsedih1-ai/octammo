package io.octammo;

import io.octammo.api.OctaMMOAPI;
import io.octammo.command.ItemUpgradeCommand;
import io.octammo.command.OctaCommand;
import io.octammo.core.Lang;
import io.octammo.core.MmoItems;
import io.octammo.core.PlayerStats;
import io.octammo.hook.EconomyHook;
import io.octammo.hook.FloodgateHook;
import io.octammo.hook.PapiHook;
import io.octammo.hook.WorldGuardHook;
import io.octammo.update.ItemUpdater;
import io.octammo.gui.GuiListener;
import io.octammo.gui.GuiManager;
import io.octammo.update.UpdateListener;
import io.octammo.upgrade.CostService;
import io.octammo.upgrade.PendingRewards;
import io.octammo.upgrade.UpgradeLog;
import io.octammo.upgrade.UpgradeService;
import io.octammo.upgrade.UpgradeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class OctaMMO extends JavaPlugin {
    private static OctaMMO instance;

    private Lang lang;
    private PlayerStats stats;
    private ItemUpdater updater;
    private UpgradeManager upgrades;
    private CostService costService;
    private OctaMMOAPI api;
    private GuiManager guis;
    private UpgradeService service;
    private PendingRewards pending;
    private UpgradeLog upgradeLog;
    private ExecutorService io;
    private PapiHook papi;

    // optional hooks, null when the plugin is not installed
    private EconomyHook economy;
    private WorldGuardHook worldGuard;
    private FloodgateHook floodgate;

    public static OctaMMO getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;

        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            getLogger().severe("MMOItems (and MythicLib) are required. Disabling OctaMMO.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        if (!new File(getDataFolder(), "updates.yml").exists()) saveResource("updates.yml", false);

        lang = new Lang(this);
        stats = new PlayerStats(this);
        updater = new ItemUpdater(this);
        upgrades = new UpgradeManager(this);
        costService = new CostService(this);
        api = new OctaMMOAPI(this);
        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OctaMMO-IO");
            t.setDaemon(true);
            return t;
        });
        guis = new GuiManager(this);
        guis.saveDefaults();
        pending = new PendingRewards(this, io);
        pending.load();
        upgradeLog = new UpgradeLog(this, io);
        service = new UpgradeService(this);

        setupHooks();
        lang.load();
        updater.load();
        upgrades.load();
        updater.startTasks();

        Bukkit.getPluginManager().registerEvents(new UpdateListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);

        PluginCommand octa = getCommand("octammo");
        if (octa != null) {
            OctaCommand handler = new OctaCommand(this);
            octa.setExecutor(handler);
            octa.setTabCompleter(handler);
        }
        PluginCommand upgrade = getCommand("itemupgrade");
        if (upgrade != null) {
            ItemUpgradeCommand handler = new ItemUpgradeCommand(this);
            upgrade.setExecutor(handler);
            upgrade.setTabCompleter(handler);
        }

        getServer().getServicesManager().register(OctaMMOAPI.class, api, this, ServicePriority.Normal);
        getLogger().info("Enabled. Item Update rules: " + updater.ruleCount() + ", upgrade files: " + upgrades.fileCount());
    }

    @Override
    public void onDisable() {
        // 1) finish running upgrades at once so no result is lost, 2) hand back items from open GUIs
        if (service != null) service.finishAll();
        if (upgrades != null) upgrades.closeAll();
        Bukkit.getScheduler().cancelTasks(this);
        if (io != null) {
            io.shutdown();   // lets queued writes / deletes finish
            try {
                if (!io.awaitTermination(10, TimeUnit.SECONDS)) getLogger().warning("Pending file writes did not finish in 10s.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        instance = null;
    }

    private void setupHooks() {
        var pm = Bukkit.getPluginManager();
        if (pm.isPluginEnabled("Vault")) {
            economy = new EconomyHook();
            if (!economy.available()) getLogger().warning("Vault found but no economy provider is registered yet.");
        } else {
            economy = null;
        }
        worldGuard = pm.isPluginEnabled("WorldGuard") ? new WorldGuardHook() : null;
        floodgate = pm.isPluginEnabled("Floodgate") ? new FloodgateHook() : null;
        if (pm.isPluginEnabled("PlaceholderAPI")) {
            papi = new PapiHook(this);
            papi.register();
        }
    }

    /** Reloads config, lang, updates.yml and upgrades/*.yml. Run /mi reload first when MMOItems changed. */
    public void reloadAll() {
        reloadConfig();
        MmoItems.clearCache();
        if (economy != null) economy.reset();
        lang.load();
        guis.reload();
        Bukkit.getScheduler().cancelTasks(this);
        updater.load();
        upgrades.load();
        updater.startTasks();
    }

    public Lang lang() { return lang; }
    public PlayerStats stats() { return stats; }
    public ItemUpdater updater() { return updater; }
    public UpgradeManager upgrades() { return upgrades; }
    public CostService costService() { return costService; }
    public OctaMMOAPI api() { return api; }
    public GuiManager guis() { return guis; }
    public UpgradeService service() { return service; }
    public PendingRewards pending() { return pending; }
    public UpgradeLog upgradeLog() { return upgradeLog; }
    public PapiHook papi() { return papi; }
    public EconomyHook economy() { return economy; }
    public boolean economyAvailable() { return economy != null && economy.available(); }
    public WorldGuardHook worldGuard() { return worldGuard; }
    public FloodgateHook floodgate() { return floodgate; }
}
