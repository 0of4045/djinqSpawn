package dev.djinq.djinqspawn.spawn;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;

import dev.djinq.djinqspawn.DjinqSpawnPlugin;

public final class SpawnListener implements Listener {
    private final DjinqSpawnPlugin plugin;

    public SpawnListener(DjinqSpawnPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            plugin.spawnTeleports().teleportFirstJoin(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Object identity = plugin.spawnTeleports().activeTeleportIdentity(playerId);
        if (identity == null || plugin.spawnTeleports().isCompletingTeleport(playerId)) {
            return;
        }
        plugin.spawnTeleports().checkAfterEvent(playerId, () -> {
            if (!event.isCancelled() && plugin.spawnTeleports().activeTeleportIdentity(playerId) == identity) {
                plugin.spawnTeleports().cancelForMove(event.getPlayer(), event.getTo());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Object identity = plugin.spawnTeleports().activeTeleportIdentity(player.getUniqueId());
        if (identity == null) {
            return;
        }
        plugin.spawnTeleports().checkAfterEvent(player.getUniqueId(), () -> {
            if (!event.isCancelled() && event.getFinalDamage() > 0.0d
                    && plugin.spawnTeleports().activeTeleportIdentity(player.getUniqueId()) == identity) {
                plugin.spawnTeleports().cancelForDamage(player);
            }
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!plugin.spawnTeleports().isCompletingTeleport(event.getPlayer().getUniqueId())
                && plugin.spawnTeleports().hasActiveTeleport(event.getPlayer().getUniqueId())) {
            plugin.spawnTeleports().cancelForWorldChange(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.spawnTeleports().cancelForDeath(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }

        Location fallback = plugin.spawnService().destination();
        if (fallback != null) {
            event.setRespawnLocation(fallback);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.spawnTeleports().cancelForQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.spawnService().worldLoaded(event.getWorld());
    }
}
