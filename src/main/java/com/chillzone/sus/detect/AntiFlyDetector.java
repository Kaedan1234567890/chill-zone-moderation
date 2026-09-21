package com.chillzone.sus.detect;

import com.chillzone.sus.data.SusRecord;
import com.chillzone.sus.data.SusStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Conservative always-on anti-fly. It does not punish players; it blocks sustained
 * unsupported hovering/rising and records the attempt for /sus. Creative,
 * spectator and Elytra flight are exempt. The grace window intentionally avoids
 * treating ordinary jumps, falls, knockback and brief lag corrections as flight.
 */
public final class AntiFlyDetector {
    private static final Map<UUID, State> states = new HashMap<>();
    private static final int AIR_GRACE_TICKS = 30; // 1.5 seconds before we consider unsupported flight
    private static final int CONFIRM_TICKS = 10;   // sustained hover/rise confirmation
    private static SusStore store;

    private AntiFlyDetector() {}
    public static void init(SusStore s) { store = s; }

    public static void tick(MinecraftServer server) {
        if (store == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State st = states.computeIfAbsent(p.getUUID(), id -> new State(p.getX(), p.getY(), p.getZ()));
            SusRecord r = store.getOrCreate(p.getUUID(), p.getGameProfile().name());

            boolean exempt = p.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                || p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                || p.isFallFlying()
                || p.isPassenger()
                || p.isInWater()
                || p.onClimbable();

            if (exempt || p.onGround()) {
                st.airTicks = 0; st.confirmTicks = 0;
                st.safeX = p.getX(); st.safeY = p.getY(); st.safeZ = p.getZ();
                st.lastY = p.getY();
                continue;
            }

            st.airTicks++;
            double dy = p.getY() - st.lastY;
            st.lastY = p.getY();

            // Normal airborne motion should be descending after the jump apex.
            // Sustained near-level hovering or rising after a generous grace period
            // is the signal we block and record.
            if (st.airTicks > AIR_GRACE_TICKS && dy > -0.025D) st.confirmTicks++;
            else if (dy < -0.08D) st.confirmTicks = Math.max(0, st.confirmTicks - 2);

            if (st.confirmTicks >= CONFIRM_TICKS) {
                r.illegalFlightAttempts++;
                r.preventedFlightAttempts++;
                r.lastFlightAttemptEpochMs = System.currentTimeMillis();
                p.teleportTo(p.level(), st.safeX, st.safeY, st.safeZ, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
                st.airTicks = 0; st.confirmTicks = 0; st.lastY = st.safeY;
            }
        }
        states.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static final class State {
        double safeX, safeY, safeZ, lastY;
        int airTicks, confirmTicks;
        State(double x, double y, double z) { safeX=x; safeY=y; safeZ=z; lastY=y; }
    }
}
