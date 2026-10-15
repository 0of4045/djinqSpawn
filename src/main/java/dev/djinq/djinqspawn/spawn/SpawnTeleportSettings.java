package dev.djinq.djinqspawn.spawn;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;

public record SpawnTeleportSettings(
        int delaySeconds,
        boolean cancelOnMovement,
        boolean cancelOnDamage,
        boolean cancelOnWorldChange,
        double movementTolerance,
        boolean actionBar,
        boolean chatMessage,
        boolean firstJoinTeleport,
        ConfiguredSound countdownSound,
        ConfiguredSound teleportSound,
        ConfiguredSound cancelledSound) {

    public static SpawnTeleportSettings from(YamlConfiguration config) {
        return new SpawnTeleportSettings(
                integer(config, "teleport.delay-seconds", 5, 0, 3600),
                bool(config, "teleport.cancel-on.movement", true),
                bool(config, "teleport.cancel-on.damage", true),
                bool(config, "teleport.cancel-on.world-change", true),
                decimal(config, "teleport.movement-tolerance", 0.05d, 0.0d, 10.0d),
                bool(config, "teleport.countdown.action-bar", true),
                bool(config, "teleport.countdown.chat-message", true),
                bool(config, "first-join.teleport-to-spawn", true),
                sound(config, "sounds.countdown", "BLOCK_NOTE_BLOCK_HAT", 0.6f, 1.2f),
                sound(config, "sounds.teleport", "ENTITY_ENDERMAN_TELEPORT", 0.7f, 1.2f),
                sound(config, "sounds.cancelled", "BLOCK_NOTE_BLOCK_BASS", 0.5f, 0.8f));
    }

    private static ConfiguredSound sound(
            YamlConfiguration config, String path, String fallbackName, float fallbackVolume, float fallbackPitch) {
        boolean enabled = bool(config, path + ".enabled", true);
        String name = string(config, path + ".name", fallbackName);
        Sound sound = findSound(name);
        if (sound == null) {
            throw new IllegalArgumentException(path + ".name contains an unknown sound: " + name);
        }
        float volume = (float) decimal(config, path + ".volume", fallbackVolume, 0.0d, Float.MAX_VALUE);
        float pitch = (float) decimal(config, path + ".pitch", fallbackPitch, 0.0d, 2.0d);
        return new ConfiguredSound(enabled, sound, volume, pitch);
    }

    private static Sound findSound(String configuredName) {
        String normalized = configuredName.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        Sound sound = key == null ? null : Registry.SOUNDS.get(key);
        if (sound != null || normalized.contains(".")) {
            return sound;
        }

        int separator = normalized.indexOf(':');
        String namespace = separator < 0 ? NamespacedKey.MINECRAFT : normalized.substring(0, separator);
        String path = separator < 0 ? normalized : normalized.substring(separator + 1);
        NamespacedKey dottedKey = NamespacedKey.fromString(namespace + ":" + path.replace('_', '.'));
        return dottedKey == null ? null : Registry.SOUNDS.get(dottedKey);
    }

    private static boolean bool(YamlConfiguration config, String path, boolean fallback) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean result)) {
            throw new IllegalArgumentException(path + " must be true or false");
        }
        return result;
    }

    private static int integer(YamlConfiguration config, String path, int fallback, int minimum, int maximum) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a whole number");
        }
        double decimal = number.doubleValue();
        int result = number.intValue();
        if (!Double.isFinite(decimal) || decimal != result || result < minimum || result > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return result;
    }

    private static double decimal(YamlConfiguration config, String path, double fallback, double minimum, double maximum) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a number");
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result) || result < minimum || result > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return result;
    }

    private static String string(YamlConfiguration config, String path, String fallback) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String result) || result.isBlank()) {
            throw new IllegalArgumentException(path + " must be a non-empty string");
        }
        return result;
    }

    public record ConfiguredSound(boolean enabled, Sound sound, float volume, float pitch) {
    }
}
