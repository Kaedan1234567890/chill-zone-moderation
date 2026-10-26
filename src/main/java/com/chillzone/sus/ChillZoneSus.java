package com.chillzone.sus;

import com.chillzone.moderation.PlayerResolver;
import com.chillzone.sus.data.SusRecord;
import com.chillzone.sus.data.SusStore;
import com.chillzone.sus.detect.AntiFlyEvidenceBridge;
import com.chillzone.sus.detect.SusDetector;
import com.chillzone.sus.permission.Permissions;
import com.chillzone.sus.ui.SusMenu;
import com.chillzone.sus.SusInvestigation;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ChillZoneSus implements ModInitializer {
    public static final String MOD_ID = "chill_zone_sus";
    private static SusStore store;

    private static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (ctx, builder) -> {
        Set<String> names = new LinkedHashSet<>();
        for (PlayerResolver.CachedPlayer p : PlayerResolver.knownPlayers(ctx.getSource().getServer())) {
            if (p.name() != null) names.add(p.name());
        }
        if (store != null) {
            for (SusRecord r : store.all()) {
                if (r.lastKnownName != null) names.add(r.lastKnownName);
            }
        }
        String prefix = builder.getRemainingLowerCase();
        for (String name : names) if (name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) builder.suggest(name);
        return builder.buildFuture();
    };

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            store = SusStore.load(server);
            SusDetector.init(store);
            AntiFlyEvidenceBridge.init(store);
            System.out.println("[Chill Zone SUS] Ready: " + store.trackedCount() + " tracked players; " + store.flaggedCount() + " flagged cases. AntiFly installed: " + FabricLoader.getInstance().isModLoaded("antifly"));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (store != null) {
                store.tick(server);
                AntiFlyEvidenceBridge.tick(server);
                if (server.getTickCount() % 20 == 0) {
                    SusDetector.refreshAll(System.currentTimeMillis());
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (store != null) store.save(server);
            SusInvestigation.clear();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("sus")
                .requires(source -> Permissions.has(source, Permissions.VIEW))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (store == null) { ctx.getSource().sendFailure(Component.literal("SUS data not loaded yet.")); return 0; }
                    SusMenu.open(player, store);
                    return 1;
                })
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        if (store == null) {
                            ctx.getSource().sendFailure(Component.literal("SUS store has not initialized."));
                            return 0;
                        }
                        boolean antifly = FabricLoader.getInstance().isModLoaded("antifly");
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "SUS: " + store.flaggedCount() + " flagged players / " + store.trackedCount() + " tracked. "
                            + "Observed breaks since start: " + SusDetector.observedBreaks()
                            + ", diamond/debris breaks: " + SusDetector.observedOreBreaks()
                            + ". AntiFly installed: " + antifly
                            + ". Only qualifying suspicion events appear in the list."
                        ), false);
                        return 1;
                    }))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(KNOWN_PLAYERS)
                    .executes(ctx -> {
                        ServerPlayer staff = ctx.getSource().getPlayerOrException();
                        String requested = StringArgumentType.getString(ctx, "player");
                        PlayerResolver.ResolvedPlayer resolved = PlayerResolver.resolve(ctx.getSource().getServer(), requested);
                        SusRecord record;
                        if (resolved != null) {
                            record = store.getOrCreate(resolved.uuid(), resolved.name());
                        } else {
                            record = store.findByName(requested);
                        }
                        if (record == null) {
                            ctx.getSource().sendFailure(Component.literal(
                                "Player not found. They must have joined Chill Zone SMP at least once."
                            ));
                            return 0;
                        }
                        SusMenu.openPlayer(staff, record.uuid, record.lastKnownName, store);
                        return 1;
                    }))
            );

            dispatcher.register(Commands.literal("susback")
                .requires(source -> Permissions.has(source, Permissions.TELEPORT))
                .executes(ctx -> SusInvestigation.back(ctx.getSource().getPlayerOrException()) ? 1 : 0));

            dispatcher.register(Commands.literal("susclear")
                .requires(source -> Permissions.has(source, Permissions.CLEAR))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(KNOWN_PLAYERS)
                    .executes(ctx -> {
                        String requested = StringArgumentType.getString(ctx, "player");
                        PlayerResolver.ResolvedPlayer resolved = PlayerResolver.resolve(ctx.getSource().getServer(), requested);
                        SusRecord record;
                        if (resolved != null) {
                            record = store.getOrCreate(resolved.uuid(), resolved.name());
                        } else {
                            record = store.findByName(requested);
                        }
                        if (record == null) {
                            ctx.getSource().sendFailure(Component.literal(
                                "Player not found. They must have joined Chill Zone SMP at least once."
                            ));
                            return 0;
                        }
                        store.clearActive(record.uuid, record.lastKnownName);
                        store.save(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("Cleared ALL SUS evidence and saved locations for " + record.lastKnownName + "."),
                            false
                        );
                        return 1;
                    })
                    .then(Commands.argument("category", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String prefix = builder.getRemainingLowerCase();
                            for (String option : new String[]{"all", "mining", "fly", "speed", "elytra"})
                                if (option.startsWith(prefix)) builder.suggest(option);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            if (store == null) return 0;
                            String requested = StringArgumentType.getString(ctx, "player");
                            String category = StringArgumentType.getString(ctx, "category");
                            PlayerResolver.ResolvedPlayer resolved = PlayerResolver.resolve(ctx.getSource().getServer(), requested);
                            SusRecord record = resolved == null ? store.findByName(requested)
                                : store.getOrCreate(resolved.uuid(), resolved.name());
                            if (record == null || !store.clearCategory(record.uuid, record.lastKnownName, category)) {
                                ctx.getSource().sendFailure(Component.literal("Unknown player or category. Use all, mining, fly, speed, or elytra."));
                                return 0;
                            }
                            store.save(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + category + " SUS evidence for " + record.lastKnownName + "."), false);
                            return 1;
                        })))
            );
        });
    }
}
