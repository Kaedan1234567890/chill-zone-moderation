package com.chillzone.moderation.mixin;

import com.chillzone.moderation.ServerStateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

/** Player teleport paths are distinct from Entity.teleport in vanilla. */
@Mixin(ServerPlayer.class)
public abstract class PlayerDimensionLockMixin {
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), cancellable = true, require = 0)
    private void chillzone$denyPlayerTransition(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        if (transition != null && transition.newLevel() != null
            && self.level().dimension() != transition.newLevel().dimension()
            && ServerStateManager.blocksDimension(transition.newLevel())) {
            cir.setReturnValue(self);
        }
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void chillzone$denyDirectPlayerTeleport(ServerLevel target, double x, double y, double z,
            Set<Relative> relatives, float yaw, float pitch, boolean resetCamera,
            CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        if (target != null && self.level().dimension() != target.dimension()
            && ServerStateManager.blocksDimension(target)) {
            cir.setReturnValue(false);
        }
    }
}
