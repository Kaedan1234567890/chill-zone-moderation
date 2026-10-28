# Moderation 0.3.16 — staff teleport implementation
Adds /tpto <online player> and /tphere <online player> in Moderation.
Permission: chillzonemoderation.staffteleport (grant to Mod, Admin and Owner; inherited via ranks if configured).
Autocomplete includes remembered names from config/chillzone-moderation/known-players.json and usercache.json; substring matching, case insensitive. Offline players are suggested but cannot be teleported until online.
**Important:** If a separate Staff TPU mod registers the same commands, remove that older Staff TPU JAR on a backed-up test instance to prevent ambiguous command conflicts. Do not delete Staff TPU data/configuration.
No server runtime test or successful Gradle build performed. Verify command registration, permission access and closed-dimension behavior in a test server.
