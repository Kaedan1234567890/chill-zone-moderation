# Chill Zone Moderation

Current development version: `0.2.11-alpha-sus-final-evidence`

Core moderation commands remain in this project, including `/czban`, `/czunban`, `/tempban`, `/untempban`, `/warn`, `/warnings`, `/mute`, `/unmute`, `/punishments`, plus `/freeze` and `/unfreeze`.

## SUS
`/sus` is a staff evidence GUI. It stores evidence by UUID, works for remembered/offline players, and keeps Fly, Speed, Elytra and X-Ray/Ore activity. Only qualifying mining/X-ray events save teleport evidence, with up to 18 locations and one saved TP per qualifying vein/event.

AntiFly 1.1.4 provides movement detections. Chill Zone intercepts the normal AntiFly movement actions so those detections are stored as observe-only activity evidence instead of automatically punishing/setbacking the player. Movement detections never create teleport locations. Staff decide enforcement manually.

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
