package com.chillzone.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ChillZoneModeration implements ModInitializer {
    private static ModerationConfig config;
    private static ModerationStore store;

    @Override
    public void onInitialize() {
        config = ModerationConfig.load();
        store = ModerationStore.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("warn")
                .requires(s -> Permissions.has(s, Permissions.WARN))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer staff = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String reason = StringArgumentType.getString(ctx, "reason");

                            PunishmentRecord rec = store.getOrCreate(target.getUUID(), target.getGameProfile().name());
                            PunishmentRecord.WarningEntry entry = new PunishmentRecord.WarningEntry();
                            entry.reason = reason;
                            entry.staff = staff.getGameProfile().name();
                            entry.issuedAt = System.currentTimeMillis();
                            rec.warnings.add(entry);
                            store.save();

                            target.sendSystemMessage(Component.literal("You have received a warning. Reason: " + reason));
                            ctx.getSource().sendSuccess(() -> Component.literal("Warned " + target.getGameProfile().name() + "."), false);
                            return 1;
                        }))));

            dispatcher.register(Commands.literal("warnings")
                .requires(s -> Permissions.has(s, Permissions.WARNINGS))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        PunishmentRecord rec = store.get(target.getUUID());
                        if (rec == null || rec.warnings.isEmpty()) {
                            ctx.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().name() + " has no warnings."), false);
                            return 1;
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("Warnings for " + target.getGameProfile().name() + ":"), false);
                        for (int i = 0; i < rec.warnings.size(); i++) {
                            PunishmentRecord.WarningEntry w = rec.warnings.get(i);
                            int num = i + 1;
                            ctx.getSource().sendSuccess(() -> Component.literal("#" + num + " - " + w.reason + " (by " + w.staff + ")"), false);
                        }
                        return 1;
                    })));

            dispatcher.register(Commands.literal("tempban")
                .requires(s -> Permissions.has(s, Permissions.TEMPBAN))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("duration", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer staff = ctx.getSource().getPlayerOrException();
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                long duration = TimeParser.parseMillis(StringArgumentType.getString(ctx, "duration"));
                                if (duration <= 0) {
                                    ctx.getSource().sendFailure(Component.literal("Invalid duration. Use examples like 30m, 2h, 2d, 1w."));
                                    return 0;
                                }
                                String reason = StringArgumentType.getString(ctx, "reason");

                                PunishmentRecord rec = store.getOrCreate(target.getUUID(), target.getGameProfile().name());
                                PunishmentRecord.BanEntry ban = new PunishmentRecord.BanEntry();
                                ban.reason = reason;
                                ban.staff = staff.getGameProfile().name();
                                ban.issuedAt = System.currentTimeMillis();
                                ban.expiresAt = ban.issuedAt + duration;
                                rec.ban = ban;
                                store.save();

                                target.connection.disconnect(banMessage(rec));
                                return 1;
                            })))));

            dispatcher.register(Commands.literal("czban")
                .requires(s -> Permissions.has(s, Permissions.BAN))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer staff = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String reason = StringArgumentType.getString(ctx, "reason");

                            PunishmentRecord rec = store.getOrCreate(target.getUUID(), target.getGameProfile().name());
                            PunishmentRecord.BanEntry ban = new PunishmentRecord.BanEntry();
                            ban.reason = reason;
                            ban.staff = staff.getGameProfile().name();
                            ban.issuedAt = System.currentTimeMillis();
                            ban.expiresAt = 0;
                            rec.ban = ban;
                            store.save();

                            target.connection.disconnect(banMessage(rec));
                            return 1;
                        }))));

            dispatcher.register(Commands.literal("czunban")
                .requires(s -> Permissions.has(s, Permissions.UNBAN))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        PunishmentRecord rec = store.get(target.getUUID());
                        if (rec != null) {
                            rec.ban = null;
                            store.save();
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("Cleared Chill Zone moderation ban for " + target.getGameProfile().name() + "."), false);
                        return 1;
                    })));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            PunishmentRecord rec = store.get(player.getUUID());
            if (rec == null || rec.ban == null) return;

            if (rec.ban.expiresAt > 0 && System.currentTimeMillis() >= rec.ban.expiresAt) {
                rec.ban = null;
                store.save();
                return;
            }

            server.execute(() -> player.connection.disconnect(banMessage(rec)));
        });
    }

    private static Component banMessage(PunishmentRecord rec) {
        PunishmentRecord.BanEntry ban = rec.ban;
        boolean temp = ban.expiresAt > 0;
        StringBuilder sb = new StringBuilder();

        sb.append("You are ").append(temp ? "temporarily banned" : "banned")
          .append(" from ").append(config.serverName).append(".\n\n")
          .append("Reason:\n").append(ban.reason).append("\n");

        if (temp) {
            sb.append("\nTime Remaining:\n")
              .append(TimeParser.formatRemaining(ban.expiresAt - System.currentTimeMillis()))
              .append("\n");
        }

        sb.append("\nAppeal:\n")
          .append(config.discordInvite)
          .append("\n\n")
          .append(config.appealMessage);

        return Component.literal(sb.toString());
    }
}
