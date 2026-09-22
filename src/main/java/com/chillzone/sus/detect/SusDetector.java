package com.chillzone.sus.detect;

import com.chillzone.sus.data.SusRecord;
import com.chillzone.sus.data.SusStore;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class SusDetector {
    private static final long SAME_VEIN_MS = 45_000L;
    private static final int VEIN_DISTANCE = 5;
    private static SusStore store;

    private SusDetector() {}

    public static void init(SusStore s) {
        store = s;
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayer sp)) return;

            SusRecord r = store.getOrCreate(sp.getUUID(), sp.getGameProfile().name());
            String type = oreType(state);
            recordMiningContext(r, pos, type == null);

            if (type == null) {
                r.diamond.blocksSinceLastVein++;
                r.debris.blocksSinceLastVein++;
                return;
            }

            record(sp, r, type, pos, level, System.currentTimeMillis());
        });
    }

    public static void refreshAll(long now) { }

    private static void recordMiningContext(SusRecord r, BlockPos pos, boolean nonOre) {
        r.totalBlocksBroken++;
        if (nonOre) r.nonOreBlocksBroken++;

        if (r.hasLastBreak) {
            int dx = Integer.compare(pos.getX() - r.lastBreakX, 0);
            int dy = Integer.compare(pos.getY() - r.lastBreakY, 0);
            int dz = Integer.compare(pos.getZ() - r.lastBreakZ, 0);
            int manhattan = Math.abs(pos.getX() - r.lastBreakX)
                + Math.abs(pos.getY() - r.lastBreakY)
                + Math.abs(pos.getZ() - r.lastBreakZ);
            boolean adjacent = manhattan == 1;
            boolean sameStep = adjacent && dx == r.lastStepX && dy == r.lastStepY && dz == r.lastStepZ;
            if (sameStep) r.straightBreakStreak++;
            else r.straightBreakStreak = adjacent ? 2 : 1;
            r.lastStepX = dx;
            r.lastStepY = dy;
            r.lastStepZ = dz;
        } else {
            r.straightBreakStreak = 1;
            r.hasLastBreak = true;
        }

        r.maxStraightBreakStreak = Math.max(r.maxStraightBreakStreak, r.straightBreakStreak);
        r.lastBreakX = pos.getX();
        r.lastBreakY = pos.getY();
        r.lastBreakZ = pos.getZ();
    }

    private static void record(ServerPlayer sp, SusRecord r, String type, BlockPos pos, Level level, long now) {
        SusRecord.OreCase c = r.ore(type);
        c.oreMined++;

        boolean same = c.currentVeinLastBreakMs > 0
            && now - c.currentVeinLastBreakMs <= SAME_VEIN_MS
            && Math.abs(c.currentVeinX - pos.getX()) <= VEIN_DISTANCE
            && Math.abs(c.currentVeinY - pos.getY()) <= VEIN_DISTANCE
            && Math.abs(c.currentVeinZ - pos.getZ()) <= VEIN_DISTANCE;

        boolean savedSuspiciousLocation = false;

        if (!same) {
            long previousVeinTime = c.lastVeinEpochMs;
            long blocksBeforeThisVein = c.blocksSinceLastVein;
            long timeSincePreviousVein = previousVeinTime > 0 ? now - previousVeinTime : -1L;

            c.separateVeins++;

            if (previousVeinTime > 0) {
                c.recentIntervalsMs.add(timeSincePreviousVein);
                while (c.recentIntervalsMs.size() > 50) c.recentIntervalsMs.remove(0);
                if (timeSincePreviousVein <= 90_000L) c.fastVeins++;

                c.totalBlocksBetweenVeins += blocksBeforeThisVein;
                c.blockGapSamples++;
                if (blocksBeforeThisVein <= 12) c.lowBlockGapVeins++;
                if (blocksBeforeThisVein <= 5) c.veryLowBlockGapVeins++;
            }

            boolean caveExposed = isCaveExposed(level, pos);
            boolean tunnelLike = !caveExposed && r.straightBreakStreak >= 5;
            if (caveExposed) c.caveExposedVeins++;
            if (tunnelLike) c.tunnelLikeVeins++;

            boolean hasPrevious = previousVeinTime > 0;
            boolean lowMiningSupport = hasPrevious && blocksBeforeThisVein <= 12;
            boolean veryLowMiningSupport = hasPrevious && blocksBeforeThisVein <= 5;
            boolean fastFind = hasPrevious && timeSincePreviousVein <= 120_000L;
            boolean veryFastFind = hasPrevious && timeSincePreviousVein <= 45_000L;

            // Evidence-only design: flag an inspectable location sooner than the
            // old detector did, but require multiple server-side signals. A cave
            // exposed vein by itself is deliberately not treated as suspicious.
            boolean suspiciousEvent = !caveExposed && (
                (veryLowMiningSupport && fastFind)
                    || (tunnelLike && lowMiningSupport)
                    || (c.separateVeins >= 3 && lowMiningSupport && fastFind)
                    || (c.separateVeins >= 3 && tunnelLike && veryFastFind)
            );

            if (suspiciousEvent) {
                c.unusualOreEvents++;
                String oreName = "debris".equals(type) ? "Ancient Debris" : "Diamond";
                store.recordOreEvidence(
                    sp,
                    type,
                    oreName + " suspicious mining event",
                    pos.getX(), pos.getY(), pos.getZ(), now
                );
                savedSuspiciousLocation = true;
            }

            c.blocksSinceLastVein = 0;
            c.lastVeinEpochMs = now;
            c.veinTimes.add(now);
            while (c.veinTimes.size() > 100) c.veinTimes.remove(0);
            c.currentVeinId++;
        }

        c.currentVeinLastBreakMs = now;
        c.currentVeinX = pos.getX();
        c.currentVeinY = pos.getY();
        c.currentVeinZ = pos.getZ();

        int score = calculateScore(c);
        boolean scoreIncreased = store.setScore(sp, type, score);
        if (scoreIncreased && !savedSuspiciousLocation) {
            String oreName = "debris".equals(type) ? "Ancient Debris" : "Diamond";
            store.recordOreEvidence(
                sp,
                type,
                oreName + " activity score increased",
                pos.getX(), pos.getY(), pos.getZ(), now
            );
        }
    }

    private static boolean isCaveExposed(Level level, BlockPos pos) {
        int open = 0;
        for (Direction d : Direction.values()) {
            BlockState around = level.getBlockState(pos.relative(d));
            if (around.isAir()) open++;
        }
        return open >= 2;
    }

    private static int calculateScore(SusRecord.OreCase c) {
        int score = 0;

        // Start building evidence after two separate veins instead of waiting
        // for four. High scores still require a pattern, not one lucky find.
        if (c.separateVeins >= 2) {
            double avgBlocks = c.averageBlocksBetweenVeins();
            double lowGapRate = c.blockGapSamples <= 0 ? 0.0 : c.lowBlockGapVeins / (double)c.blockGapSamples;
            double veryLowRate = c.blockGapSamples <= 0 ? 0.0 : c.veryLowBlockGapVeins / (double)c.blockGapSamples;
            double caveRate = c.caveExposedVeins / (double)Math.max(1, c.separateVeins);
            double tunnelRate = c.tunnelLikeVeins / (double)Math.max(1, c.separateVeins);

            if (avgBlocks >= 0 && avgBlocks < 5) score += 6;
            else if (avgBlocks >= 0 && avgBlocks < 10) score += 4;
            else if (avgBlocks >= 0 && avgBlocks < 18) score += 2;

            if (veryLowRate >= 0.50 && c.blockGapSamples >= 2) score += 5;
            else if (lowGapRate >= 0.50 && c.blockGapSamples >= 2) score += 3;
            else if (lowGapRate >= 0.30 && c.blockGapSamples >= 3) score += 2;

            if (c.fastVeins >= 6) score += 5;
            else if (c.fastVeins >= 3) score += 3;
            else if (c.fastVeins >= 2) score += 1;

            if (tunnelRate >= 0.50 && c.tunnelLikeVeins >= 3) score += 4;
            else if (tunnelRate >= 0.30 && c.tunnelLikeVeins >= 2) score += 2;

            if (c.unusualOreEvents >= 5) score += 8;
            else if (c.unusualOreEvents >= 3) score += 5;
            else if (c.unusualOreEvents >= 2) score += 3;
            else if (c.unusualOreEvents >= 1) score += 1;

            // Strong cave evidence lowers suspicion substantially.
            if (caveRate >= 0.65 && c.caveExposedVeins >= 4) score -= 5;
            else if (caveRate >= 0.45 && c.caveExposedVeins >= 3) score -= 2;

            // Lots of ordinary mining between finds also lowers suspicion.
            if (avgBlocks >= 35 && c.blockGapSamples >= 4) score -= 4;
            else if (avgBlocks >= 22 && c.blockGapSamples >= 4) score -= 2;
        }

        return Math.max(0, Math.min(30, score));
    }

    private static String oreType(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return null;
        return switch (id.getPath()) {
            case "diamond_ore", "deepslate_diamond_ore" -> "diamond";
            case "ancient_debris" -> "debris";
            default -> null;
        };
    }
}
