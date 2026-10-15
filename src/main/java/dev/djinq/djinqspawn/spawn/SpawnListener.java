package dev.djinq.djinqspawn.spawn;

import dev.djinq.djinqspawn.DjinqSpawnPlugin;
import java.util.UUID;
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

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!plugin.spawnTeleports().hasActiveTeleport(playerId)) {
            return;
        }
        plugin.spawnTeleports().cancelForMove(event.getPlayer(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0.0d) {
            return;
        }
        if (!plugin.spawnTeleports().hasActiveTeleport(player.getUniqueId())) {
            return;
        }
        plugin.spawnTeleports().cancelForDamage(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (plugin.spawnTeleports().hasActiveTeleport(event.getPlayer().getUniqueId())) {
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

        org.bukkit.Location fallback = plugin.spawnService().destination();
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
