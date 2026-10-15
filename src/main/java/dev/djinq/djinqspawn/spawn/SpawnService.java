package dev.djinq.djinqspawn.spawn;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public final class SpawnService {
    private final Plugin plugin;
    private final SpawnRepository repository;
    private final AtomicBoolean unresolvedLogged = new AtomicBoolean();
    private volatile SpawnRepository.StoredSpawn stored;

    public SpawnService(Plugin plugin) throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        this.plugin = plugin;
        repository = new SpawnRepository(new File(plugin.getDataFolder(), "spawn.yml"), plugin.getLogger());
        stored = repository.load().orElse(null);
    }

    public boolean hasSpawn() {
        return stored != null;
    }

    public Location destination() {
        SpawnRepository.StoredSpawn current = stored;
        if (current == null) {
            return null;
        }
        if (!coordinatesAreFinite(current)) {
            logUnresolved(current, "the stored coordinates are invalid");
            return null;
        }

        World world = resolveWorld(current);
        if (world == null) {
            logUnresolved(current, "the configured world is not loaded");
            return null;
        }

        if (unresolvedLogged.getAndSet(false)) {
            plugin.getLogger().info("The configured Spawn world is available again; retained Spawn data resolved successfully.");
        }
        return new Location(world, current.x(), current.y(), current.z(), current.yaw(), current.pitch());
    }

    public synchronized void setSpawn(Location location) throws IOException {
        validate(location);
        World world = location.getWorld();
        SpawnRepository.StoredSpawn next = new SpawnRepository.StoredSpawn(
                world.getUID(),
                world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());

        repository.save(next);
        stored = next;
        unresolvedLogged.set(false);
    }

    public synchronized boolean deleteSpawn() throws IOException {
        if (stored == null) {
            return false;
        }
        repository.delete();
        stored = null;
        unresolvedLogged.set(false);
        return true;
    }

    public void worldLoaded(World world) {
        SpawnRepository.StoredSpawn current = stored;
        if (current == null) {
            return;
        }
        boolean matchesId = current.worldId() != null && current.worldId().equals(world.getUID());
        boolean matchesName = current.worldName() != null && current.worldName().equals(world.getName());
        if (matchesId || matchesName) {
            destination();
        }
    }

    private World resolveWorld(SpawnRepository.StoredSpawn current) {
        UUID worldId = current.worldId();
        World byId = worldId == null ? null : Bukkit.getWorld(worldId);
        if (byId != null) {
            return byId;
        }
        String worldName = current.worldName();
        return worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
    }

    private void logUnresolved(SpawnRepository.StoredSpawn current, String reason) {
        if (unresolvedLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning(
                    "Configured Spawn is unresolved because " + reason + " (world='" + current.worldName()
                            + "'). spawn.yml was retained unchanged.");
        }
    }

    private static boolean coordinatesAreFinite(SpawnRepository.StoredSpawn spawn) {
        return Double.isFinite(spawn.x())
                && Double.isFinite(spawn.y())
                && Double.isFinite(spawn.z())
                && Float.isFinite(spawn.yaw())
                && Float.isFinite(spawn.pitch());
    }

    private static void validate(Location location) {
        if (location == null
                || location.getWorld() == null
                || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ())
                || !Float.isFinite(location.getYaw())
                || !Float.isFinite(location.getPitch())) {
            throw new IllegalArgumentException("The current location cannot be used as Spawn");
        }
    }
}

