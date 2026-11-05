# Chill Zone Moderation 0.3.28 — Rules command integration + presets

Source based directly on 0.3.27. The entire 0.3.27 shared /ban and /tempban preset catalogue and hover durations remain.

## Rules
- Carries all 68 numbered Season 2 rules in `ServerRules.java`.
- `/rules` / `/rules all` and six category commands are registered at command setup and reinforced on server start.
- **NEW diagnostic fallback:** `/czrules`, `/czrules all`, `/czrules spawn|pvp|cheating|teams|chat|staff` show this mod's rules even if another mod owns /rules.
- Titles are bold purple, text aqua, without emoji; output uses player.sendSystemMessage rather than command feedback.
- `/rules` overview starts with `CHILL ZONE SMP - SERVER RULES (68 RULES)` to identify the new handler.

## Important conflict diagnosis
The screenshot showing `CHILL ZONE RULES` and only 10 rules is **not** the content of this mod's `ServerRules.java`. If `/czrules` works but `/rules` stays old, another mod owns /rules and must be identified. Search the server mods or config for the old 10 rules / title; remove the competing registration before assuming `/rules` is fixed. If neither works, confirm GitHub Actions actually compiled 0.3.28 and the running server uses its JAR. Do not put multiple Chill Zone Moderation JAR versions in mods/.

## No changes
- No changes to spawn protection, home cooldowns, other moderation, SUS, /help, team systems or gameplay.
- No automatic bans added. Punishment presets remain as in 0.3.27.

## Status
Not built or tested in a running Minecraft 26.3 server here. This ZIP is SOURCE, not a deployable JAR. Build with GitHub Actions and test both `/czrules` and `/rules`.
