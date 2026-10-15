package dev.djinq.djinqspawn.spawn;

import dev.djinq.djinqspawn.DjinqSpawnPlugin;
import dev.djinq.djinqspawn.config.MessageService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.scheduler.BukkitTask;

public final class SpawnTeleportManager {
    private static final String USE_PERMISSION = "djinqspawn.spawn.use";
    private static final String BYPASS_PERMISSION = "djinqspawn.spawn.bypass";

    private final DjinqSpawnPlugin plugin;
    private final SpawnService spawnService;
    private final MessageService messages;
    private final ConcurrentHashMap<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, InFlightTeleport> inFlight = new ConcurrentHashMap<>();

    private volatile SpawnTeleportSettings settings;
    private BukkitTask ticker;
    private boolean accepting = true;

    public SpawnTeleportManager(
            DjinqSpawnPlugin plugin,
            SpawnService spawnService,
            MessageService messages,
            SpawnTeleportSettings settings) {
        this.plugin = plugin;
        this.spawnService = spawnService;
        this.messages = messages;
        this.settings = settings;
    }

    public void reload(SpawnTeleportSettings candidate) {
        settings = candidate;
    }

    public void request(Player player) {
        if (!accepting) {
            messages.send(player, "spawn-unavailable", Map.of());
            return;
        }

        UUID playerId = player.getUniqueId();
        if (pending.containsKey(playerId) || inFlight.containsKey(playerId)) {
            messages.send(player, "already-teleporting", Map.of());
            return;
        }

        Location destination = spawnService.destination();
        if (!destinationAvailable(player, destination, true)) {
            return;
        }

        SpawnTeleportSettings current = settings;
        if (player.hasPermission(BYPASS_PERMISSION) || current.delaySeconds() == 0) {
            teleportLatest(player, true, true);
            return;
        }

        Location start = player.getLocation();
        PendingTeleport state = new PendingTeleport(
                playerId,
                start.getWorld().getUID(),
                start.getX(),
                start.getY(),
                start.getZ(),
                current.delaySeconds());

        if (pending.putIfAbsent(playerId, state) != null) {
            messages.send(player, "already-teleporting", Map.of());
            return;
        }

        ensureTicker();
    }

    public void teleportFirstJoin(Player player) {
        if (accepting && settings.firstJoinTeleport() && player.isOnline() && !player.isDead()) {
            teleportLatest(player, false, false);
        }
    }

    public boolean hasActiveTeleport(UUID playerId) {
        return pending.containsKey(playerId) || inFlight.containsKey(playerId);
    }

    public void cancelForMove(Player player, Location destination) {
        if (!settings.cancelOnMovement() || destination == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PendingTeleport pendingTeleport = pending.get(playerId);
        InFlightTeleport inFlightTeleport = inFlight.get(playerId);
        if (pendingTeleport == null && inFlightTeleport == null) {
            return;
        }

        UUID startWorldId = pendingTeleport != null ? pendingTeleport.worldId() : inFlightTeleport.worldId();
        double startX = pendingTeleport != null ? pendingTeleport.x() : inFlightTeleport.x();
        double startY = pendingTeleport != null ? pendingTeleport.y() : inFlightTeleport.y();
        double startZ = pendingTeleport != null ? pendingTeleport.z() : inFlightTeleport.z();

        if (destination.getWorld() == null || !startWorldId.equals(destination.getWorld().getUID())) {
            if (settings.cancelOnWorldChange()) {
                cancel(player, "cancelled-world", true);
            }
            return;
        }

        double dx = destination.getX() - startX;
        double dy = destination.getY() - startY;
        double dz = destination.getZ() - startZ;
        double tolerance = settings.movementTolerance();
        if ((dx * dx) + (dy * dy) + (dz * dz) > tolerance * tolerance) {
            cancel(player, "cancelled-move", true);
        }
    }

    public void cancelForDamage(Player player) {
        if (settings.cancelOnDamage()) {
            cancel(player, "cancelled-damage", true);
        }
    }

    public void cancelForWorldChange(Player player) {
        if (settings.cancelOnWorldChange()) {
            cancel(player, "cancelled-world", true);
        }
    }

    public void cancelForDeath(UUID playerId) {
        remove(playerId);
        inFlight.remove(playerId);
    }

    public void cancelForQuit(UUID playerId) {
        remove(playerId);
        inFlight.remove(playerId);
    }

    public void spawnDeleted() {
        Set<UUID> playerIds = new HashSet<>(pending.keySet());
        playerIds.addAll(inFlight.keySet());
        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                cancel(player, "spawn-not-set", false);
            } else {
                remove(playerId);
            }
        }
    }

    public void shutdown() {
        accepting = false;
        pending.clear();
        inFlight.clear();
        stopTicker();
    }

    private void ensureTicker() {
        if (ticker == null || ticker.isCancelled()) {
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
        }
    }

    private void tick() {
        if (pending.isEmpty()) {
            stopTicker();
            return;
        }

        for (PendingTeleport state : pending.values()) {
            Player player = Bukkit.getPlayer(state.playerId());
            if (player == null || !player.isOnline() || player.isDead()) {
                remove(state.playerId());
                continue;
            }

            if (state.remainingSeconds() == 0) {
                if (pending.remove(state.playerId(), state)) {
                    teleportLatest(player, true, true);
                }
                continue;
            }

            SpawnTeleportSettings current = settings;
            Component countdown = messages.render(
                    "teleport-countdown",
                    Map.of("seconds", Integer.toString(state.remainingSeconds())));
            if (current.actionBar()) {
                player.sendActionBar(countdown);
            }
            if (current.chatMessage()) {
                messages.send(player, "teleport-countdown", Map.of("seconds", Integer.toString(state.remainingSeconds())));
            }
            play(player, current.countdownSound());
            state.decrement();
        }

        if (pending.isEmpty()) {
            stopTicker();
        }
    }

    private void teleportLatest(Player player, boolean announce, boolean requirePermission) {
        if (!accepting || !player.isOnline() || player.isDead()) {
            return;
        }
        if (requirePermission && !player.hasPermission(USE_PERMISSION)) {
            if (announce) {
                messages.send(player, "no-permission", Map.of());
            }
            return;
        }

        Location destination = spawnService.destination();
        if (!destinationAvailable(player, destination, announce)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Location start = player.getLocation();
        InFlightTeleport request = new InFlightTeleport(
                playerId,
                start.getWorld().getUID(),
                start.getX(),
                start.getY(),
                start.getZ(),
                announce,
                requirePermission);
        if (inFlight.putIfAbsent(playerId, request) != null) {
            if (announce) {
                messages.send(player, "already-teleporting", Map.of());
            }
            return;
        }
        prepareDestination(player, request, destination);
    }

    private void prepareDestination(Player player, InFlightTeleport request, Location destination) {
        try {
            destination.getWorld().getChunkAtAsync(destination)
                    .whenComplete((chunk, error) -> schedulePrepared(player, request, destination, error));
        } catch (RuntimeException exception) {
            failPreparation(player, request);
        }
    }

    private void schedulePrepared(
            Player player, InFlightTeleport request, Location preparedDestination, Throwable error) {
        if (!plugin.isEnabled()) {
            inFlight.remove(request.playerId(), request);
            return;
        }
        try {
            Bukkit.getScheduler().runTask(
                    plugin, () -> completePrepared(player, request, preparedDestination, error));
        } catch (RuntimeException exception) {
            inFlight.remove(request.playerId(), request);
        }
    }

    private void completePrepared(
            Player player, InFlightTeleport request, Location preparedDestination, Throwable error) {
        if (inFlight.get(request.playerId()) != request) {
            return;
        }
        if (error != null || !accepting || !player.isOnline() || player.isDead()) {
            failPreparation(player, request);
            return;
        }
        if (request.requirePermission() && !player.hasPermission(USE_PERMISSION)) {
            inFlight.remove(request.playerId(), request);
            if (request.announce()) {
                messages.send(player, "no-permission", Map.of());
            }
            return;
        }

        Location latest = spawnService.destination();
        if (!destinationAvailable(player, latest, request.announce())) {
            inFlight.remove(request.playerId(), request);
            return;
        }

        if (!sameChunk(preparedDestination, latest)) {
            prepareDestination(player, request, latest);
            return;
        }

        boolean success;
        try {
            success = player.teleport(latest, TeleportCause.PLUGIN);
        } catch (RuntimeException exception) {
            success = false;
        }
        inFlight.remove(request.playerId(), request);

        if (!success) {
            if (request.announce()) {
                messages.send(player, "teleport-failed", Map.of());
            }
            return;
        }

        play(player, settings.teleportSound());
        if (request.announce()) {
            messages.send(player, "teleport-success", Map.of());
        }
    }

    private void failPreparation(Player player, InFlightTeleport request) {
        if (!inFlight.remove(request.playerId(), request)) {
            return;
        }
        if (request.announce() && player.isOnline()) {
            messages.send(player, "teleport-failed", Map.of());
        }
    }

    private boolean destinationAvailable(
            Player player, Location destination, boolean announce) {
        if (destination != null) {
            return true;
        }
        if (announce) {
            String key = spawnService.hasSpawn() ? "spawn-unavailable" : "spawn-not-set";
            messages.send(player, key, Map.of());
        }
        return false;
    }

    private void cancel(Player player, String messageKey, boolean playSound) {
        if (!remove(player.getUniqueId())) {
            return;
        }
        player.sendActionBar(net.kyori.adventure.text.Component.empty());
        if (playSound) {
            play(player, settings.cancelledSound());
        }
        messages.send(player, messageKey, Map.of());
    }

    private boolean remove(UUID playerId) {
        boolean removed = pending.remove(playerId) != null;
        removed |= inFlight.remove(playerId) != null;
        if (removed && pending.isEmpty()) {
            stopTicker();
        }
        return removed;
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private static boolean sameChunk(Location first, Location second) {
        return first.getWorld().getUID().equals(second.getWorld().getUID())
                && (first.getBlockX() >> 4) == (second.getBlockX() >> 4)
                && (first.getBlockZ() >> 4) == (second.getBlockZ() >> 4);
    }

    private static void play(Player player, SpawnTeleportSettings.ConfiguredSound sound) {
        if (sound.enabled()) {
            player.playSound(player.getLocation(), sound.sound(), sound.volume(), sound.pitch());
        }
    }

    private record InFlightTeleport(
            UUID playerId,
            UUID worldId,
            double x,
            double y,
            double z,
            boolean announce,
            boolean requirePermission) {
    }

    private static final class PendingTeleport {
        private final UUID playerId;
        private final UUID worldId;
        private final double x;
        private final double y;
        private final double z;
        private int remainingSeconds;

        private PendingTeleport(
                UUID playerId, UUID worldId, double x, double y, double z, int remainingSeconds) {
            this.playerId = playerId;
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.remainingSeconds = remainingSeconds;
        }

        UUID playerId() {
            return playerId;
        }

        UUID worldId() {
            return worldId;
        }

        double x() {
            return x;
        }

        double y() {
            return y;
        }

        double z() {
            return z;
        }

        int remainingSeconds() {
            return remainingSeconds;
        }

        void decrement() {
            remainingSeconds--;
        }
    }
}
