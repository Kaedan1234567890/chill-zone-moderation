# Chill Zone Moderation 0.3.17 — Punishment preset update

Compared with Moderation 0.3.16 source (`CHANGELOG_0.3.15.zip`).

| Preset | Previous | New |
|---|---|---|
| Duping / Item Exploits | 14 days | 2 days |
| Event Disruption | 5 days | 5 hours |
| Harassment | 1 day | 1 hour |
| Severe Exploiting | 14 days | 14 days (unchanged per final choice) |
| Ban Evasion | 14 days | unchanged |
| Hacking | 7-day base, +5 days per subtype | unchanged |
| X-Ray | 3 days | unchanged |
| Major Griefing | 2 days | unchanged |
| Minor Griefing | 12 hours | unchanged |
| Spawn Killing | 5 hours | unchanged |
| Other | 1 minute to 30 years | unchanged |

**Important:** Staff must manually remove duplicated items. No automatic inventory wipe was added. Permanent `/ban` and hacking subtype choices remain unchanged. All other source files were preserved from the supplied project.

## Build
Upload this ZIP's *contents* to the repository root; GitHub Actions uses Java 25 and Gradle 9.5.1. The artifact path now matches version 0.3.17.

**Validation:** source/preset checks performed; compilation and in-game testing not performed here.
