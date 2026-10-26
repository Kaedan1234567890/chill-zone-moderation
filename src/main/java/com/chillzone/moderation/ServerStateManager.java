package com.chillzone.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class ServerStateManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chill-zone-moderation-server-state.json");

    public static final double PREPARING_BORDER = 100.0;
    public static final double FINAL_OVERWORLD_BORDER = 16000.0;
    public static final int NETHER_BORDER = 5000;
    public static final int END_BORDER = 10000;
    public static final long BORDER_EXPANSION_TICKS = 20L * 90L; // 90 seconds: slightly slower visible expansion
    public static final long GRACE_MILLIS = 60L * 60L * 1000L;

    private static State state = new State();

    private ServerStateManager() {}

    public static final class State {
        public boolean endOpen = false;
        public boolean netherOpen = false;
        public boolean serverStarted = false;
        public long graceEndsAtEpochMs = 0L;
        public boolean graceEndAnnounced = false;
    }

    public static void load() {
        if (!Files.exists(FILE)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(FILE)) {
            State loaded = GSON.fromJson(r, State.class);
            if (loaded != null) state = loaded;
        } catch (Exception e) {
            System.err.println("[Chill Zone Moderation] Could not load server state: " + e.getMessage());
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(state, w);
            }
        } catch (Exception e) {
            System.err.println("[Chill Zone Moderation] Could not save server state: " + e.getMessage());
        }
    }

    public static boolean isEndOpen() { return state.endOpen; }
    public static boolean isNetherOpen() { return state.netherOpen; }
    public static boolean isServerStarted() { return state.serverStarted; }
    public static boolean isGraceActive() {
        return state.serverStarted && state.graceEndsAtEpochMs > System.currentTimeMillis();
    }
    public static boolean isOverworldRtpUnlocked() {
        return state.serverStarted && !isGraceActive();
    }
    public static long graceSecondsRemaining() {
        return Math.max(0L, (state.graceEndsAtEpochMs - System.currentTimeMillis() + 999L) / 1000L);
    }

    public static boolean blocksDimension(ServerLevel target) {
        if (target == null) return false;
        if (target.dimension() == Level.END) return !state.endOpen;
        if (target.dimension() == Level.NETHER) return !state.netherOpen;
        return false;
    }

    public static void closeEnd(MinecraftServer server) {
        state.endOpen = false;
        save();
        ejectPlayers(server, Level.END);
        broadcast(server, Component.literal("The End is now CLOSED.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    public static void openEnd(MinecraftServer server, String openingMode) {
        state.endOpen = true;
        save();
        boolean dragon = "dragon_fight".equals(openingMode);
        showTitle(server,
            Component.literal("THE END IS OPEN!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
            Component.literal(dragon ? "Good luck getting the dragon egg!" : "Explore the End!").withStyle(ChatFormatting.WHITE));
        broadcast(server, Component.literal("The End is OPEN!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        if (dragon) broadcast(server, Component.literal("Dragon Fight: Good luck getting the egg!").withStyle(ChatFormatting.YELLOW));
        broadcast(server, Component.literal("End world border: " + END_BORDER + " x " + END_BORDER + " blocks.").withStyle(ChatFormatting.GRAY));
    }

    public static void closeNether(MinecraftServer server) {
        state.netherOpen = false;
        save();
        ejectPlayers(server, Level.NETHER);
        broadcast(server, Component.literal("The Nether is now CLOSED.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    public static void openNether(MinecraftServer server) {
        state.netherOpen = true;
        save();
        showTitle(server,
            Component.literal("THE NETHER IS OPEN!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            Component.literal("World border: " + NETHER_BORDER + " x " + NETHER_BORDER).withStyle(ChatFormatting.YELLOW));
        broadcast(server, Component.literal("The Nether is OPEN!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        broadcast(server, Component.literal("Nether world border: " + NETHER_BORDER + " x " + NETHER_BORDER + " blocks.").withStyle(ChatFormatting.GRAY));
    }

    public static void preparing(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        overworld.getWorldBorder().setCenter(0.0, 0.0);
        overworld.getWorldBorder().setSize(PREPARING_BORDER);
        state.serverStarted = false;
        state.graceEndsAtEpochMs = 0L;
        state.graceEndAnnounced = false;
        save();
        showTitle(server,
            Component.literal("SERVER PREPARING").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
            Component.literal("Gather at spawn • 100 x 100 border").withStyle(ChatFormatting.AQUA)
        );
        broadcast(server, Component.literal("Server preparing mode enabled. Overworld border set to 100 x 100.").withStyle(ChatFormatting.GRAY));
    }

    public static void startServer(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 0");

        double current = overworld.getWorldBorder().getSize();
        overworld.getWorldBorder().setCenter(0.0, 0.0);
        overworld.getWorldBorder().lerpSizeBetween(
            current, FINAL_OVERWORLD_BORDER, BORDER_EXPANSION_TICKS, overworld.getGameTime()
        );

        state.serverStarted = true;
        // A fresh launch always starts one new one-hour grace interval.
        // Repeated /server start commands still reset the hour; preparing clears it.
        state.graceEndsAtEpochMs = System.currentTimeMillis() + GRACE_MILLIS;
        state.graceEndAnnounced = false;
        save();

        showTitle(server,
            Component.literal("SERVER HAS STARTED").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
            Component.literal("1 HOUR GRACE PERIOD").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
        );
        broadcast(server, Component.literal("The Chill Zone SMP has started! PvP and Overworld RTP are disabled for 1 hour.").withStyle(ChatFormatting.YELLOW));
        broadcast(server, Component.literal("Overworld world border: 16,000 x 16,000 blocks (expanding now).").withStyle(ChatFormatting.GRAY));
        broadcast(server, Component.literal("Nether border: 5,000 x 5,000 | End border: 10,000 x 10,000 blocks (when opened).").withStyle(ChatFormatting.GRAY));
    }

    public static void tick(MinecraftServer server) {
        // Failsafe: remove players from locked dimensions even if vanilla, another mod,
        // or a vehicle bypasses the normal portal/teleport injection.
        if (!state.endOpen) ejectPlayers(server, Level.END);
        if (!state.netherOpen) ejectPlayers(server, Level.NETHER);
        if (state.serverStarted && !isGraceActive() && !state.graceEndAnnounced) {
            state.graceEndAnnounced = true;
            save();
            showTitle(server,
                Component.literal("GRACE PERIOD OVER").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.literal("PvP and Overworld RTP are now enabled").withStyle(ChatFormatting.WHITE)
            );
            broadcast(server, Component.literal("Grace period has ended. PvP and Overworld RTP are now enabled.").withStyle(ChatFormatting.RED));
        }
    }

    private static void ejectPlayers(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension) {
        ServerLevel overworld = server.overworld();
        var respawn = overworld.getLevelData().getRespawnData();
        BlockPos pos = respawn.globalPos().pos();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension() == dimension) {
                player.stopRiding();
                player.teleportTo(overworld, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    Set.of(), respawn.yaw(), respawn.pitch(), false);
                player.sendSystemMessage(Component.literal("That dimension has been closed. You were returned to the Overworld.").withStyle(ChatFormatting.RED));
            }
        }
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static void showTitle(MinecraftServer server, Component title, Component subtitle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }
}
