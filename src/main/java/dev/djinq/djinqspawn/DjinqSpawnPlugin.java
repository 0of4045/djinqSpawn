package dev.djinq.djinqspawn;

import dev.djinq.djinqspawn.command.SpawnCommandHandler;
import dev.djinq.djinqspawn.config.MessageService;
import dev.djinq.djinqspawn.spawn.SpawnListener;
import dev.djinq.djinqspawn.spawn.SpawnService;
import dev.djinq.djinqspawn.spawn.SpawnTeleportManager;
import dev.djinq.djinqspawn.spawn.SpawnTeleportSettings;
import dev.djinq.djinqspawn.text.TextRenderer;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DjinqSpawnPlugin extends JavaPlugin {
    private MessageService messages;
    private SpawnService spawnService;
    private SpawnTeleportManager spawnTeleports;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            saveResourceIfAbsent("messages.yml");

            SpawnTeleportSettings teleportSettings = loadTeleportSettings();
            messages = new MessageService(new File(getDataFolder(), "messages.yml"));
            spawnService = new SpawnService(this);
            spawnTeleports = new SpawnTeleportManager(this, spawnService, messages, teleportSettings);

            registerCommands();
            Bukkit.getPluginManager().registerEvents(new SpawnListener(this), this);
            Bukkit.getConsoleSender().sendMessage(TextRenderer.render(
                    "<#B3E342>djinqSpawn <#CACACA>made by <#B3E342>djinq "
                            + "<#CACACA>| <#97DD25>djinq.dev <#CACACA>| <#B3E342>Loaded"));
        } catch (Exception exception) {
            getLogger().severe("Could not enable djinqSpawn: " + describe(exception));
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (spawnTeleports != null) {
            spawnTeleports.shutdown();
        }
    }

    public void reloadPlugin(CommandSender sender) {
        try {
            SpawnTeleportSettings teleportSettings = loadTeleportSettings();
            messages.reload(new File(getDataFolder(), "messages.yml"));
            spawnTeleports.reload(teleportSettings);
            messages.send(sender, "reload-success", Map.of());
        } catch (Exception exception) {
            getLogger().warning("Configuration reload failed; previous settings remain active: " + describe(exception));
            if (messages != null) {
                messages.send(sender, "reload-failed", Map.of("reason", describe(exception)));
            }
        }
    }

    private void registerCommands() {
        SpawnCommandHandler commands = new SpawnCommandHandler(this, messages);
        Objects.requireNonNull(getCommand("spawn")).setExecutor(commands);
        Objects.requireNonNull(getCommand("setspawn")).setExecutor(commands);
        Objects.requireNonNull(getCommand("delspawn")).setExecutor(commands);
        Objects.requireNonNull(getCommand("djinqspawn")).setExecutor(commands);
    }

    private void saveResourceIfAbsent(String name) {
        File target = new File(getDataFolder(), name);
        if (!target.isFile()) {
            saveResource(name, false);
        }
    }

    private SpawnTeleportSettings loadTeleportSettings() throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File(getDataFolder(), "config.yml"));
        return SpawnTeleportSettings.from(config);
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public SpawnService spawnService() {
        return spawnService;
    }

    public SpawnTeleportManager spawnTeleports() {
        return spawnTeleports;
    }

}
