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
            .then(Commands.literal("lifesteal").executes(ctx -> showHelp(ctx.getSource(), "lifesteal")))
            .then(Commands.literal("pvp").executes(ctx -> showHelp(ctx.getSource(), "lifesteal")))
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

    /** Only help descriptions are changed in 0.3.19. Keep the established header and body styling. */
    private static int showHelp(CommandSourceStack s, String section) {
        s.sendSuccess(() -> Component.literal("Chill Zone SMP | " + section.toUpperCase())
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        String[] lines = switch (section) {
            case "teleportation" -> new String[] {
                "/spawn - Teleport to spawn after 1 second",
                "/homes - Open the home menu | /home <name> - Go to a saved home",
                "/sethome <name> - Create a home | /delhome <name> - Delete one",
                "/tpa <player> - Request teleport | /tpahere <player> - Ask player to come to you",
                "/tpaccept - Accept request | /tpdeny - Decline request"
            };
            case "lifesteal" -> new String[] {
                "/lifesteal hearts - Show your current hearts",
                "/lifesteal list - Show Top 10 Lifesteal hearts",
                "/lifesteal craftstatus - See today's 3-Heart crafting limit",
                "/lifesteal recipe heart - Heart recipe",
                "/lifesteal recipe beacon - Revive Beacon recipe",
                "/withdraw <amount> - Withdraw your hearts as items",
                "/leaderboards - View kills, deaths, playtime and other rankings",
                "Combat-tagged players cannot use teleportation commands"
            };
            case "moderation" -> new String[] {
                "/warn, /warnings - Issue and review warnings",
                "/mute, /unmute - Manage player mutes",
                "/freeze, /unfreeze - Freeze or release a player",
                "/sus - Suspected activity GUI | /sus <player> - Review a player",
                "/susclear <player> - Clear evidence | /susback - Return from evidence",
                "/punishments <player> - Review punishment records"
            };
            case "admin" -> new String[] {
                "/vanish - Staff vanish (requires Vanish mod access)",
                "/czban, /czunban - Permanent ban controls",
                "/tempban, /untempban - Temporary ban controls",
                "/punishments - Punishment history",
                "/tpto, /tphere - Staff teleportation tools"
            };
            case "owner" -> new String[] {
                "/spawn set - Set spawn here | /spawn set create - Same location",
                "/spawn set <x> <y> <z> - Set spawn with coordinates",
                "/server preparing, /server start - Launch and grace-period controls",
                "/end open, /end close | /nether open, /nether close",
                "/leaderboards reset confirm - Reset tracked leaderboard progress",
                "/lifesteal admin menu - Open Lifesteal settings",
                "/lifesteal admin skip - Reset Heart craft counter",
                "/lifesteal admin sethearts, giveheart, giverevivebeacon",
                "/lifesteal admin containers, revive, combat status",
                "/lifesteal admin pvp, pvploss, naturalloss, deathbans",
                "/lifesteal admin reset CONFIRM - Reset Lifesteal data"
            };
            default -> new String[] {
                "/help general, teleportation, lifesteal, moderation, admin, owner",
                "/rules - Server rules | /shop - Shard shop",
                "/leaderboards - Player statistics GUI",
                "/homes, /home, /sethome, /delhome - Home management",
                "/spawn, /tpa, /tpaccept, /tpdeny - Teleportation",
                "/lifesteal hearts, list, craftstatus - Heart information",
                "/lifesteal recipe heart, recipe beacon, /withdraw - Heart items",
                "/teams - Team management (if enabled)",
                "Use /help teleportation or /help lifesteal for details"
            };
        };
        for (String line : lines) {
            s.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.YELLOW), false);
        }
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
