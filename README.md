# Chill Zone Moderation

Current development version: `0.2.8-alpha-antifly-evidence-sus`

Core moderation commands remain in this project, including `/czban`, `/czunban`, `/tempban`, `/untempban`, `/warn`, `/warnings`, `/mute`, `/unmute`, `/punishments`, plus `/freeze` and `/unfreeze`.

## SUS
`/sus` is a staff evidence GUI. It stores evidence by UUID, works for remembered/offline players, and keeps Fly, Speed, Elytra and X-Ray/Ore activity plus up to 14 teleportable evidence locations.

AntiFly 1.1.4 provides movement detections. Chill Zone intercepts the normal AntiFly movement actions so those detections are stored as evidence instead of automatically punishing/setbacking the player. Staff decide enforcement manually.

## Files
Moderation config: `config/chill-zone-moderation.json`
Moderation data: `config/chill-zone-moderation-data.json`
SUS evidence: `config/chill_zone_sus.json`

## Build
GitHub Actions uses Java 25 and Gradle 9.5.1.


## 0.2.9 Grim cleanup
This build contains NO GrimAC API dependency.
A legacy no-op GrimSusBridge.java is included only to overwrite a stale 0.2.7 file
when this source is uploaded over an existing GitHub repository.
