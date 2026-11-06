# Chill Zone Moderation 0.3.29 — build fix 1

Fixed the Java compilation syntax error in `src/main/java/com/chillzone/moderation/ServerRules.java` at line 126. One extra closing parenthesis was removed from the category hover-text builder.

No rules, `/sus` behaviour, command permissions, or other features were intentionally changed from the 0.3.29 source.

Build the full source in your existing GitHub Actions workflow using Java 25. This ZIP is SOURCE, not a compiled server mod JAR. The fix was checked with a local Java parser/compiler pass; full Fabric build was not possible locally due to unavailable dependencies and JDK 25.

Spark suggestions (`/sparkc`, `/sparkclient`) are separate client-side commands, not registered by the Moderation mod. See assistant explanation for client-mod vs server permission approaches.
