# djinqSpawn

A small and lightweight `/spawn` plugin for Paper servers.

Set a global spawn at your exact location, use a configurable teleport delay, and cancel teleporting on movement, damage, or world changes. Messages, prefix, and sounds are all easy to edit.

Made by djinq ~ [djinq.dev](https://djinq.dev)

## Installation

1. Drop `djinqSpawn.jar` into your server's `plugins` folder.
2. Start the server once.
3. Edit `plugins/djinqSpawn/config.yml` and `messages.yml` however you want.
4. Run `/djinqspawn reload` or restart the server.

## Commands

| Command | Description |
| --- | --- |
| `/spawn` | Teleports the player to spawn. |
| `/setspawn` | Sets the global spawn to your current location. |
| `/delspawn` | Deletes the current spawn. |
| `/djinqspawn reload` | Reloads the configuration and messages. |

## Permissions

| Permission | Description |
| --- | --- |
| `djinqspawn.spawn.use` | Allows `/spawn`. |
| `djinqspawn.spawn.set` | Allows `/setspawn`. |
| `djinqspawn.spawn.delete` | Allows `/delspawn`. |
| `djinqspawn.spawn.bypass` | Skips the spawn teleport delay. |
| `djinqspawn.admin.reload` | Allows `/djinqspawn reload`. |
| `djinqspawn.*` | Gives every djinqSpawn permission. |

## Configuration

In `config.yml`, you can edit:

- `teleport.delay-seconds` ~ the delay before teleporting.
- `teleport.cancel-on` ~ whether movement, damage, or world changes cancel a teleport.
- `teleport.countdown` ~ actionbar and chat countdown settings.
- `sounds` ~ countdown, teleport, and cancel sounds.

`messages.yml` contains one `prefix` setting. It is added automatically to every chat message, while the actionbar countdown stays clean without a prefix.

Built for Paper 1.21.11 through 26.3. Use Java 21 for 1.21.11, or Java 25 for Paper 26.1+.
