package com.chillzone.moderation.mixin;

import com.chillzone.moderation.ChillZoneModeration;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/** Rejects Chill Zone blacklist entries at Minecraft's admission gate, before world entry. */
@Mixin(PlayerList.class)
public abstract class PlayerListBlacklistMixin {
    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void chillzone$checkBlacklist(SocketAddress address, NameAndId nameAndId, CallbackInfoReturnable<Component> cir) {
        Component rejection = ChillZoneModeration.blacklistMessage(nameAndId.id());
        if (rejection != null) cir.setReturnValue(rejection);
    }
}
