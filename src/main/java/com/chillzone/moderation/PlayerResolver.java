package com.chillzone.moderation;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.nio.file.*;
import java.util.*;

public final class PlayerResolver {
    private PlayerResolver() {}

    public static ResolvedPlayer resolve(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return new ResolvedPlayer(online.getUUID(), online.getGameProfile().name(), online);
        for (CachedPlayer p : knownPlayers(server)) {
            if (p.name().equalsIgnoreCase(name)) return new ResolvedPlayer(p.uuid(), p.name(), null);
        }
        return null;
    }

    public static List<CachedPlayer> knownPlayers(MinecraftServer server) {
        LinkedHashMap<UUID, CachedPlayer> out = new LinkedHashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            out.put(p.getUUID(), new CachedPlayer(p.getUUID(), p.getGameProfile().name()));
        }
        try {
            Path cache = server.getServerDirectory().resolve("usercache.json");
            if (Files.exists(cache)) try (Reader reader = Files.newBufferedReader(cache)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonArray()) for (JsonElement element : parsed.getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject obj = element.getAsJsonObject();
                    if (!obj.has("name") || !obj.has("uuid")) continue;
                    try {
                        UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
                        out.putIfAbsent(uuid, new CachedPlayer(uuid, obj.get("name").getAsString()));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(out.values());
    }

    public record CachedPlayer(UUID uuid, String name) {}
    public record ResolvedPlayer(UUID uuid, String name, ServerPlayer onlinePlayer) {}
}
