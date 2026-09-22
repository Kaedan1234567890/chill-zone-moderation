package com.chillzone.sus.detect;

import com.chillzone.sus.data.SusStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receives AntiFly detection events from the optional AntiFly mixin and stores
 * staff-facing evidence. It never punishes, teleports or modifies the player.
 */
public final class AntiFlyEvidenceBridge {
    private static final long RECENT_USE_WINDOW_MS = 5_000L;
    private static final long RECENT_IMPULSE_WINDOW_MS = 3_000L;
    // AntiFly can reach the same action point every tick while a condition persists.
    // Count one identical reason per second so /sus shows meaningful events, not tick spam.
    private static final long SAME_REASON_COOLDOWN_MS = 1_000L;
    private static final Map<UUID, RecentContext> RECENT = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_RECORDED = new ConcurrentHashMap<>();
    private static SusStore store;

    private AntiFlyEvidenceBridge() {}

    public static void init(SusStore s) {
        store = s;
        System.out.println("[Chill Zone SUS] AntiFly evidence bridge ready (observe-only integration)." );
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RecentContext ctx = RECENT.computeIfAbsent(player.getUUID(), ignored -> new RecentContext());

            int rockets = player.getStats().getValue(Stats.ITEM_USED.get(Items.FIREWORK_ROCKET));
            if (ctx.lastRocketUses >= 0 && rockets > ctx.lastRocketUses) {
                addUses(ctx.rocketUses, rockets - ctx.lastRocketUses, now);
            }
            ctx.lastRocketUses = rockets;

            int windCharges = player.getStats().getValue(Stats.ITEM_USED.get(Items.WIND_CHARGE));
            if (ctx.lastWindChargeUses >= 0 && windCharges > ctx.lastWindChargeUses) {
                addUses(ctx.windChargeUses, windCharges - ctx.lastWindChargeUses, now);
            }
            ctx.lastWindChargeUses = windCharges;

            Vec3 velocity = player.getDeltaMovement();
            if (ctx.lastVelocity != null) {
                double gain = velocity.length() - ctx.lastVelocity.length();
                if (gain > 0.30 && velocity.length() > 0.45) {
                    ctx.lastImpulseMs = now;
                }
            }
            ctx.lastVelocity = velocity;

            prune(ctx.rocketUses, now);
            prune(ctx.windChargeUses, now);
        }

        RECENT.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        LAST_RECORDED.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
    }

    private static void addUses(Deque<Long> queue, int count, long now) {
        int safeCount = Math.min(Math.max(count, 0), 32);
        for (int i = 0; i < safeCount; i++) queue.addLast(now);
    }

    private static void prune(Deque<Long> queue, long now) {
        while (!queue.isEmpty() && now - queue.peekFirst() > RECENT_USE_WINDOW_MS) queue.removeFirst();
    }

    public static void record(ServerPlayer player, String reason, double actual, double allowed) {
        SusStore currentStore = store;
        if (currentStore == null || player == null) return;

        long now = System.currentTimeMillis();
        RecentContext recent = RECENT.computeIfAbsent(player.getUUID(), ignored -> new RecentContext());
        prune(recent.rocketUses, now);
        prune(recent.windChargeUses, now);

        String category = classify(reason);
        String safeReason = reason == null ? "unknown" : reason;
        String dedupeKey = player.getUUID() + "|" + category + "|" + safeReason;
        Long previous = LAST_RECORDED.put(dedupeKey, now);
        if (previous != null && now - previous < SAME_REASON_COOLDOWN_MS) return;
        Vec3 velocity = player.getDeltaMovement();
        double horizontalBps = Math.hypot(velocity.x, velocity.z) * 20.0;
        double verticalBps = velocity.y * 20.0;
        float pitch = player.getXRot();
        boolean nearVerticalAscent = verticalBps > 0.0 && pitch <= -75.0f;
        boolean elytraEquipped = player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        boolean elytraActive = player.isFallFlying();

        int nearbyEntities = 0;
        int nearbyBoats = 0;
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(2.5))) {
            nearbyEntities++;
            if (entity instanceof Boat) nearbyBoats++;
        }

        String vehicle = "None";
        Entity ridden = player.getVehicle();
        if (ridden != null) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(ridden.getType());
            vehicle = id == null ? ridden.getType().toString() : id.toString();
        }

        currentStore.recordAntiFly(
            player,
            category,
            safeReason,
            actual,
            allowed,
            horizontalBps,
            verticalBps,
            pitch,
            nearVerticalAscent,
            elytraEquipped,
            elytraActive,
            player.isCreative(),
            player.isSpectator(),
            player.onGround(),
            heightAboveGround(player),
            movementEffects(player),
            recent.rocketUses.size(),
            recent.windChargeUses.size(),
            now - recent.lastImpulseMs <= RECENT_IMPULSE_WINDOW_MS,
            player.hurtTime > 0,
            nearbyBoats,
            nearbyEntities,
            vehicle,
            now
        );
    }

    private static String classify(String reason) {
        if (reason != null && reason.startsWith("elytra_")) return "elytra";
        if (reason != null && (reason.contains("speed") || reason.equals("boat_speed") || reason.equals("water_speed"))) {
            return "speed";
        }
        return "fly";
    }

    private static int heightAboveGround(ServerPlayer player) {
        BlockPos base = player.blockPosition();
        for (int i = 1; i <= 64; i++) {
            BlockPos below = base.below(i);
            if (!player.level().getBlockState(below).isAir()) return i - 1;
        }
        return 65; // 65 means "more than 64 blocks" in the GUI.
    }

    private static String movementEffects(ServerPlayer player) {
        StringBuilder out = new StringBuilder();
        appendEffect(out, player.getEffect(MobEffects.SPEED), "Speed");
        appendEffect(out, player.getEffect(MobEffects.SLOWNESS), "Slowness");
        appendEffect(out, player.getEffect(MobEffects.JUMP_BOOST), "Jump Boost");
        appendEffect(out, player.getEffect(MobEffects.SLOW_FALLING), "Slow Falling");
        appendEffect(out, player.getEffect(MobEffects.LEVITATION), "Levitation");
        appendEffect(out, player.getEffect(MobEffects.DOLPHINS_GRACE), "Dolphin's Grace");
        return out.length() == 0 ? "None" : out.toString();
    }

    private static void appendEffect(StringBuilder out, MobEffectInstance effect, String name) {
        if (effect == null) return;
        if (out.length() > 0) out.append(", ");
        out.append(name).append(' ').append(effect.getAmplifier() + 1);
    }

    private static final class RecentContext {
        int lastRocketUses = -1;
        int lastWindChargeUses = -1;
        final Deque<Long> rocketUses = new ArrayDeque<>();
        final Deque<Long> windChargeUses = new ArrayDeque<>();
        Vec3 lastVelocity;
        long lastImpulseMs;
    }
}
