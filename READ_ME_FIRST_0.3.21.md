# Chill Zone Moderation 0.3.21 — /help command override

This is full GitHub source, not a compiled JAR. Changes from 0.3.20:

- **Actual code change:** re-registers expanded `/help` command on the LIVE server dispatcher on SERVER_STARTED, after command registrations, rather than assuming Moderation wins registration order.
- `/help`, `/help general`, `/help teleportation`, `/help lifesteal`, `/help pvp`, `/help moderation`, `/help admin`, `/help owner` all route to the expanded Season 2 lists (staff sections require matching permissions).
- `/czhelp` remains a diagnostic alias.
- Version and displayed heading changed to 0.3.21 UPDATED COMMANDS.
- No Lifesteal, Homes, spawn, permissions, SUS or other Moderation behaviours intentionally changed.
- Retains Moderation's existing chat formatting. **This does NOT modify a separate mod's purple/blue help GUI textures**. An existing conflicting `/help` command could still override at reload time, or have its own client command path.

### Verify
1. Remove all previous `chill-zone-moderation` JARs; install only the newly compiled 0.3.21.
2. Watch logs for `[Chill Zone Moderation 0.3.21] Registered expanded /help command list on live dispatcher`.
3. Run `/help`, `/help lifesteal`, `/help teleportation`, `/czhelp`. The first line should show `HELP 0.3.21 — UPDATED COMMANDS`.
4. If `/help` still opens a separate GUI, identify that mod (list installed JAR names or send its source). The existing GUI cannot be updated faithfully without the mod owning it.

This source has not been compiled or tested in a Minecraft server.
