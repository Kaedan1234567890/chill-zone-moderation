package com.chillzone.moderation;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class Permissions {
    public static final String WARN = "chillzonemoderation.warn";
    public static final String WARNINGS = "chillzonemoderation.warnings";
    public static final String TEMPBAN = "chillzonemoderation.tempban";
    public static final String BAN = "chillzonemoderation.ban";
    public static final String UNBAN = "chillzonemoderation.unban";
    public static final String MUTE = "chillzonemoderation.mute";
    public static final String FREEZE = "chillzonemoderation.freeze";

    public static boolean has(CommandSourceStack source, String permission) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            return user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
