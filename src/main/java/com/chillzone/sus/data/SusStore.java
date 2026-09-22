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
    private static final int MAX_FLAG_LOCATIONS = 14;

    private final Map<UUID, SusRecord> records = new ConcurrentHashMap<>();
    private long ticksSinceSave;

    public static SusStore load(MinecraftServer server) {
        SusStore store = new SusStore();
        Path path = file();
        if (!Files.exists(path)) return store;

        try (Reader reader = Files.newBufferedReader(path)) {
            List<SusRecord> loaded = GSON.fromJson(reader, new TypeToken<List<SusRecord>>(){}.getType());
            if (loaded != null) {
                for (SusRecord r : loaded) {
                    if (r.uuid != null) {
                        normalize(r);
                        store.records.put(r.uuid, r);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Chill Zone SUS] Could not load data: " + e.getMessage());
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

        addLocation(r, new SusRecord.FlagLocation(
            category,
            reason,
            player.level().dimension().identifier().toString(),
            player.getX(), player.getY(), player.getZ(),
            now, actual, allowed, pitch
        ));
    }

    public synchronized void recordOreEvidence(ServerPlayer player, String type, String reason,
                                               int x, int y, int z, long now) {
        SusRecord r = getOrCreate(player.getUUID(), player.getGameProfile().name());
        addLocation(r, new SusRecord.FlagLocation(
            type,
            reason,
            player.level().dimension().identifier().toString(),
            x, y, z,
            now, 0.0, 0.0, player.getXRot()
        ));
    }

    private static void addLocation(SusRecord r, SusRecord.FlagLocation location) {
        if (r.flagLocations == null) r.flagLocations = new ArrayList<>();

        // Avoid filling all 14 slots with the same check firing every tick at
        // effectively the same block. A meaningfully different reason/location
        // is still saved immediately.
        if (!r.flagLocations.isEmpty()) {
            SusRecord.FlagLocation newest = r.flagLocations.get(0);
            boolean sameCategory = safeEquals(newest.category, location.category);
            boolean sameReason = safeEquals(newest.reason, location.reason);
            boolean sameWorld = safeEquals(newest.world, location.world);
            double dx = newest.x - location.x;
            double dy = newest.y - location.y;
            double dz = newest.z - location.z;
            boolean sameArea = dx * dx + dy * dy + dz * dz <= 4.0;
            boolean veryRecent = Math.abs(location.timestamp - newest.timestamp) <= 2_000L;
            if (sameCategory && sameReason && sameWorld && sameArea && veryRecent) return;
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
