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


## 0.3.0-alpha-mc26.3-server-state
- Updated build target to Minecraft 26.3 / Fabric Loader 0.19.5 / Fabric API 0.165.0+26.3 / Loom 1.17.21.
- Added owner/server-control permission: `chillzonemoderation.servercontrol` (OP level 4 also allowed for testing).
- Added `/end close`, `/end open [message]`, `/nether close`, `/nether open`.
- Closed dimensions are rejected at the cross-dimension `Entity.teleport(TeleportTransition)` layer, covering players, entities, passengers, portal travel, and other dimension transitions that use the vanilla teleport pipeline.
- Closing a dimension ejects players already inside back to the Overworld spawn.
- Added `/server preparing`: Overworld border becomes 100x100.
- Added `/server start`: resets time to 0, visibly expands border to 16,000x16,000 over 60 seconds, shows title/subtitle, starts 1-hour grace.
- During grace, player-caused PvP damage is cancelled at `ServerPlayer.hurtServer`.
- Grace state persists to `config/chill-zone-moderation-server-state.json`; future RTP can read/integrate the same state.
- SUS list page 1: recent cases (30-minute window) on top row, divider row, older cases below. Later pages show older cases only.
- SUS player heads now carry unresolved UUID profile data so skins can render.
- Existing focused SUS evidence screen, X-ray/mining locations, and clear controls retained.


## 0.3.3 adjustment (unbuilt)

- Overworld border expansion changed from 60 seconds (1200 ticks) to 90 seconds (1800 ticks).
- Existing closed-dimension checks and player ejection retained unchanged. This build does NOT prevent vanilla Minecraft from initializing/loading the dimension internally. Player-entry prevention needs in-game verification across portals, vehicles, and commands.
