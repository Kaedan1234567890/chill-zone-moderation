# Chill Zone Moderation 0.3.27 - Shared Ban and Tempban Presets

Based on 0.3.26 source with the 68 rules. ServerRules.java and /rules registration are retained without changes.

- Shared preset IDs for /ban, /czban and /tempban.
- /tempban suggestion tooltips show fixed ban durations.
- /ban suggestion tooltips identify the offence as permanent.
- Spawn killing tempban defaults to 8 hours. Naked killing 8 hours.
- Expanded general and hacking offence categories.
- /tempban <player> hacking <hack1,hack2> remains supported for older usage, but takes the longest listed duration, not additive stacking.
- /tempban <player> other <duration> <reason> remains supported (1m-30y).
- /ban <player> other <reason> and previous /ban <player> <custom reason> remain supported.
- Existing saved punishment records and permissions are unchanged.
- No changes to SUS, spawn protection, /home, or automatic ban enforcement.

Source only, not compiled or tested with Minecraft 26.3. Upload complete source to GitHub and run Actions Build; back up the working server and data before installation.
