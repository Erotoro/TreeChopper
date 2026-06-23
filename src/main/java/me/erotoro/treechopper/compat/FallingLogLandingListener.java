package me.erotoro.treechopper.compat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Fallback that stops the plugin's cosmetic falling logs from reforming into blocks on landing.
 *
 * <p>On newer servers this is handled by {@link FallingBlock#setCancelDrop(boolean)} and no listener
 * is registered — this is deliberate, because an unconditional {@code EntityChangeBlockEvent}
 * listener fires for every unrelated falling block (sand/gravel farms) and is a known source of
 * region-thread churn. This listener is therefore registered <em>only</em> on versions where
 * {@code setCancelDrop} is unavailable (~1.16–1.18), where Folia does not exist anyway.
 */
public final class FallingLogLandingListener implements Listener {

    private final Set<UUID> activeFallingBlocks;

    public FallingLogLandingListener(Set<UUID> activeFallingBlocks) {
        this.activeFallingBlocks = activeFallingBlocks;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFallingLogLand(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof FallingBlock)) {
            return;
        }
        if (activeFallingBlocks.remove(entity.getUniqueId())) {
            event.setCancelled(true);
            entity.remove();
        }
    }
}
