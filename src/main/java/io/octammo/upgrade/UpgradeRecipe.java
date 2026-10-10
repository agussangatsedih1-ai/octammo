package io.octammo.upgrade;

import io.octammo.core.ItemRef;

import java.util.List;

/**
 * One path of a station: INPUT + COST + COST => OUTPUT (chance%).
 *
 * @param chance            base success chance in percent (0-100)
 * @param animationDuration per-path override of gui-animation.duration-ticks, or null
 */
public record UpgradeRecipe(String file,
                            String key,
                            String name,
                            List<String> description,
                            ItemRef input,
                            List<Cost> costs,
                            ItemRef output,
                            double chance,
                            FailMode onFail,
                            String permission,
                            Effects onSuccess,
                            Effects onFailEffects,
                            Integer animationDuration) {

    public enum FailMode { KEEP, DESTROY }
}
