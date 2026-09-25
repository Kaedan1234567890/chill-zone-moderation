package com.chillzone.sus.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SusStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SAVE_EVERY_TICKS = 20 * 60;
    public static final int MAX_FLAG_LOCATIONS = 18;
    private static final String RESET_MARKER = "chill_zone_sus_evidence_reset_moderation_0_2_11.done";

    private final Map<UUID, SusRecord> records = new ConcurrentHashMap<>();
    private long ticksSinceSave;

    public static SusStore load(MinecraftServer server) {
        SusStore store = new SusStore();
        Path path = file();
        boolean oneTimeReset = !Files.exists(resetMarker());

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                List<SusRecord> loaded = GSON.fromJson(reader, new TypeToken<List<SusRecord>>(){}.getType());
                if (loaded != null) {
                    for (SusRecord r : loaded) {
                        if (r != null && r.uuid != null) {
                            normalize(r);
                            // One-time cleanup for this update: clear every old saved
                            // teleport/evidence location but keep scores, history and
                            // movement/mining counters intact.
                            if (oneTimeReset) r.flagLocations.clear();
                            store.records.put(r.uuid, r);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Chill Zone SUS] Could not load data: " + e.getMessage());
            }
        }

        if (oneTimeReset) {
            store.save(server);
            try {
                Files.createDirectories(resetMarker().getParent());
                Files.writeString(resetMarker(), "Old SUS teleport/evidence locations cleared once.\n");
                System.out.println("[Chill Zone SUS] One-time saved evidence-location reset completed.");
            } catch (Exception e) {
                System.err.println("[Chill Zone SUS] Could not write evidence reset marker: " + e.getMessage());
            }
        }

        return store;
    }

    private static void normalize(SusRecord r) {
        if (r.recentFlagTimes == null) r.recentFlagTimes = new ArrayList<>();
        if (r.fly == null) r.fly = new SusRecord.ActivityCase();
        if (r.speed == null) r.speed = new SusRecord.ActivityCase();
        if (r.elytra == null) r.elytra = new SusRecord.ActivityCase();
        if (r.diamond == null) r.diamond = new SusRecord.OreCase();
        if (r.debris == null) r.debris = new SusRecord.OreCase();
        if (r.flagLocations == null) r.flagLocations = new ArrayList<>();
        // From this version onward only ore/X-ray locations are valid teleport evidence.
        r.flagLocations.removeIf(loc -> loc == null
            || (!"diamond".equals(loc.category) && !"debris".equals(loc.category)));
        normalizeActivity(r.fly);
        normalizeActivity(r.speed);
        normalizeActivity(r.elytra);
        normalizeCase(r.diamond);
        normalizeCase(r.debris);
        while (r.flagLocations.size() > MAX_FLAG_LOCATIONS) {
            r.flagLocations.remove(r.flagLocations.size() - 1);
        }
    }

    private static void normalizeActivity(SusRecord.ActivityCase c) {
        if (c.lastReason == null) c.lastReason = "";
        if (c.lastMovementEffects == null) c.lastMovementEffects = "None";
        if (c.lastVehicle == null) c.lastVehicle = "None";
    }

    private static void normalizeCase(SusRecord.OreCase c) {
        if (c.veinTimes == null) c.veinTimes = new ArrayList<>();
        if (c.recentIntervalsMs == null) c.recentIntervalsMs = new ArrayList<>();
    }

    public synchronized void save(MinecraftServer server) {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(new ArrayList<>(records.values()), w);
            }
        } catch (Exception e) {
            System.err.println("[Chill Zone SUS] Could not save data: " + e.getMessage());
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chill_zone_sus.json");
    }

    private static Path resetMarker() {
        return FabricLoader.getInstance().getConfigDir().resolve(RESET_MARKER);
    }

    public SusRecord getOrCreate(UUID uuid, String name) {
        SusRecord r = records.computeIfAbsent(uuid, id -> new SusRecord(id, name));
        normalize(r);
        if (name != null && !name.isBlank()) r.lastKnownName = name;
        return r;
    }

    public SusRecord get(UUID uuid) {
        SusRecord r = records.get(uuid);
        if (r != null) normalize(r);
        return r;
    }

    public SusRecord findByName(String name) {
        if (name == null) return null;
        for (SusRecord r : records.values()) {
            if (r.lastKnownName != null && r.lastKnownName.equalsIgnoreCase(name)) {
                normalize(r);
                return r;
            }
        }
        return null;
    }

    public Collection<SusRecord> all() {
        for (SusRecord r : records.values()) normalize(r);
        return records.values();
    }

    public synchronized void recordAntiFly(
        ServerPlayer player,
        String category,
        String reason,
        double actual,
        double allowed,
        double horizontalBps,
        double verticalBps,
        float pitch,
        boolean nearVerticalAscent,
        boolean elytraEquipped,
        boolean elytraActive,
        boolean creative,
        boolean spectator,
        boolean onGround,
        int heightAboveGround,
        String movementEffects,
        int recentRockets,
        int recentWindCharges,
        boolean recentImpulse,
        boolean recentlyHurt,
        int nearbyBoats,
        int nearbyEntities,
        String vehicle,
        long now
    ) {
        SusRecord r = getOrCreate(player.getUUID(), player.getGameProfile().name());
        SusRecord.ActivityCase c = r.activity(category);
        c.flags++;
        c.lastFlagEpochMs = now;
        c.lastReason = reason == null ? "unknown" : reason;
        c.lastActual = actual;
        c.lastAllowed = allowed;
        c.lastHorizontalBps = horizontalBps;
        c.lastVerticalBps = verticalBps;
        c.lastPitch = pitch;
        c.lastNearVerticalAscent = nearVerticalAscent;
        c.lastElytraEquipped = elytraEquipped;
        c.lastElytraActive = elytraActive;
        c.lastCreative = creative;
        c.lastSpectator = spectator;
        c.lastOnGround = onGround;
        c.lastHeightAboveGround = heightAboveGround;
        c.lastMovementEffects = movementEffects == null || movementEffects.isBlank() ? "None" : movementEffects;
        c.lastRecentRockets = recentRockets;
        c.lastRecentWindCharges = recentWindCharges;
        c.lastRecentImpulse = recentImpulse;
        c.lastRecentlyHurt = recentlyHurt;
        c.lastNearbyBoats = nearbyBoats;
        c.lastNearbyEntities = nearbyEntities;
        c.lastVehicle = vehicle == null || vehicle.isBlank() ? "None" : vehicle;

        // Movement detections remain visible in the activity cards, but must
        // never create teleportable evidence locations.
    }

    public synchronized void recordOreEvidence(ServerPlayer player, String type, String reason,
                                               int x, int y, int z, long now) {
        SusRecord r = getOrCreate(player.getUUID(), player.getGameProfile().name());
        SusRecord.OreCase ore = r.ore(type);

        // Explicit one-TP-per-qualifying-vein lock. A new vein resets this in
        // SusDetector; further flags from the same vein cannot spam locations.
        if (ore.evidenceSavedForCurrentVein) return;

        addLocation(r, new SusRecord.FlagLocation(
            type,
            reason,
            player.level().dimension().identifier().toString(),
            x, y, z,
            now, 0.0, 0.0, player.getXRot()
        ));
        ore.evidenceSavedForCurrentVein = true;
    }

    private static void addLocation(SusRecord r, SusRecord.FlagLocation location) {
        if (r.flagLocations == null) r.flagLocations = new ArrayList<>();

        // Hard safety rule: only suspicious Diamond / Ancient Debris mining may
        // create a teleport location. Movement evidence never belongs here.
        if (!"diamond".equals(location.category) && !"debris".equals(location.category)) return;

        // Never add the exact same evidence point twice.
        for (SusRecord.FlagLocation existing : r.flagLocations) {
            if (safeEquals(existing.category, location.category)
                && safeEquals(existing.world, location.world)
                && Math.abs(existing.x - location.x) < 0.001
                && Math.abs(existing.y - location.y) < 0.001
                && Math.abs(existing.z - location.z) < 0.001) {
                return;
            }
        }

        r.flagLocations.add(0, location);
        while (r.flagLocations.size() > MAX_FLAG_LOCATIONS) {
            r.flagLocations.remove(r.flagLocations.size() - 1);
        }
    }

    private static boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public void flag(ServerPlayer p, String type, int points) {
        SusRecord r = getOrCreate(p.getUUID(), p.getGameProfile().name());
        SusRecord.OreCase c = r.ore(type);
        long now = System.currentTimeMillis();
        c.suspicionScore += points;
        c.activeFlags++;
        c.lastFlagEpochMs = now;
    }

    public void ensureScore(ServerPlayer p, String type, int minimum) {
        SusRecord r = getOrCreate(p.getUUID(), p.getGameProfile().name());
        SusRecord.OreCase c = r.ore(type);
        long now = System.currentTimeMillis();
        if (c.suspicionScore < minimum) {
            c.suspicionScore = minimum;
            c.activeFlags++;
            c.lastFlagEpochMs = now;
        }
    }

    public boolean setScore(ServerPlayer p, String type, int score) {
        SusRecord r = getOrCreate(p.getUUID(), p.getGameProfile().name());
        SusRecord.OreCase c = r.ore(type);
        int old = c.suspicionScore;
        c.suspicionScore = Math.max(0, score);
        if (c.suspicionScore > old) {
            c.activeFlags++;
            c.lastFlagEpochMs = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public void clearActive(UUID uuid, String name) {
        SusRecord r = getOrCreate(uuid, name);
        clearCase(r.diamond);
        clearCase(r.debris);
        clearActivity(r.fly);
        clearActivity(r.speed);
        clearActivity(r.elytra);
        r.flagLocations.clear();

        r.suspicionScore = 0;
        r.lastFlagEpochMs = 0;
        r.cleanActiveTicks = 0;
        r.recentFlagTimes.clear();

    }

    private static void clearActivity(SusRecord.ActivityCase c) {
        c.flags = 0;
        c.lastFlagEpochMs = 0;
        c.lastReason = "";
        c.lastActual = 0;
        c.lastAllowed = 0;
        c.lastHorizontalBps = 0;
        c.lastVerticalBps = 0;
        c.lastPitch = 0;
        c.lastNearVerticalAscent = false;
        c.lastElytraEquipped = false;
        c.lastElytraActive = false;
        c.lastCreative = false;
        c.lastSpectator = false;
        c.lastOnGround = false;
        c.lastHeightAboveGround = 0;
        c.lastMovementEffects = "None";
        c.lastRecentRockets = 0;
        c.lastRecentWindCharges = 0;
        c.lastRecentImpulse = false;
        c.lastRecentlyHurt = false;
        c.lastNearbyBoats = 0;
        c.lastNearbyEntities = 0;
        c.lastVehicle = "None";
    }

    private static void clearCase(SusRecord.OreCase c) {
        c.archivedPoints += Math.max(0, c.suspicionScore);
        c.suspicionScore = 0;
        c.activeFlags = 0;
        c.lastFlagEpochMs = 0;
        c.oreMined = 0;
        c.separateVeins = 0;
        c.veinTimes.clear();
        c.recentIntervalsMs.clear();
        c.lastVeinEpochMs = 0;
        c.currentVeinId = 0;
        c.currentVeinLastBreakMs = 0;
        c.evidenceSavedForCurrentVein = false;
        c.blocksSinceLastVein = 0;
        c.totalBlocksBetweenVeins = 0;
        c.blockGapSamples = 0;
        c.lowBlockGapVeins = 0;
        c.veryLowBlockGapVeins = 0;
        c.fastVeins = 0;
        c.caveExposedVeins = 0;
        c.tunnelLikeVeins = 0;
        c.unusualOreEvents = 0;
    }

    public void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            getOrCreate(p.getUUID(), p.getGameProfile().name()).totalActiveTicks++;
        }
        if (++ticksSinceSave >= SAVE_EVERY_TICKS) {
            save(server);
            ticksSinceSave = 0;
        }
    }
}
