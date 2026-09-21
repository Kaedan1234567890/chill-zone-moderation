package com.chillzone.moderation.mixin;

import com.chillzone.moderation.ChillZoneModeration;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/**
 * Enforces Chill Zone bans at Minecraft's login admission gate.
 * This runs before the player is placed into the world.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void chillZone$checkBlacklist(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
        Component blocked = ChillZoneModeration.blacklistMessage(profile.id());
        if (blocked != null) cir.setReturnValue(blocked);
    }
}
