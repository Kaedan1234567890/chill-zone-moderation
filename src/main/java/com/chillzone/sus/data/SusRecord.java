package com.chillzone.sus.data;

import java.util.*;

public final class SusRecord {
    public UUID uuid;
    public String lastKnownName;

    // Legacy fields retained so existing chill_zone_sus.json files load safely.
    public int suspicionScore;
    public int archivedFlags;
    public long lastFlagEpochMs;
    public long cleanActiveTicks;
    public long totalActiveTicks;
    public List<Long> recentFlagTimes = new ArrayList<>();

    // Shared mining context. These counters let SUS judge ore finds against the
    // amount and shape of ordinary mining instead of raw diamond totals alone.
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

    // GrimAC-backed hack evidence. Legacy anti-fly fields above were replaced in 0.2.7.
    public int grimFlyAttempts;
    public int grimFlyBlocked;
    public long lastGrimFlyEpochMs;
    public int grimSpeedAttempts;
    public int grimSpeedBlocked;
    public long lastGrimSpeedEpochMs;

    public boolean hasHackActivity() { return grimFlyAttempts > 0 || grimSpeedAttempts > 0; }
    public long lastHackEpochMs() { return Math.max(lastGrimFlyEpochMs, lastGrimSpeedEpochMs); }

    public OreCase diamond = new OreCase();
    public OreCase debris = new OreCase();

    public SusRecord() {}
    public SusRecord(UUID uuid, String name) { this.uuid = uuid; this.lastKnownName = name; }

    public OreCase ore(String type) { return "debris".equals(type) ? debris : diamond; }

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

        // Behaviour-based evidence added in 0.4.0.
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
            long sum = 0; for (long v : recentIntervalsMs) sum += v;
            return sum / recentIntervalsMs.size();
        }
        public long fastestIntervalMs() {
            if (recentIntervalsMs == null || recentIntervalsMs.isEmpty()) return -1;
            long min = Long.MAX_VALUE; for (long v : recentIntervalsMs) min = Math.min(min, v);
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
