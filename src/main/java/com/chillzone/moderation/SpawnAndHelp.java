package com.chillzone.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns only the new /help and /spawn commands. Existing moderation systems are unchanged. */
public final class SpawnAndHelp {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chill-zone-spawn.json");
    private static final Map<UUID, PendingTeleport> WAITING = new HashMap<>();
    private static SpawnLocation location;
    private static boolean initialized = false;

    private record SpawnLocation(double x, double y, double z, float yaw, float pitch) {}
    private record PendingTeleport(long readyAt, double x, double y, double z) {}

    private SpawnAndHelp() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ServerTickEvents.END_SERVER_TICK.register(SpawnAndHelp::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WAITING.remove(handler.getPlayer().getUUID()));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        initialize();
        dispatcher.register(Commands.literal("spawn")
            .executes(ctx -> teleportSpawn(ctx.getSource()))
            .then(Commands.literal("set")
                .requires(SpawnAndHelp::canSetSpawn)
                .executes(ctx -> setAtPlayer(ctx.getSource()))
                .then(Commands.literal("create").executes(ctx -> setAtPlayer(ctx.getSource())))
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                    .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                            .executes(ctx -> setSpawn(ctx.getSource(),
                                DoubleArgumentType.getDouble(ctx, "x"),
                                DoubleArgumentType.getDouble(ctx, "y"),
                                DoubleArgumentType.getDouble(ctx, "z"))))))));
        dispatcher.register(Commands.literal("help")
            .executes(ctx -> showHelp(ctx.getSource(), "general"))
            .then(Commands.literal("general").executes(ctx -> showHelp(ctx.getSource(), "general")))
            .then(Commands.literal("teleportation").executes(ctx -> showHelp(ctx.getSource(), "teleportation")))
            .then(Commands.literal("moderation").requires(s -> Permissions.has(s, Permissions.WARN))
                .executes(ctx -> showHelp(ctx.getSource(), "moderation")))
            .then(Commands.literal("admin").requires(s -> Permissions.has(s, Permissions.STAFF_TELEPORT))
                .executes(ctx -> showHelp(ctx.getSource(), "admin")))
            .then(Commands.literal("owner").requires(SpawnAndHelp::canSetSpawn)
                .executes(ctx -> showHelp(ctx.getSource(), "owner"))));
    }

    private static boolean canSetSpawn(CommandSourceStack s) {
        return s.getEntity() == null || Permissions.has(s, Permissions.SPAWN_SET);
    }

    private static int showHelp(CommandSourceStack s, String section) {
        s.sendSuccess(() -> Component.literal("Chill Zone SMP | " + section.toUpperCase()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        String desc = switch (section) {
            case "teleportation" -> "/spawn - server spawn  |  /home, /homes, /sethome, /delhome - homes  |  /tpa, /tpaccept, /tpdeny - requests";
            case "moderation" -> "/warn, /warnings, /mute, /unmute, /freeze, /unfreeze, /punishments, /sus, /susclear, /susback";
            case "admin" -> "/tpto, /tphere - staff teleport  |  /vanish - staff vanish (permission controlled)  |  /ban, /tempban, /unban";
            case "owner" -> "/spawn set [x y z], /spawn set create  |  /server preparing, /server start  |  /end open, /end close  |  /nether open, /nether close  |  /leaderboards reset confirm  |  /lifesteal admin";
            default -> "/shop - Shards shop  |  /homes - saved homes  |  /rules - rules  |  /discord - community  |  /leaderboards - stats  |  /lifesteal hearts, /lifesteal list, /lifesteal craftstatus  |  /teams - allies  |  /help teleportation, /help moderation, /help admin, /help owner";
        };
        s.sendSuccess(() -> Component.literal(desc).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int setAtPlayer(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        return setSpawn(source, p.getX(), p.getY(), p.getZ());
    }
    private static int setSpawn(CommandSourceStack source, double x, double y, double z) {
        if (!(source.getLevel().dimension() == Level.OVERWORLD)) {
            source.sendFailure(Component.literal("Spawn must be set in the Overworld."));
            return 0;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || y < -64 || y > 320 || Math.abs(x) > 29999984 || Math.abs(z) > 29999984) {
            source.sendFailure(Component.literal("Invalid spawn coordinates."));
            return 0;
        }
        float yaw = source.getEntity() instanceof ServerPlayer p ? p.getYRot() : 0;
        float pitch = source.getEntity() instanceof ServerPlayer p ? p.getXRot() : 0;
        location = new SpawnLocation(x, y, z, yaw, pitch);
        save();
        source.sendSuccess(() -> Component.literal("Spawn saved at " + (int)x + ", " + (int)y + ", " + (int)z + "."), false);
        return 1;
    }
    private static int teleportSpawn(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        if (location == null) {
            source.sendFailure(Component.literal("Spawn has not been set. Ask the Owner to use /spawn set."));
            return 0;
        }
        WAITING.put(p.getUUID(), new PendingTeleport(System.currentTimeMillis() + 1000, p.getX(), p.getY(), p.getZ()));
        p.sendSystemMessage(Component.literal("Teleporting to spawn in 1 second...").withStyle(ChatFormatting.YELLOW));
        return 1;
    }
    private static void tick(MinecraftServer server) {
        if (WAITING.isEmpty()) return;
        long now = System.currentTimeMillis();
        var it = WAITING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now < entry.getValue().readyAt()) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || location == null) continue;
            ServerLevel overworld = server.overworld();
            if (overworld == null) continue;
            boolean result = p.teleportTo(overworld, location.x(), location.y(), location.z(), Set.of(), location.yaw(), location.pitch(), false);
            if (result) p.sendSystemMessage(Component.literal("Teleported to spawn.").withStyle(ChatFormatting.GREEN));
            else p.sendSystemMessage(Component.literal("Spawn teleport failed.").withStyle(ChatFormatting.RED));
        }
    }
    private static void load() {
        try (Reader r = Files.newBufferedReader(FILE)) { location = JSON.fromJson(r, SpawnLocation.class); }
        catch (java.nio.file.NoSuchFileException ignored) {}
        catch (Exception e) { System.err.println("[Chill Zone Spawn] Cannot read spawn: " + e); }
    }
    private static void save() {
        try { Files.createDirectories(FILE.getParent()); try (Writer w = Files.newBufferedWriter(FILE)) { JSON.toJson(location, w); } }
        catch (Exception e) { System.err.println("[Chill Zone Spawn] Cannot save spawn: " + e); }
    }
}
