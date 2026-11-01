# Chill Zone Moderation 0.3.24 — Help chat formatting

Based on the complete 0.3.23 help-layout source. This update changes only the help text renderer in `SpawnAndHelp.java`.

- Each command remains on its own line.
- Command syntax is **aqua/blue bold**; the dash and explanation are **plain gray**.
- Category headers are **bold light purple** and informational notes are **gray**.
- `/help` still shows all available categories subject to existing staff permission checks; `/help <category>` and `/czhelp <category>` still work.
- All other commands, spawn teleport behavior, permissions and other systems are untouched.

This is source code, not a compiled mod JAR. Build using your usual Gradle / GitHub Actions workflow before deploying. In-game testing has not been performed.
