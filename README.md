# Chill Zone Moderation 0.1.1-alpha

Commands:
- `/warn <player> <reason>`
- `/warnings <player>`
- `/tempban <player> <duration> <reason>`
- `/czban <player> <reason>`
- `/czunban <player>`

Durations:
- `30m`
- `2h`
- `2d`
- `1w`

Config file generated automatically:
`config/chill-zone-moderation.json`

Data file:
`config/chill-zone-moderation-data.json`

LuckPerms:
```text
/lp group owner permission set chillzonemoderation.warn true
/lp group owner permission set chillzonemoderation.warnings true
/lp group owner permission set chillzonemoderation.tempban true
/lp group owner permission set chillzonemoderation.ban true
/lp group owner permission set chillzonemoderation.unban true

/lp group admin permission set chillzonemoderation.warn true
/lp group admin permission set chillzonemoderation.warnings true
/lp group admin permission set chillzonemoderation.tempban true
/lp group admin permission set chillzonemoderation.ban false
/lp group admin permission set chillzonemoderation.unban false

/lp group mod permission set chillzonemoderation.warn false
/lp group member permission set chillzonemoderation.warn false
```

Note:
This alpha uses `/czban` and `/czunban` rather than overriding vanilla `/ban` and `/pardon`, to avoid command conflicts while testing.

## 0.1.1-alpha
- Clickable Discord appeal URL on Java clients when supported.
- URL remains visible as fallback.
- Warn, temp-ban, permanent-ban, unban, and warning-history commands now work on known offline players.
- Offline targets must have joined Chill Zone SMP at least once so the server knows their UUID/profile.
- Offline bans take effect the next time the player joins.

## 0.1.2-alpha
- Adds `/discord` for every player.
- `/discord` posts the configured Discord invite in that player's chat.
- On Java, the URL uses Minecraft's normal OPEN_URL click event, so clicking it can show Minecraft's trusted-link/browser confirmation and then open the invite.
- The invite still comes from `config/chill-zone-moderation.json`; no Discord URL is hard-coded.
