# Chill Zone Moderation 0.3.11 — Saved Evidence confirmation

- Saved SUS case detail: bottom-right barrier opens a 3-row delete confirmation GUI.
- Red wool cancels, green wool deletes only the saved snapshot.
- Live SUS activity is preserved. Deletion is atomic when supported, with fallback and rollback on failure.
- No changes to current detection, dimension locking, `/susback`, or grace logic.

## Deliberately NOT implemented in this build
The uploaded `.github(2).zip` has empty Homes source directories. It contains `ShardBridge`, which proves that Homes owns the Shards store, but no Homes store API or playtime implementation. Modifying unrelated shard data files would be unsafe, particularly for offline users. `/server preparing` therefore does **not** reset Shards or playtime in this version. Existing `/server start` resets the day time (`time set 0`) but does not rewind world age/game ticks. Resetting a season requires an explicit backup and a compatible Homes implementation.

Build and server startup are UNVERIFIED.
