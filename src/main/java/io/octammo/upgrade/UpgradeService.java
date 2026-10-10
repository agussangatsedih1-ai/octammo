package io.octammo.upgrade;

import io.octammo.OctaMMO;
import io.octammo.api.event.UpgradeAnimationEndEvent;
import io.octammo.api.event.UpgradeAnimationStartEvent;
import io.octammo.api.event.UpgradePreEvent;
import io.octammo.api.event.UpgradeResultEvent;
import io.octammo.core.Items;
import io.octammo.core.MmoItems;
import io.octammo.core.PlayerStats;
import io.octammo.core.Sounds;
import io.octammo.gui.ForgeGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs upgrades. Order of one attempt:
 * validate, consume everything and roll ONCE, persist the rewards, animate 1% to 100%, deliver.
 */
public final class UpgradeService {
    private final OctaMMO plugin;
    private final Map<UUID, UpgradeSession> sessions = new HashMap<>();

    public UpgradeService(OctaMMO plugin) {
        this.plugin = plugin;
    }

    public boolean isAnimating(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    /** Called by the GO button. */
    public void tryStart(ForgeGui gui) {
        Player p = gui.player();
        if (gui.locked() || sessions.containsKey(p.getUniqueId())) return;

        ForgeGui.Problem problem = gui.problem();
        if (problem != ForgeGui.Problem.NONE) {
            plugin.lang().send(p, "upgrade.problem." + problem.name().toLowerCase().replace('_', '-'));
            gui.refresh();
            return;
        }
        boolean risky = gui.recipe().onFail() == UpgradeRecipe.FailMode.DESTROY && gui.activeProtection() == null;
        if (risky && plugin.getConfig().getBoolean("upgrade.confirm-destroy", true)) {
            gui.switchToConfirm();
            return;
        }
        begin(gui);
    }

    /** Validates again, consumes, rolls, persists and starts the animation. */
    public void begin(ForgeGui gui) {
        Player p = gui.player();
        if (gui.locked() || sessions.containsKey(p.getUniqueId())) return;
        UpgradeFile file = gui.file();
        UpgradeRecipe recipe = gui.recipe();

        ForgeGui.Problem problem = gui.problem();
        if (problem != ForgeGui.Problem.NONE) {
            plugin.lang().send(p, "upgrade.problem." + problem.name().toLowerCase().replace('_', '-'));
            gui.refresh();
            return;
        }

        // The result must exist BEFORE anything is taken, so a broken output never eats the player's items.
        ItemStack output = MmoItems.create(recipe.output());
        if (output == null) {
            plugin.lang().send(p, "upgrade.output-missing", "item", recipe.output().key());
            plugin.getLogger().warning("Path '" + recipe.key() + "' (" + recipe.file() + "): output "
                    + recipe.output().key() + " does not exist in MMOItems.");
            return;
        }

        UpgradeFile.Booster booster = gui.activeBooster();
        double chance = Math.max(0, Math.min(100, recipe.chance() + (booster == null ? 0 : booster.bonus())));
        UpgradePreEvent pre = new UpgradePreEvent(p, recipe, chance);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return;
        chance = pre.getChance();

        AnimConfig cfg = AnimConfig.read(plugin, file, recipe);
        boolean animate = cfg.enabled() && cfg.duration() > 0;
        if (animate) {
            UpgradeAnimationStartEvent start = new UpgradeAnimationStartEvent(p, recipe, cfg.duration());
            Bukkit.getPluginManager().callEvent(start);
            if (start.isCancelled()) return;
            cfg = cfg.withDuration(start.getDurationTicks());
        }

        // ---- point of no return: from here every click on the GUI is refused
        gui.lock();
        if (!plugin.costService().consumeNonItems(p, recipe.costs())) {
            gui.unlock();
            plugin.lang().send(p, "upgrade.payment-failed");
            return;
        }

        boolean success = ThreadLocalRandom.current().nextDouble(100.0) < chance;   // the ONLY roll
        boolean protectionUsed = !success && recipe.onFail() == UpgradeRecipe.FailMode.DESTROY && gui.activeProtection() != null;
        List<ItemStack> rewards = gui.settle(success, protectionUsed);
        ItemStack given = null;
        if (success) {
            given = output.clone();
            int amount = recipe.output().amount();
            int max = Math.max(1, output.getMaxStackSize());
            while (amount > 0) {
                ItemStack stack = output.clone();
                stack.setAmount(Math.min(max, amount));
                rewards.add(stack);
                amount -= stack.getAmount();
            }
        }

        UpgradeSession s = new UpgradeSession(p.getUniqueId(), p.getName(), gui, recipe, success, protectionUsed,
                chance, rewards, given, cfg);
        sessions.put(p.getUniqueId(), s);

        if (!animate) {
            finish(s, true);
            return;
        }
        gui.renderAnimating();
        // wait for the safety copy to reach the disk (async), then start the animation
        plugin.pending().save(s).whenComplete((v, t) -> {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> startAnimation(s));
            } catch (IllegalPluginAccessException ignored) {
                // plugin disabled: finishAll() already delivered
            }
        });
    }

    private void startAnimation(UpgradeSession s) {
        if (s.done) return;
        Player p = Bukkit.getPlayer(s.playerId);
        if (p == null || !p.isOnline()) {
            finish(s, true);
            return;
        }
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(s), 0L, s.cfg.interval());
    }

    private void tick(UpgradeSession s) {
        if (s.done) return;
        Player p = Bukkit.getPlayer(s.playerId);
        if (p == null || !p.isOnline()) {
            finish(s, true);
            return;
        }
        s.elapsed += s.cfg.interval();
        int percent = (int) Math.max(1, Math.min(100, Math.round(100.0 * s.elapsed / s.cfg.duration())));
        s.gui.paintBorder(s.elapsed / 4);
        if (percent != s.percent) {
            s.percent = percent;
            Map<String, String> ph = Map.of("percent", String.valueOf(percent));
            long stay = Math.max(500L, (s.cfg.interval() + 10L) * 50L);
            p.showTitle(Title.title(plugin.lang().render(s.cfg.title(), p, ph), plugin.lang().render(s.cfg.subtitle(), p, ph),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(stay), Duration.ZERO)));
            s.gui.setProgress(percent, s.elapsed);
            if (s.cfg.soundEnabled() && percent % 5 == 0 && percent != s.lastSoundPercent) {
                s.lastSoundPercent = percent;
                Sounds.playRaw(p, s.cfg.tickSound(), 0.6f, 0.5f + percent / 100f * 1.5f);
            }
        }
        if (percent >= 100) finish(s, false);
    }

    /**
     * Delivers the result. {@code instant} skips the animation (disconnect, shutdown, animation off).
     * A player who is not online keeps the pending-rewards file and gets the items on the next join.
     */
    public void finish(UpgradeSession s, boolean instant) {
        if (s.done) return;
        s.done = true;
        if (s.task != null) s.task.cancel();
        sessions.remove(s.playerId);

        Player p = Bukkit.getPlayer(s.playerId);
        boolean online = p != null && p.isOnline();
        if (online) {
            for (ItemStack item : s.rewards) Items.giveOrDrop(p, item);
            plugin.pending().delete(s);

            if (s.success) {
                plugin.stats().add(p, PlayerStats.UPGRADE_SUCCESS, 1);
                plugin.lang().send(p, "upgrade.success", "item", MmoItems.displayName(s.recipe.output()));
                Sounds.play(plugin, p, "success");
                s.recipe.onSuccess().run(plugin, p);
            } else {
                plugin.stats().add(p, PlayerStats.UPGRADE_FAIL, 1);
                String key = s.protectionUsed ? "upgrade.failed-protected"
                        : s.recipe.onFail() == UpgradeRecipe.FailMode.DESTROY ? "upgrade.failed-destroy" : "upgrade.failed-keep";
                plugin.lang().send(p, key);
                Sounds.play(plugin, p, "fail");
                s.recipe.onFailEffects().run(plugin, p);
            }
            if (!instant) {
                Component head = plugin.lang().render(s.success ? "{upgrade.animation.done-success}" : "{upgrade.animation.done-fail}", p, null);
                p.showTitle(Title.title(head, Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
            }
        }

        plugin.upgradeLog().log(s.playerName + " | " + s.recipe.file() + "/" + s.recipe.key()
                + " | " + (s.success ? "SUCCESS" : "FAIL") + (s.protectionUsed ? " (protected)" : "")
                + " | chance=" + ForgeGui.fmt(s.chance) + "% | input=" + s.inputKey
                + " | output=" + s.recipe.output().key() + " | rewards=" + s.rewards.size()
                + " | " + (online ? "delivered" : "DEFERRED to next join") + " | session=" + s.id);

        if (p != null) {
            Bukkit.getPluginManager().callEvent(new UpgradeResultEvent(p, s.recipe, s.success, s.output));
            Bukkit.getPluginManager().callEvent(new UpgradeAnimationEndEvent(p, s.recipe, s.success, instant));
        }

        s.gui.endAnimation();
        if (online && !instant && s.cfg.closeAfterResult() && s.gui.isViewing()) p.closeInventory();
    }

    /** The player left: finish at once, the items go to the inventory that is about to be saved. */
    public void handleQuit(Player p) {
        UpgradeSession s = sessions.get(p.getUniqueId());
        if (s != null) finish(s, true);
    }

    /** Server stop / plugin disable / reload of the jar. */
    public void finishAll() {
        for (UpgradeSession s : new ArrayList<>(sessions.values())) finish(s, true);
    }
}
