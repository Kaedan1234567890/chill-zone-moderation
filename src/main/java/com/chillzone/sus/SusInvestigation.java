package com.chillzone.sus;

import com.chillzone.moderation.ServerStateManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Temporary staff investigation sessions, never persisted or used to modify evidence. */
public final class SusInvestigation {
    private static final Map<UUID, ReturnPoint> RETURNS = new HashMap<>();

    private record ReturnPoint(ServerLevel level, double x, double y, double z,
                               float yaw, float pitch, GameType gameMode) {}

    private SusInvestigation() {}

    public static void begin(ServerPlayer staff, ServerLevel destination,
                             double x, double y, double z) {
        if (ServerStateManager.blocksDimension(destination)) {
            staff.sendSystemMessage(Component.literal("This evidence dimension is currently closed."));
            return;
        }
        RETURNS.computeIfAbsent(staff.getUUID(), id -> new ReturnPoint(
            (ServerLevel) staff.level(), staff.getX(), staff.getY(), staff.getZ(),
            staff.getYRot(), staff.getXRot(), staff.gameMode.getGameMode()));
        staff.stopRiding();
        staff.setGameMode(GameType.SPECTATOR);
        staff.teleportTo(destination, x, y, z, Set.of(), staff.getYRot(), staff.getXRot(), false);
        staff.sendSystemMessage(Component.literal("SUS investigation active. Use /susback to return to your previous location and game mode."));
    }

    public static boolean back(ServerPlayer staff) {
        ReturnPoint point = RETURNS.get(staff.getUUID());
        if (point == null) {
            staff.sendSystemMessage(Component.literal("No active SUS investigation. Use an evidence teleport first."));
            return false;
        }
        if (ServerStateManager.blocksDimension(point.level())) {
            staff.sendSystemMessage(Component.literal("Your previous dimension is closed. Your return point has been kept until it reopens."));
            return false;
        }
        staff.setCamera(staff);
        staff.stopRiding();
        staff.teleportTo(point.level(), point.x(), point.y(), point.z(), Set.of(), point.yaw(), point.pitch(), false);
        staff.setGameMode(point.gameMode());
        RETURNS.remove(staff.getUUID());
        staff.sendSystemMessage(Component.literal("Returned to your original location and game mode."));
        return true;
    }

    public static void clear() { RETURNS.clear(); }
}
