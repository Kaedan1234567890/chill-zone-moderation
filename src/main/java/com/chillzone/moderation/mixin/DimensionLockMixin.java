package com.chillzone.moderation.mixin;

import com.chillzone.moderation.ServerStateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class DimensionLockMixin {
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), cancellable = true)
    private void chillzone$blockClosedDimensions(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) {
        Entity self = (Entity)(Object)this;
        if (transition == null || transition.newLevel() == null) return;
        if (self.level().dimension() == transition.newLevel().dimension()) return;

        if (ServerStateManager.blocksDimension(transition.newLevel())) {
            cir.setReturnValue(null);
        }
    }
}
