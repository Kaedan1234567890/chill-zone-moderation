package com.chillzone.sus.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class Permissions {
    public static final String VIEW = "chillzonesus.command.sus";
    public static final String TELEPORT = "chillzonesus.teleport";
    public static final String SPECTATE = "chillzonesus.spectate";
    public static final String CLEAR = "chillzonesus.clear";

    private Permissions() {}

    public static boolean has(CommandSourceStack source, String permission) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        return has(player, permission);
    }

    public static boolean has(ServerPlayer player, String permission) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            return user != null &&
                user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
