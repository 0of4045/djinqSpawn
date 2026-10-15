package dev.djinq.djinqspawn.config;

import dev.djinq.djinqspawn.text.TextRenderer;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MessageService {
    private static final String PREFIX_KEY = "\u0000prefix";
    private static final Set<String> REQUIRED = Set.of(
            "no-permission", "player-only", "spawn-not-set", "spawn-unavailable",
            "teleport-countdown", "teleport-success", "teleport-failed",
            "cancelled-move", "cancelled-damage", "cancelled-world", "already-teleporting",
            "set-success", "delete-success", "save-failed", "reload-success", "reload-failed",
            "usage-spawn", "usage-setspawn", "usage-delspawn", "usage-admin");

    private volatile Map<String, String> configuration;

    public MessageService(File file) throws IOException, InvalidConfigurationException {
        reload(file);
    }

    public void reload(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        Object prefixValue = yaml.get("prefix");
        if (!(prefixValue instanceof String prefix)) {
            throw new InvalidConfigurationException("messages.yml must contain a string prefix");
        }
        TextRenderer.render(prefix);

        ConfigurationSection section = yaml.getConfigurationSection("messages");
        if (section == null) {
            throw new InvalidConfigurationException("messages.yml must contain a messages section");
        }

        Map<String, String> loaded = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (!(value instanceof String text)) {
                throw new InvalidConfigurationException("messages." + key + " must be a string");
            }
            TextRenderer.render(text);
            loaded.put(key, text);
        }
        for (String required : REQUIRED) {
            if (!loaded.containsKey(required)) {
                throw new InvalidConfigurationException("messages." + required + " is missing");
            }
        }
        loaded.put(PREFIX_KEY, prefix);
        configuration = Collections.unmodifiableMap(loaded);
    }

    public void send(CommandSender recipient, String key, Map<String, String> values) {
        Map<String, String> current = configuration;
        String template = current.getOrDefault(key, "<red>Missing message: " + key);
        recipient.sendMessage(TextRenderer.render(current.get(PREFIX_KEY) + template, values));
    }

    public Component render(String key, Map<String, String> values) {
        String template = configuration.getOrDefault(key, "<red>Missing message: " + key);
        return TextRenderer.render(template, values);
    }
}
