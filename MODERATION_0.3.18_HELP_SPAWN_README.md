# Moderation 0.3.18 — /help and /spawn source candidate

Based on the user-provided 0.3.17 ZIP. Prior source has **no custom /help command**; this adds one with chat sections: general, teleportation, moderation, admin and owner. Existing moderation commands are retained.

New `/spawn` teleports after a one-second delay to a saved Overworld location; `/spawn set` or `/spawn set create` saves your current location; `/spawn set <x> <y> <z>` saves coordinates. Only LuckPerms node `chillzonemoderation.spawnset` (assign to Owner group) or console may set spawn. Location saved to `config/chill-zone-spawn.json`.

No new protection radius, PvP invulnerability, or combat-tag detection is included. The installed Lifesteal mod must intercept `/spawn` during combat, including the possibility of a combat tag acquired during the delay. This is not a verified integration.

This source is not compiled or server-tested; it may conflict with another registered `/help` or `/spawn` command. The Moderation source did not include a current /help implementation, so the older GUI described by the user is NOT reproduced here. Back up the existing JAR/config before testing. Staff access follows existing LuckPerms nodes; /vanish is documented but belongs to another mod. The help command is informational and does not itself grant permissions.
