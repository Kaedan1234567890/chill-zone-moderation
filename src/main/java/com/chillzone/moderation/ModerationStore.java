package com.chillzone.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ModerationStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, PunishmentRecord> records = new ConcurrentHashMap<>();

    public static ModerationStore load() {
        ModerationStore store = new ModerationStore();
        Path path = file();
        if (!Files.exists(path)) return store;

        try (Reader r = Files.newBufferedReader(path)) {
            List<PunishmentRecord> loaded = GSON.fromJson(r, new TypeToken<List<PunishmentRecord>>(){}.getType());
            if (loaded != null) {
                for (PunishmentRecord rec : loaded) {
                    if (rec.uuid != null) {
                        if (rec.warnings == null) rec.warnings = new ArrayList<>();
                        store.records.put(rec.uuid, rec);
                    }
                }
            }
        } catch (Exception ignored) {}
        return store;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(new ArrayList<>(records.values()), w);
            }
        } catch (Exception ignored) {}
    }

    public PunishmentRecord getOrCreate(UUID uuid, String name) {
        PunishmentRecord r = records.computeIfAbsent(uuid, k -> {
            PunishmentRecord n = new PunishmentRecord();
            n.uuid = uuid;
            return n;
        });
        r.lastKnownName = name;
        return r;
    }

    public PunishmentRecord get(UUID uuid) {
        return records.get(uuid);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chill-zone-moderation-data.json");
    }
}
