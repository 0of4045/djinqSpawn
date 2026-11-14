package dev.djinq.djinqspawn.command;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import dev.djinq.djinqspawn.DjinqSpawnPlugin;
import dev.djinq.djinqspawn.config.MessageService;

public final class SpawnCommandHandler implements CommandExecutor, TabCompleter {
    private final DjinqSpawnPlugin plugin;
    private final MessageService messages;

    public SpawnCommandHandler(DjinqSpawnPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawn(sender, args);
            case "setspawn" -> setSpawn(sender, args);
            case "delspawn" -> deleteSpawn(sender, args);
            case "djinqspawn" -> admin(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("djinqspawn") && args.length == 1
                && sender.hasPermission("djinqspawn.admin.reload")
                && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return List.of();
    }

    private boolean spawn(CommandSender sender, String[] args) {
        if (args.length != 0) {
            messages.send(sender, "usage-spawn", Map.of());
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only", Map.of());
            return true;
        }
        if (!player.hasPermission("djinqspawn.spawn.use")) {
            messages.send(player, "no-permission", Map.of());
            return true;
        }

        plugin.spawnTeleports().request(player);
        return true;
    }

    private boolean setSpawn(CommandSender sender, String[] args) {
        if (args.length != 0) {
            messages.send(sender, "usage-setspawn", Map.of());
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only", Map.of());
            return true;
        }
        if (!player.hasPermission("djinqspawn.spawn.set")) {
            messages.send(player, "no-permission", Map.of());
            return true;
        }

        try {
            plugin.spawnService().setSpawn(player.getLocation());
            messages.send(player, "set-success", Map.of());
        } catch (IOException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not save Spawn: " + exception.getMessage());
            messages.send(player, "save-failed", Map.of());
        }
        return true;
    }

    private boolean deleteSpawn(CommandSender sender, String[] args) {
        if (args.length != 0) {
            messages.send(sender, "usage-delspawn", Map.of());
            return true;
        }
        if (!sender.hasPermission("djinqspawn.spawn.delete")) {
            messages.send(sender, "no-permission", Map.of());
            return true;
        }

        try {
            if (!plugin.spawnService().deleteSpawn()) {
                messages.send(sender, "spawn-not-set", Map.of());
                return true;
            }
            plugin.spawnTeleports().spawnDeleted();
            messages.send(sender, "delete-success", Map.of());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not delete Spawn: " + exception.getMessage());
            messages.send(sender, "save-failed", Map.of());
        }
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            messages.send(sender, "usage-admin", Map.of());
            return true;
        }
        if (!sender.hasPermission("djinqspawn.admin.reload")) {
            messages.send(sender, "no-permission", Map.of());
            return true;
        }

        plugin.reloadPlugin(sender);
        return true;
    }
}
