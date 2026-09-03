# Chill Zone Moderation 0.1.0-alpha

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
