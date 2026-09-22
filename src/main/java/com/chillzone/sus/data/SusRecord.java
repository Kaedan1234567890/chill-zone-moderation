package com.chillzone.sus.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SusRecord {
    public UUID uuid;
    public String lastKnownName;

    // Legacy fields retained so older chill_zone_sus.json files still load.
    public int suspicionScore;
    public int archivedFlags;
    public long lastFlagEpochMs;
    public long cleanActiveTicks;
    public long totalActiveTicks;
    public List<Long> recentFlagTimes = new ArrayList<>();

    // Shared mining context.
    public long totalBlocksBroken;
    public long nonOreBlocksBroken;
    public int lastBreakX;
    public int lastBreakY;
    public int lastBreakZ;
    public boolean hasLastBreak;
    public int lastStepX;
    public int lastStepY;
    public int lastStepZ;
    public int straightBreakStreak;
    public int maxStraightBreakStreak;

    // AntiFlight-backed evidence categories.
    public ActivityCase fly = new ActivityCase();
    public ActivityCase speed = new ActivityCase();
    public ActivityCase elytra = new ActivityCase();

    // Ore / X-ray evidence.
    public OreCase diamond = new OreCase();
    public OreCase debris = new OreCase();

    // Newest first. The GUI exposes at most 14 saved evidence locations.
    public List<FlagLocation> flagLocations = new ArrayList<>();

    public SusRecord() {}
    public SusRecord(UUID uuid, String name) {
        this.uuid = uuid;
        this.lastKnownName = name;
    }

    public OreCase ore(String type) {
        return "debris".equals(type) ? debris : diamond;
    }

    public ActivityCase activity(String type) {
        return switch (type) {
            case "speed" -> speed;
            case "elytra" -> elytra;
            default -> fly;
        };
    }

    public boolean hasActivity() {
        return fly.flags > 0 || speed.flags > 0 || elytra.flags > 0
            || diamond.suspicionScore > 0 || debris.suspicionScore > 0
            || diamond.activeFlags > 0 || debris.activeFlags > 0
            || !flagLocations.isEmpty();
    }

    public int totalFlags() {
        return fly.flags + speed.flags + elytra.flags + diamond.activeFlags + debris.activeFlags;
    }

    public long lastActivityEpochMs() {
        return Math.max(
            Math.max(Math.max(fly.lastFlagEpochMs, speed.lastFlagEpochMs), elytra.lastFlagEpochMs),
            Math.max(diamond.lastFlagEpochMs, debris.lastFlagEpochMs)
        );
    }

    public static final class ActivityCase {
        public int flags;
        public long lastFlagEpochMs;
        public String lastReason = "";

        // Raw AntiFlight values from the check that triggered.
        public double lastActual;
        public double lastAllowed;

        // Extra server-side context captured at the same moment.
        public double lastHorizontalBps;
        public double lastVerticalBps;
        public float lastPitch;
        public boolean lastNearVerticalAscent;
        public boolean lastElytraEquipped;
        public boolean lastElytraActive;
        public boolean lastCreative;
        public boolean lastSpectator;
        public boolean lastOnGround;
        public int lastHeightAboveGround;
        public String lastMovementEffects = "None";
        public int lastRecentRockets;
        public int lastRecentWindCharges;
        public boolean lastRecentImpulse;
        public boolean lastRecentlyHurt;
        public int lastNearbyBoats;
        public int lastNearbyEntities;
        public String lastVehicle = "None";
    }

    public static final class FlagLocation {
        public String category;
        public String reason;
        public String world;
        public double x;
        public double y;
        public double z;
        public long timestamp;
        public double actual;
        public double allowed;
        public float pitch;

        public FlagLocation() {}

        public FlagLocation(String category, String reason, String world,
                            double x, double y, double z, long timestamp,
                            double actual, double allowed, float pitch) {
            this.category = category;
            this.reason = reason;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
            this.actual = actual;
            this.allowed = allowed;
            this.pitch = pitch;
        }
    }

    public static final class OreCase {
        public int suspicionScore;
        public int archivedPoints;
        public int activeFlags;
        public long lastFlagEpochMs;
        public int oreMined;
        public int separateVeins;
        public List<Long> veinTimes = new ArrayList<>();
        public List<Long> recentIntervalsMs = new ArrayList<>();
        public long lastVeinEpochMs;
        public int currentVeinId;
        public long currentVeinLastBreakMs;
        public int currentVeinX, currentVeinY, currentVeinZ;

        public long blocksSinceLastVein;
        public long totalBlocksBetweenVeins;
        public int blockGapSamples;
        public int lowBlockGapVeins;
        public int veryLowBlockGapVeins;
        public int fastVeins;
        public int caveExposedVeins;
        public int tunnelLikeVeins;
        public int unusualOreEvents;

        public String status() {
            if (suspicionScore <= 4) return "Low / Normal";
            if (suspicionScore <= 9) return "Elevated";
            if (suspicionScore <= 17) return "High";
            return "Very High";
        }

        public long averageIntervalMs() {
            if (recentIntervalsMs == null || recentIntervalsMs.isEmpty()) return -1;
            long sum = 0;
            for (long v : recentIntervalsMs) sum += v;
            return sum / recentIntervalsMs.size();
        }

        public long fastestIntervalMs() {
            if (recentIntervalsMs == null || recentIntervalsMs.isEmpty()) return -1;
            long min = Long.MAX_VALUE;
            for (long v : recentIntervalsMs) min = Math.min(min, v);
            return min;
        }

        public double averageBlocksBetweenVeins() {
            return blockGapSamples <= 0 ? -1.0 : (double) totalBlocksBetweenVeins / blockGapSamples;
        }

        public double orePerVein() {
            return separateVeins <= 0 ? 0.0 : (double) oreMined / separateVeins;
        }

        public int cavePercent() {
            return separateVeins <= 0 ? 0 : (int)Math.round((caveExposedVeins * 100.0) / separateVeins);
        }

        public int tunnelPercent() {
            return separateVeins <= 0 ? 0 : (int)Math.round((tunnelLikeVeins * 100.0) / separateVeins);
        }
    }
}
