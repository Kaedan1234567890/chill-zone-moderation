package com.chillzone.moderation;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public final class PlayerResolver {
    private PlayerResolver() {}

    public static ResolvedPlayer resolve(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return new ResolvedPlayer(online.getUUID(), online.getGameProfile().name(), online);
        }

        try {
            Optional<GameProfile> cached = server.getProfileCache().get(name);
            if (cached.isPresent()) {
                GameProfile profile = cached.get();
                return new ResolvedPlayer(profile.id(), profile.name(), null);
            }
        } catch (Exception ignored) {}

        return null;
    }

    public record ResolvedPlayer(UUID uuid, String name, ServerPlayer onlinePlayer) {}
}
