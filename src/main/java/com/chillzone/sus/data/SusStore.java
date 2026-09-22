package com.chillzone.sus.data;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.io.*; import java.nio.file.*; import java.util.*; import java.util.concurrent.ConcurrentHashMap;

public final class SusStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SAVE_EVERY_TICKS = 20 * 60;
    private final Map<UUID, SusRecord> records = new ConcurrentHashMap<>();
    private long ticksSinceSave;

    public static SusStore load(MinecraftServer server) {
        SusStore store = new SusStore(); Path path = file(); if (!Files.exists(path)) return store;
        try (Reader reader = Files.newBufferedReader(path)) {
            List<SusRecord> loaded = GSON.fromJson(reader, new TypeToken<List<SusRecord>>(){}.getType());
            if (loaded != null) for (SusRecord r : loaded) if (r.uuid != null) { normalize(r); store.records.put(r.uuid, r); }
        } catch (Exception e) { System.err.println("[Chill Zone SUS] Could not load data: " + e.getMessage()); }
        return store;
    }
    private static void normalize(SusRecord r) {
        if (r.recentFlagTimes == null) r.recentFlagTimes = new ArrayList<>();
        if (r.diamond == null) r.diamond = new SusRecord.OreCase();
        if (r.debris == null) r.debris = new SusRecord.OreCase();
        normalizeCase(r.diamond); normalizeCase(r.debris);
    }
    private static void normalizeCase(SusRecord.OreCase c) {
        if (c.veinTimes == null) c.veinTimes = new ArrayList<>();
        if (c.recentIntervalsMs == null) c.recentIntervalsMs = new ArrayList<>();
    }
    public synchronized void save(MinecraftServer server) {
        try { Files.createDirectories(file().getParent()); try (Writer w = Files.newBufferedWriter(file())) { GSON.toJson(new ArrayList<>(records.values()), w); } }
        catch (Exception e) { System.err.println("[Chill Zone SUS] Could not save data: " + e.getMessage()); }
    }
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("chill_zone_sus.json"); }
    public SusRecord getOrCreate(UUID uuid, String name) { SusRecord r=records.computeIfAbsent(uuid,id->new SusRecord(id,name)); normalize(r); r.lastKnownName=name; return r; }
    public SusRecord get(UUID uuid) { return records.get(uuid); }
    public Collection<SusRecord> all() { return records.values(); }

    public void flag(ServerPlayer p, String type, int points) {
        SusRecord r=getOrCreate(p.getUUID(),p.getGameProfile().name()); SusRecord.OreCase c=r.ore(type); long now=System.currentTimeMillis();
        c.suspicionScore += points; c.activeFlags++; c.lastFlagEpochMs=now;
    }
    public void ensureScore(ServerPlayer p, String type, int minimum) {
        SusRecord r=getOrCreate(p.getUUID(),p.getGameProfile().name()); SusRecord.OreCase c=r.ore(type); long now=System.currentTimeMillis();
        if (c.suspicionScore < minimum) { c.suspicionScore=minimum; c.activeFlags++; c.lastFlagEpochMs=now; }
    }
    public void setScore(ServerPlayer p, String type, int score) {
        SusRecord r=getOrCreate(p.getUUID(),p.getGameProfile().name()); SusRecord.OreCase c=r.ore(type);
        int old=c.suspicionScore; c.suspicionScore=Math.max(0,score);
        if (c.suspicionScore > old) { c.activeFlags++; c.lastFlagEpochMs=System.currentTimeMillis(); }
    }
    public void clearActive(UUID uuid, String name) {
        SusRecord r=getOrCreate(uuid,name); clearCase(r.diamond); clearCase(r.debris);
        r.suspicionScore=0; r.lastFlagEpochMs=0; r.cleanActiveTicks=0; r.recentFlagTimes.clear();
        clearHackFields(r);
    }
    public void clearCase(UUID uuid, String name, String type) { clearCase(getOrCreate(uuid,name).ore(type)); }
    public void clearHackCase(UUID uuid, String name) {
        SusRecord r=getOrCreate(uuid,name);
        clearHackFields(r);
    }
    public synchronized void recordGrimAttempt(UUID uuid, String name, boolean fly, long now) {
        SusRecord r = getOrCreate(uuid, name);
        if (fly) { r.grimFlyAttempts++; r.lastGrimFlyEpochMs = now; }
        else { r.grimSpeedAttempts++; r.lastGrimSpeedEpochMs = now; }
    }
    public synchronized void recordGrimBlock(UUID uuid, String name, boolean fly, long now) {
        SusRecord r = getOrCreate(uuid, name);
        if (fly) { r.grimFlyBlocked++; r.lastGrimFlyEpochMs = Math.max(r.lastGrimFlyEpochMs, now); }
        else { r.grimSpeedBlocked++; r.lastGrimSpeedEpochMs = Math.max(r.lastGrimSpeedEpochMs, now); }
    }
    private static void clearHackFields(SusRecord r) {
        r.grimFlyAttempts=0; r.grimFlyBlocked=0; r.lastGrimFlyEpochMs=0;
        r.grimSpeedAttempts=0; r.grimSpeedBlocked=0; r.lastGrimSpeedEpochMs=0;
    }
    private static void clearCase(SusRecord.OreCase c) {
        c.archivedPoints += Math.max(0,c.suspicionScore); c.suspicionScore=0; c.activeFlags=0; c.lastFlagEpochMs=0;
        c.oreMined=0; c.separateVeins=0; c.veinTimes.clear(); c.recentIntervalsMs.clear(); c.lastVeinEpochMs=0; c.currentVeinId=0; c.currentVeinLastBreakMs=0;
        c.blocksSinceLastVein=0; c.totalBlocksBetweenVeins=0; c.blockGapSamples=0; c.lowBlockGapVeins=0; c.veryLowBlockGapVeins=0;
        c.fastVeins=0; c.caveExposedVeins=0; c.tunnelLikeVeins=0; c.unusualOreEvents=0;
    }
    public void tick(MinecraftServer server) {
        for (ServerPlayer p:server.getPlayerList().getPlayers()) getOrCreate(p.getUUID(),p.getGameProfile().name()).totalActiveTicks++;
        if (++ticksSinceSave>=SAVE_EVERY_TICKS) { save(server); ticksSinceSave=0; }
    }
}
