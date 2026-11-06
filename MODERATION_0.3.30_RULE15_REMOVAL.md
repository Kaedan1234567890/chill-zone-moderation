# Chill Zone Moderation 0.3.30 — Rule 15 removal

Built from the prior 0.3.29 Build Fix 1 source, with no changes to SUS, punishments, spawn, server-state, or existing functionality.

- Removed the original rule 15, "No Automated Gameplay", from the Cheating section.
- All later rules are automatically renumbered by the existing dynamic numbering logic.
- Current total: 60 rules.
- New category ranges: Chat 1–9, Cheating 10–18, PvP 19–29, Spawn 30–37, Staff 38–48, Teams 49–60.
- The existing `/rules` overview, category hover, `/rules <number>`, `/rules all`, and enforcement text continue using the live rule count.

Source-only delivery. Full Fabric 26.3 Gradle compilation and gameplay tests must be run in the existing Java 25 GitHub Actions build. Do not install the source ZIP in mods/.
