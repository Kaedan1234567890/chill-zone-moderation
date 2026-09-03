package com.chillzone.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;

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
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer staff = ctx.getSource().getPlayerOrException();
                            PlayerResolver.ResolvedPlayer target = resolve(
                                ctx.getSource().getServer(),
                                StringArgumentType.getString(ctx, "player"),
                                ctx.getSource()
                            );
                            if (target == null) return 0;

                            String reason = StringArgumentType.getString(ctx, "reason");
                            PunishmentRecord rec = store.getOrCreate(target.uuid(), target.name());

                            PunishmentRecord.WarningEntry entry = new PunishmentRecord.WarningEntry();
                            entry.reason = reason;
                            entry.staff = staff.getGameProfile().name();
                            entry.issuedAt = System.currentTimeMillis();
                            rec.warnings.add(entry);
                            store.save();

                            if (target.onlinePlayer() != null) {
                                target.onlinePlayer().sendSystemMessage(
                                    Component.literal("You have received a warning. Reason: " + reason)
                                );
                            }

                            ctx.getSource().sendSuccess(
                                () -> Component.literal("Warned " + target.name() + "."),
                                false
                            );
                            return 1;
                        }))));

            dispatcher.register(Commands.literal("warnings")
                .requires(s -> Permissions.has(s, Permissions.WARNINGS))
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        PlayerResolver.ResolvedPlayer target = resolve(
                            ctx.getSource().getServer(),
                            StringArgumentType.getString(ctx, "player"),
                            ctx.getSource()
                        );
                        if (target == null) return 0;

                        PunishmentRecord rec = store.get(target.uuid());
                        if (rec == null || rec.warnings.isEmpty()) {
                            ctx.getSource().sendSuccess(
                                () -> Component.literal(target.name() + " has no warnings."),
                                false
                            );
                            return 1;
                        }

                        ctx.getSource().sendSuccess(
                            () -> Component.literal("Warnings for " + target.name() + ":"),
                            false
                        );

                        for (int i = 0; i < rec.warnings.size(); i++) {
                            PunishmentRecord.WarningEntry w = rec.warnings.get(i);
                            int num = i + 1;
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("#" + num + " - " + w.reason + " (by " + w.staff + ")"),
                                false
                            );
                        }
                        return 1;
                    })));

            dispatcher.register(Commands.literal("tempban")
                .requires(s -> Permissions.has(s, Permissions.TEMPBAN))
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("duration", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer staff = ctx.getSource().getPlayerOrException();

                                PlayerResolver.ResolvedPlayer target = resolve(
                                    ctx.getSource().getServer(),
                                    StringArgumentType.getString(ctx, "player"),
                                    ctx.getSource()
                                );
                                if (target == null) return 0;

                                long duration = TimeParser.parseMillis(
                                    StringArgumentType.getString(ctx, "duration")
                                );
                                if (duration <= 0) {
                                    ctx.getSource().sendFailure(
                                        Component.literal("Invalid duration. Use examples like 30m, 2h, 2d, 1w.")
                                    );
                                    return 0;
                                }

                                String reason = StringArgumentType.getString(ctx, "reason");
                                PunishmentRecord rec = store.getOrCreate(target.uuid(), target.name());

                                PunishmentRecord.BanEntry ban = new PunishmentRecord.BanEntry();
                                ban.reason = reason;
                                ban.staff = staff.getGameProfile().name();
                                ban.issuedAt = System.currentTimeMillis();
                                ban.expiresAt = ban.issuedAt + duration;
                                rec.ban = ban;
                                store.save();

                                if (target.onlinePlayer() != null) {
                                    target.onlinePlayer().connection.disconnect(banMessage(rec));
                                }

                                ctx.getSource().sendSuccess(
                                    () -> Component.literal("Temporarily banned " + target.name() + "."),
                                    false
                                );
                                return 1;
                            })))));

            dispatcher.register(Commands.literal("czban")
                .requires(s -> Permissions.has(s, Permissions.BAN))
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer staff = ctx.getSource().getPlayerOrException();

                            PlayerResolver.ResolvedPlayer target = resolve(
                                ctx.getSource().getServer(),
                                StringArgumentType.getString(ctx, "player"),
                                ctx.getSource()
                            );
                            if (target == null) return 0;

                            String reason = StringArgumentType.getString(ctx, "reason");
                            PunishmentRecord rec = store.getOrCreate(target.uuid(), target.name());

                            PunishmentRecord.BanEntry ban = new PunishmentRecord.BanEntry();
                            ban.reason = reason;
                            ban.staff = staff.getGameProfile().name();
                            ban.issuedAt = System.currentTimeMillis();
                            ban.expiresAt = 0;
                            rec.ban = ban;
                            store.save();

                            if (target.onlinePlayer() != null) {
                                target.onlinePlayer().connection.disconnect(banMessage(rec));
                            }

                            ctx.getSource().sendSuccess(
                                () -> Component.literal("Permanently banned " + target.name() + "."),
                                false
                            );
                            return 1;
                        }))));

            dispatcher.register(Commands.literal("czunban")
                .requires(s -> Permissions.has(s, Permissions.UNBAN))
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        PlayerResolver.ResolvedPlayer target = resolve(
                            ctx.getSource().getServer(),
                            StringArgumentType.getString(ctx, "player"),
                            ctx.getSource()
                        );
                        if (target == null) return 0;

                        PunishmentRecord rec = store.get(target.uuid());
                        if (rec != null) {
                            rec.ban = null;
                            store.save();
                        }

                        ctx.getSource().sendSuccess(
                            () -> Component.literal("Cleared Chill Zone moderation ban for " + target.name() + "."),
                            false
                        );
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

    private static PlayerResolver.ResolvedPlayer resolve(
        MinecraftServer server,
        String name,
        net.minecraft.commands.CommandSourceStack source
    ) {
        PlayerResolver.ResolvedPlayer target = PlayerResolver.resolve(server, name);
        if (target == null) {
            source.sendFailure(
                Component.literal("Player not found. They must have joined Chill Zone SMP at least once.")
            );
        }
        return target;
    }

    private static Component banMessage(PunishmentRecord rec) {
        PunishmentRecord.BanEntry ban = rec.ban;
        boolean temporary = ban.expiresAt > 0;

        MutableComponent root = Component.literal(
            "You are " + (temporary ? "temporarily banned" : "banned") +
            " from " + config.serverName + ".\n\n" +
            "Reason:\n" + ban.reason + "\n"
        );

        if (temporary) {
            root.append(Component.literal(
                "\nTime Remaining:\n" +
                TimeParser.formatRemaining(
                    ban.expiresAt - System.currentTimeMillis()
                ) + "\n"
            ));
        }

        root.append(Component.literal("\nAppeal:\n"));

        MutableComponent discord = Component.literal(config.discordInvite);
        try {
            discord = discord.withStyle(style ->
                style.withUnderlined(true)
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(config.discordInvite)))
            );
        } catch (Exception ignored) {
        }

        root.append(discord);
        root.append(Component.literal("\n\n" + config.appealMessage));
        return root;
    }
}
