package com.chillzone.sus;

import com.chillzone.sus.data.SusStore;
import com.chillzone.sus.detect.SusDetector;
import com.chillzone.sus.detect.GrimSusBridge;
import com.chillzone.sus.permission.Permissions;
import com.chillzone.sus.ui.SusMenu;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ChillZoneSus implements ModInitializer {
    public static final String MOD_ID = "chill_zone_sus";
    private static SusStore store;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            store = SusStore.load(server);
            SusDetector.init(store);
            GrimSusBridge.init(this, store);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (store != null) {
                store.tick(server);
                if (server.getTickCount() % 20 == 0) SusDetector.refreshAll(System.currentTimeMillis());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (store != null) store.save(server);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("sus")
                .requires(source -> Permissions.has(source, Permissions.VIEW))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    SusMenu.open(player, store);
                    return 1;
                })
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer staff = ctx.getSource().getPlayerOrException();
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        var record = store.getOrCreate(target.getUUID(), target.getGameProfile().name());
                        String type = record.hasHackActivity() ? "hacks" : (record.diamond.suspicionScore > 0 ? "diamond" : "debris");
                        SusMenu.openPlayer(staff, target, store, type);
                        return 1;
                    }))
            );

            dispatcher.register(Commands.literal("susclear")
                .requires(source -> Permissions.has(source, Permissions.CLEAR))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        store.clearActive(target.getUUID(), target.getGameProfile().name());
                        store.save(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("Cleared active SUS flags for " + target.getGameProfile().name() + "."),
                            false
                        );
                        return 1;
                    }))
            );
        });
    }
}
