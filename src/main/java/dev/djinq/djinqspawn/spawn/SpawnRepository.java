package dev.djinq.djinqspawn.spawn;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class SpawnRepository {
    private final File file;
    private final Logger logger;

    SpawnRepository(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    Optional<StoredSpawn> load() throws IOException, InvalidConfigurationException {
        if (!file.isFile()) {
            return Optional.empty();
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        if (!yaml.getBoolean("spawn.set", false)) {
            return Optional.empty();
        }

        String worldName = yaml.getString("spawn.world.name", "");
        UUID worldId = parseWorldId(yaml.getString("spawn.world.uuid", ""));
        require(yaml, "spawn.location.x");
        require(yaml, "spawn.location.y");
        require(yaml, "spawn.location.z");
        require(yaml, "spawn.location.yaw");
        require(yaml, "spawn.location.pitch");

        return Optional.of(new StoredSpawn(
                worldId,
                worldName,
                yaml.getDouble("spawn.location.x"),
                yaml.getDouble("spawn.location.y"),
                yaml.getDouble("spawn.location.z"),
                (float) yaml.getDouble("spawn.location.yaw"),
                (float) yaml.getDouble("spawn.location.pitch")));
    }

    void save(StoredSpawn spawn) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawn.set", true);
        yaml.set("spawn.world.name", spawn.worldName());
        yaml.set("spawn.world.uuid", spawn.worldId().toString());
        yaml.set("spawn.location.x", spawn.x());
        yaml.set("spawn.location.y", spawn.y());
        yaml.set("spawn.location.z", spawn.z());
        yaml.set("spawn.location.yaw", spawn.yaw());
        yaml.set("spawn.location.pitch", spawn.pitch());
        saveAtomically(yaml);
    }

    void delete() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawn.set", false);
        saveAtomically(yaml);
    }

    private UUID parseWorldId(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(serialized);
        } catch (IllegalArgumentException exception) {
            logger.warning("spawn.yml has an invalid world UUID. Its data was retained and world-name resolution will be attempted.");
            return null;
        }
    }

    private static void require(YamlConfiguration yaml, String path) throws InvalidConfigurationException {
        if (!yaml.contains(path) || !(yaml.get(path) instanceof Number)) {
            throw new InvalidConfigurationException(path + " is missing or is not numeric");
        }
    }

    private void saveAtomically(YamlConfiguration yaml) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create the plugin data directory");
        }

        File temporary = File.createTempFile("spawn-", ".yml.tmp", parent);
        try {
            yaml.save(temporary);
            try {
                Files.move(
                        temporary.toPath(),
                        file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    record StoredSpawn(
            UUID worldId,
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch) {
    }
}

