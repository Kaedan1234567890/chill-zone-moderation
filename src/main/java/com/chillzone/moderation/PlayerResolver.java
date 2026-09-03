package com.chillzone.moderation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class PlayerResolver {
    private PlayerResolver() {}

    public static ResolvedPlayer resolve(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return new ResolvedPlayer(
                online.getUUID(),
                online.getGameProfile().name(),
                online
            );
        }

        // Vanilla stores profiles for players who have previously joined in usercache.json.
        // Read it directly so moderation commands can target known offline players on 26.2.
        try {
            Path cache = server.getServerDirectory().resolve("usercache.json");
            if (Files.exists(cache)) {
                try (Reader reader = Files.newBufferedReader(cache)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonArray()) {
                        JsonArray array = parsed.getAsJsonArray();
                        for (JsonElement element : array) {
                            if (!element.isJsonObject()) continue;
                            JsonObject obj = element.getAsJsonObject();
                            if (!obj.has("name") || !obj.has("uuid")) continue;

                            String cachedName = obj.get("name").getAsString();
                            if (!cachedName.equalsIgnoreCase(name)) continue;

                            UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
                            return new ResolvedPlayer(uuid, cachedName, null);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    public record ResolvedPlayer(UUID uuid, String name, ServerPlayer onlinePlayer) {}
}
