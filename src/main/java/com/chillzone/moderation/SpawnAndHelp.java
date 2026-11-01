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
        // Explicit alias guarantees players can test Moderation's help, even when another mod owns /help.
        dispatcher.register(Commands.literal("czhelp")
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
        registerHelpOverride(dispatcher);
    }


    /** Re-register /help after other mods finish command registration.
     * Brigadier CommandNode.addChild merges a new command execution handler into the
     * existing root /help node, so our content takes precedence without changing
     * unrelated commands, permission rules, or teleport/spawn logic.
     */
    public static void reinforceHelp(net.minecraft.server.MinecraftServer server) {
        registerHelpOverride(server.getCommands().getDispatcher());
        System.out.println("[Chill Zone Moderation 0.3.21] Registered expanded /help command list on live dispatcher");
    }

    private static void registerHelpOverride(CommandDispatcher<CommandSourceStack> dispatcher) {
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

    /** Detailed Season 2 command list. Does not modify any non-help feature. */
    private static int showHelp(CommandSourceStack s, String section) {
        s.sendSuccess(() -> Component.literal("✦ CHILL ZONE SMP ✦  HELP 0.3.21 — UPDATED COMMANDS  |  " + section.toUpperCase())
            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        String[] lines = switch (section) {
            case "teleportation" -> new String[] {
                "/spawn — Go to spawn after a 1-second delay",
                "/homes — Open saved homes menu",
                "/home <name> — Go to a saved home",
                "/sethome <name> — Create a new home",
                "/delhome <name> — Delete a saved home",
                "/tpa <player> — Ask to teleport to someone",
                "/tpahere <player> — Ask someone to teleport to you",
                "/tpaccept — Accept a request | /tpdeny — Deny it",
                "Teleporting is blocked while combat-tagged"
            };
            case "lifesteal" -> new String[] {
                "/lifesteal hearts — Show your current hearts",
                "/lifesteal list — Top 10 by hearts",
                "/lifesteal craftstatus — Daily 3-Heart limit and timer",
                "/lifesteal recipe heart — Heart crafting recipe",
                "/lifesteal recipe beacon — Revive Beacon recipe",
                "/withdraw <amount> — Turn hearts into Heart items",
                "/leaderboards — Kills, deaths, blocks and other ranks"
            };
            case "moderation" -> new String[] {
                "/warn <player> — Warn a player",
                "/warnings <player> — View warning history",
                "/mute <player> / /unmute <player> — Control mutes",
                "/freeze <player> / /unfreeze <player> — Freeze controls",
                "/sus — Open suspicious activity evidence",
                "/susclear <player> — Clear evidence | /susback — Return",
                "/punishments <player> — Punishment history"
            };
            case "admin" -> new String[] {
                "/vanish — Staff invisibility (separate Vanish permission)",
                "/czban <player> / /czunban <player> — Permanent bans",
                "/tempban <player> / /untempban <player> — Temporary bans",
                "/punishments <player> — Review punishment records",
                "/tpto <player> / /tphere <player> — Staff teleportation"
            };
            case "owner" -> new String[] {
                "/spawn set — Set spawn at your location",
                "/spawn set create — Same as /spawn set",
                "/spawn set <x> <y> <z> — Set spawn by coordinates",
                "/server preparing / /server start — Season launch controls",
                "/end open / /end close — Control End travel",
                "/nether open / /nether close — Control Nether travel",
                "/leaderboards reset confirm — Reset leaderboard progress",
                "/lifesteal admin menu — Open combat/Lifesteal settings",
                "/lifesteal admin giveheart / giverevivebeacon — Give items",
                "/lifesteal admin sethearts / maxhearts — Adjust hearts",
                "/lifesteal admin skip — Reset crafting quota",
                "/lifesteal admin containers — Heart storage rules",
                "/lifesteal admin pvp / pvploss / naturalloss / deathbans",
                "/lifesteal admin revive / combat status / reset CONFIRM"
            };
            default -> new String[] {
                "/rules — Server rules | /shop — Shard shop",
                "/leaderboards — Player rankings",
                "/homes / /home / /sethome / /delhome — Saved homes",
                "/spawn / /tpa / /tpahere / /tpaccept / /tpdeny",
                "/lifesteal hearts / list / craftstatus — Heart information",
                "/lifesteal recipe heart / recipe beacon — Recipes",
                "/withdraw <amount> — Withdraw a Heart item",
                "/teams — Allied Teams, if enabled",
                "More: /czhelp teleportation | /czhelp lifesteal",
                "Staff: /czhelp moderation | admin | owner (by permission)"
            };
        };
        for (String line : lines) {
            s.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
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
