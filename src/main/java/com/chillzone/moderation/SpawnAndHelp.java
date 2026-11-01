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
        System.out.println("[Chill Zone Moderation 0.3.22] Registered expanded /help command list on live dispatcher");
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

    /** Organized Season 2 help. Preserves the existing purple/blue text palette. */
    private static int showHelp(CommandSourceStack s, String section) {
        s.sendSuccess(() -> Component.literal("✦ CHILL ZONE SMP ✦  HELP 0.3.22  |  " + section.toUpperCase())
            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        String[] lines = switch (section) {
            case "teleportation" -> new String[] {
                "§dTELEPORTATION  §7— Travel & saved locations",
                "/spawn — Return to spawn (1-second delay)",
                "/homes — Open your saved-home menu",
                "/home <name> — Teleport to a saved home",
                "/sethome <name> — Save a home | /delhome <name> — Remove one",
                "/tpa <player> — Request a teleport to another player",
                "/tpahere <player> — Invite a player to teleport to you",
                "/tpaccept — Accept | /tpdeny — Decline",
                "§7Combat-tagged players cannot use teleport commands."
            };
            case "lifesteal" -> new String[] {
                "§dPVP & LIFESTEAL  §7— Hearts & combat",
                "/lifesteal hearts — Show how many hearts you have",
                "/lifesteal list — Top 10 players by heart count",
                "/lifesteal craftstatus — Heart crafting quota / countdown",
                "/lifesteal recipe heart — See Heart recipe",
                "/lifesteal recipe beacon — See Revive Beacon recipe",
                "/withdraw <amount> — Withdraw hearts as items",
                "/leaderboards — Open player statistics / rankings",
                "§7Combat-tag timer and rules apply during PvP."
            };
            case "moderation" -> new String[] {
                "§dMODERATOR COMMANDS  §7— Player safety",
                "/warn <player> — Warn | /warnings <player> — Warning history",
                "/mute <player> — Mute | /unmute <player> — Unmute",
                "/freeze <player> — Freeze | /unfreeze <player> — Release",
                "/sus — Open investigation cases and evidence",
                "/susclear <player> — Clear flags | /susback — Return",
                "/punishments <player> — View moderation history"
            };
            case "admin" -> new String[] {
                "§dADMIN COMMANDS  §7— Advanced moderation",
                "/vanish — Toggle staff vanish (requires Vanish permission)",
                "/czban <player> — Ban | /czunban <player> — Unban",
                "/tempban <player> — Temporary ban",
                "/untempban <player> — Remove temporary ban",
                "/tpto <player> — Staff teleport to player",
                "/tphere <player> — Teleport player to staff",
                "/punishments <player> — Review punishment records",
                "§7Moderator commands remain available to permitted admins."
            };
            case "owner" -> new String[] {
                "§dOWNER COMMANDS  §7— Server configuration",
                "/spawn set — Set spawn here | /spawn set create — Alias",
                "/spawn set <x> <y> <z> — Set exact spawn coordinates",
                "/server preparing — Prepare launch | /server start — Start",
                "/end close / open — Control End access",
                "/nether close / open — Control Nether access",
                "/leaderboards reset confirm — Reset tracked leaderboard progress",
                "/lifesteal admin menu — Open combat / Lifesteal settings",
                "/lifesteal admin giveheart / giverevivebeacon — Grant items",
                "/lifesteal admin sethearts / maxhearts — Heart controls",
                "/lifesteal admin skip — Reset crafting quota",
                "/lifesteal admin containers — Heart storage control",
                "/lifesteal admin pvp / pvploss / naturalloss / deathbans",
                "/lifesteal admin revive / combat status / reset CONFIRM"
            };
            default -> new String[] {
                "§dGENERAL  §7— Everyday player commands",
                "/rules — Server rules | /shop — Open Shard shop",
                "/leaderboards — Player rankings | /teams — Allied Teams",
                "/homes — Saved homes | /home <name> — Visit a home",
                "/sethome <name> — Save home | /delhome <name> — Delete",
                "§dTELEPORTATION  §7— /help teleportation",
                "§dPVP & LIFESTEAL  §7— /help lifesteal",
                "/lifesteal hearts / list / craftstatus — Heart tools",
                "/lifesteal recipe heart / recipe beacon — Recipes",
                "/withdraw <amount> — Withdraw hearts into items",
                "§dSTAFF HELP  §7— Based on your permissions",
                "§b/help moderation §7| §b/help admin §7| §b/help owner",
                "§7Use /help <section> for full commands. No separate Economy menu."
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
