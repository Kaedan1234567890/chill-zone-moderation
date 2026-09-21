package com.chillzone.sus.detect;

import com.chillzone.sus.data.*;
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

            // Every normal block broken is useful context for both ore categories.
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
            int manhattan = Math.abs(pos.getX() - r.lastBreakX) + Math.abs(pos.getY() - r.lastBreakY) + Math.abs(pos.getZ() - r.lastBreakZ);
            boolean adjacent = manhattan == 1;
            boolean sameStep = adjacent && dx == r.lastStepX && dy == r.lastStepY && dz == r.lastStepZ;
            if (sameStep) r.straightBreakStreak++;
            else r.straightBreakStreak = adjacent ? 2 : 1;
            r.lastStepX = dx; r.lastStepY = dy; r.lastStepZ = dz;
        } else {
            r.straightBreakStreak = 1;
            r.hasLastBreak = true;
        }
        r.maxStraightBreakStreak = Math.max(r.maxStraightBreakStreak, r.straightBreakStreak);
        r.lastBreakX = pos.getX(); r.lastBreakY = pos.getY(); r.lastBreakZ = pos.getZ();
    }

    private static void record(ServerPlayer sp, SusRecord r, String type, BlockPos pos, Level level, long now) {
        SusRecord.OreCase c = r.ore(type);
        c.oreMined++;

        boolean same = c.currentVeinLastBreakMs > 0 && now - c.currentVeinLastBreakMs <= SAME_VEIN_MS &&
            Math.abs(c.currentVeinX - pos.getX()) <= VEIN_DISTANCE &&
            Math.abs(c.currentVeinY - pos.getY()) <= VEIN_DISTANCE &&
            Math.abs(c.currentVeinZ - pos.getZ()) <= VEIN_DISTANCE;

        if (!same) {
            c.separateVeins++;

            if (c.lastVeinEpochMs > 0) {
                long gap = now - c.lastVeinEpochMs;
                c.recentIntervalsMs.add(gap);
                while (c.recentIntervalsMs.size() > 50) c.recentIntervalsMs.remove(0);
                if (gap <= 60_000L) c.fastVeins++;

                c.totalBlocksBetweenVeins += c.blocksSinceLastVein;
                c.blockGapSamples++;
                if (c.blocksSinceLastVein <= 8) c.lowBlockGapVeins++;
                if (c.blocksSinceLastVein <= 3) c.veryLowBlockGapVeins++;
            }

            boolean caveExposed = isCaveExposed(level, pos);
            boolean tunnelLike = !caveExposed && r.straightBreakStreak >= 6;
            if (caveExposed) c.caveExposedVeins++;
            if (tunnelLike) c.tunnelLikeVeins++;

            // An "unusual" event is deliberately multi-signal. A fast find by itself
            // or a straight tunnel by itself is not enough.
            boolean lowMiningSupport = c.lastVeinEpochMs > 0 && c.blocksSinceLastVein <= 5;
            boolean fastFind = c.lastVeinEpochMs > 0 && now - c.lastVeinEpochMs <= 75_000L;
            if ((!caveExposed && lowMiningSupport && fastFind) || (tunnelLike && lowMiningSupport)) {
                c.unusualOreEvents++;
            }

            c.blocksSinceLastVein = 0;
            c.lastVeinEpochMs = now;
            c.veinTimes.add(now);
            while (c.veinTimes.size() > 100) c.veinTimes.remove(0);
            c.currentVeinId++;
        }

        c.currentVeinLastBreakMs = now;
        c.currentVeinX = pos.getX(); c.currentVeinY = pos.getY(); c.currentVeinZ = pos.getZ();
        score(sp, r, type, c);
    }

    private static boolean isCaveExposed(Level level, BlockPos pos) {
        int open = 0;
        for (Direction d : Direction.values()) {
            BlockState around = level.getBlockState(pos.relative(d));
            if (around.isAir()) open++;
        }
        return open >= 2;
    }

    private static void score(ServerPlayer sp, SusRecord r, String type, SusRecord.OreCase c) {
        int score = 0;

        // Require a meaningful sample before the behaviour score can become high.
        if (c.separateVeins >= 4) {
            double avgBlocks = c.averageBlocksBetweenVeins();
            double lowGapRate = c.blockGapSamples <= 0 ? 0.0 : c.lowBlockGapVeins / (double)c.blockGapSamples;
            double veryLowRate = c.blockGapSamples <= 0 ? 0.0 : c.veryLowBlockGapVeins / (double)c.blockGapSamples;
            double caveRate = c.caveExposedVeins / (double)Math.max(1, c.separateVeins);
            double tunnelRate = c.tunnelLikeVeins / (double)Math.max(1, c.separateVeins);

            if (avgBlocks >= 0 && avgBlocks < 4) score += 7;
            else if (avgBlocks >= 0 && avgBlocks < 8) score += 4;
            else if (avgBlocks >= 0 && avgBlocks < 15) score += 2;

            if (veryLowRate >= 0.45) score += 6;
            else if (lowGapRate >= 0.50) score += 4;
            else if (lowGapRate >= 0.30) score += 2;

            if (c.fastVeins >= 8) score += 5;
            else if (c.fastVeins >= 5) score += 3;
            else if (c.fastVeins >= 3) score += 1;

            if (tunnelRate >= 0.50 && c.tunnelLikeVeins >= 4) score += 4;
            else if (tunnelRate >= 0.30 && c.tunnelLikeVeins >= 3) score += 2;

            if (c.unusualOreEvents >= 8) score += 7;
            else if (c.unusualOreEvents >= 5) score += 4;
            else if (c.unusualOreEvents >= 3) score += 2;

            // Strong cave evidence is a reason to LOWER confidence. This is the key
            // protection for legitimate long caving sessions with lots of diamonds.
            if (caveRate >= 0.65 && c.caveExposedVeins >= 6) score -= 5;
            else if (caveRate >= 0.45 && c.caveExposedVeins >= 4) score -= 2;

            // Lots of ordinary mining per vein also lowers confidence.
            if (avgBlocks >= 35 && c.blockGapSamples >= 5) score -= 4;
            else if (avgBlocks >= 22 && c.blockGapSamples >= 5) score -= 2;
        }

        // Raw ore totals are intentionally NOT used as suspicion points.
        score = Math.max(0, Math.min(30, score));
        store.setScore(sp, type, score);
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
