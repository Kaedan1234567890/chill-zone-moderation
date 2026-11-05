# Chill Zone Moderation 0.3.25 - /rules update

- Adds an actual public `/rules` command in Moderation (the previous source only *listed* `/rules` in `/help`).
- `/rules` shows six categorized sections, `/rules spawn|pvp|cheating|teams|chat|staff` shows a selected section, and `/rules all` prints all 45 rules.
- Plain ASCII labels and rule text, with bold light-purple section headings and aqua rule body text. No emoji or decorative Unicode symbols in this new command.
- Player-facing rules use `ServerPlayer.sendSystemMessage` to avoid dependence on `send_command_feedback` gamerule.
- Naked killing rule explicitly allows self-defense if the unarmoured player initiates damage; merely holding a sword is not enough. A temporary ban is possible after staff review, not automatically imposed.
- Does not modify spawn protection, the `/home` cooldown, or existing moderation punishment automation.
- This is full source only, NOT a compiled .jar. Compile through the existing repository workflow, and test with a non-OP Java and Bedrock account.
- This source is based on the user's uploaded `build(20261105-012051).zip`, whose gradle.properties identified 0.3.23. Compare changes if a newer server JAR is deployed.
