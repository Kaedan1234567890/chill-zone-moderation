package com.chillzone.moderation;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.nio.file.*;
import java.util.*;

public final class PlayerResolver {
    private static final String HISTORY_FILENAME = "chillzone-moderation/known-players.json";

    /** Saved indefinitely once a player has connected. */
    public static void remember(MinecraftServer server, ServerPlayer player) {
        try {
            Path file = historyPath(server);
            JsonObject history = readHistory(file);
            history.addProperty(player.getUUID().toString(), player.getGameProfile().name());
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.writeString(temporary, history.toString());
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[Chill Zone Moderation] Could not save player history: " + e.getMessage());
        }
    }

    private static Path historyPath(MinecraftServer server) {
        return server.getServerDirectory().resolve("config").resolve(HISTORY_FILENAME);
    }

    private static JsonObject readHistory(Path path) {
        if (!Files.exists(path)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception ignored) { return new JsonObject(); }
    }

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
        JsonObject history = readHistory(historyPath(server));
        for (Map.Entry<String, JsonElement> entry : history.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                String name = entry.getValue().getAsString();
                if (!name.isBlank()) out.putIfAbsent(uuid, new CachedPlayer(uuid, name));
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(out.values());
    }

    public record CachedPlayer(UUID uuid, String name) {}
    public record ResolvedPlayer(UUID uuid, String name, ServerPlayer onlinePlayer) {}
}
