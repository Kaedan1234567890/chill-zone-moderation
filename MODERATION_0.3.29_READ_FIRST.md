# Chill Zone Moderation 0.3.29 source update

Based on the actual 0.3.28 source ZIP, targeting Fabric Minecraft 26.3.

## Rules
- Reviewed categories in order: chat, cheating, pvp, spawn, staff, team, same order in overview and command suggestions.
- Compact aqua rule names followed by gray explanations, dynamically numbered.
- /rules <number> (and /czrules <number>) shows one rule with enforcement; numeric IDs are intentionally not autocomplete suggestions.
- Hover overview category label for exact first/last rule numbers.
- Enforcement appears on overview, each section, all, and individual rule.
- Normal commands, pearls, portals, dimensions allowed by written PvP rules; other mods may still block them mechanically.
- Only rail, carpet, string dupers expressly allowed, subject to lag rule.
- Spawn camping guideline is ~90 seconds, with mutual fight / naked exceptions.
- Combat crash claims evaluated with evidence, with footage strongly preferred and no guaranteed item restoration.

## SUS
- Removed 30-minute automatic expiration of Recent flags.
- Opening and closing details does not mark a case viewed.
- Mark Viewed button only when the case has newer activity than the last review; returns only when newly flagged.
- First page 18 recent + 18 older; subsequent pages 45 older/overflow with prev/next arrows on bottom.
- More than 18 simultaneous unreviewed cases can overflow onto later pages and are still labelled RECENT.

## Validation
- Source edits and structural checks performed. THIS IS SOURCE CODE, NOT A TESTED JAR.
- A proper Java 25 / Gradle 9.5.1 build and in-game testing are required.
- Always back up config/chill_zone_sus.json before installing a new moderation JAR.
