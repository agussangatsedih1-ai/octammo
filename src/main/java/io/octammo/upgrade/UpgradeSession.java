package io.octammo.upgrade;

import io.octammo.gui.ForgeGui;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

/** One running upgrade. The outcome is decided (and everything consumed) before the animation starts. */
public final class UpgradeSession {
    public final UUID id = UUID.randomUUID();
    public final UUID playerId;
    public final String playerName;
    public final ForgeGui gui;
    public final UpgradeRecipe recipe;
    public final boolean success;
    public final boolean protectionUsed;
    public final double chance;
    public final List<ItemStack> rewards;
    public final ItemStack output;
    public final String inputKey;
    public final long startedAt = System.currentTimeMillis();

    public AnimConfig cfg;
    public BukkitTask task;
    public int elapsed;
    public int percent;
    public int lastSoundPercent;
    public boolean persisted;
    public boolean done;

    public UpgradeSession(UUID playerId, String playerName, ForgeGui gui, UpgradeRecipe recipe, boolean success,
                          boolean protectionUsed, double chance, List<ItemStack> rewards, ItemStack output, AnimConfig cfg) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.gui = gui;
        this.recipe = recipe;
        this.success = success;
        this.protectionUsed = protectionUsed;
        this.chance = chance;
        this.rewards = rewards;
        this.output = output;
        this.cfg = cfg;
        this.inputKey = recipe.input().key();
    }
}
