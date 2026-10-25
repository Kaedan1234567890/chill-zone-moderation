package com.chillzone.moderation.mixin;

import com.chillzone.moderation.ServerStateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class GracePvpMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void chillzone$blockGracePvp(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (!ServerStateManager.isGraceActive()) return;
        Entity causing = source.getEntity();
        if (causing instanceof ServerPlayer && causing != (Object)this) {
            cir.setReturnValue(false);
        }
    }
}
