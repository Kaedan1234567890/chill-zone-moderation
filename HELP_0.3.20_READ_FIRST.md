# Help 0.3.20 – diagnose which mod owns /help

**Changed only SpawnAndHelp.java help registration/text, version, artifact name.** Spawn logic and Moderation remain unchanged.

1. Compile using GitHub Actions, replace old Moderation JAR (do not keep two versions), restart.
2. Run `/czhelp`. Its heading MUST say `HELP 0.3.20`, with purple heading and aqua command descriptions.
3. Run `/help`. If that still opens the old menu, another mod owns `/help`, so THIS MOD CANNOT UPDATE THAT EXISTING MENU. Identify the owning mod and supply its source/JAR to change its contents and preserve its colours.
4. Help entries describe known/planned commands, not a runtime-discovered command registry. Verify any commands from other mods in-game.

This source ZIP is not compiled or server-tested.
