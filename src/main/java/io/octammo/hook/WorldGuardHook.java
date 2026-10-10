package io.octammo.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class WorldGuardHook {
    /** Lowercase ids of the regions at a location. */
    public Set<String> regionsAt(Location loc) {
        Set<String> ids = new HashSet<>();
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        for (ProtectedRegion r : set.getRegions()) ids.add(r.getId().toLowerCase(Locale.ROOT));
        return ids;
    }
}
