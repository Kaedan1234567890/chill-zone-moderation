package com.chillzone.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModerationConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String serverName = "Chill Zone SMP";
    public String discordInvite = "https://discord.gg/YOURCODE";
    public String appealMessage = "Join our Discord and create a ticket to submit an appeal.";

    public static ModerationConfig load() {
        Path path = file();
        if (!Files.exists(path)) {
            ModerationConfig cfg = new ModerationConfig();
            cfg.save();
            return cfg;
        }
        try (Reader r = Files.newBufferedReader(path)) {
            ModerationConfig cfg = GSON.fromJson(r, ModerationConfig.class);
            return cfg == null ? new ModerationConfig() : cfg;
        } catch (Exception e) {
            return new ModerationConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(this, w);
            }
        } catch (Exception ignored) {}
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chill-zone-moderation.json");
    }
}
