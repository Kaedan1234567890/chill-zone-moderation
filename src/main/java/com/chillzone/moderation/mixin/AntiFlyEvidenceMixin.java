package com.chillzone.moderation.mixin;

import com.chillzone.sus.detect.AntiFlyEvidenceBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Soft integration with AntiFly 1.1.4.
 *
 * AntiFly continues doing all detection. These hooks intercept only the action
 * points, record the evidence in /sus, and cancel the setback/suppression so the
 * anti-cheat becomes evidence-only for Chill Zone SMP.
 */
@Pseudo
@Mixin(targets = "com.antifly.fabric.AntiFlyFabric", remap = false)
public abstract class AntiFlyEvidenceMixin {

    @Inject(method = "applyHungerMode", at = @At("HEAD"), cancellable = true, remap = false)
    private void chillzone$disableHungerPunishment(
        ServerPlayer player,
        @Coerce Object state,
        Vec3 position,
        CallbackInfo ci
    ) {
        // Chill Zone uses AntiFly as evidence only. Never let Hunger Mode drain
        // food/health if it is accidentally enabled in AntiFly's config.
        ci.cancel();
    }

    @Inject(method = "rubberBand", at = @At("HEAD"), cancellable = true, remap = false)
    private void chillzone$observePlayerSetback(
        ServerPlayer player,
        @Coerce Object state,
        Vec3 target,
        String reason,
        double actual,
        double allowed,
        CallbackInfo ci
    ) {
        AntiFlyEvidenceBridge.record(player, reason, actual, allowed);
        ci.cancel();
    }

    @Inject(method = "rubberBandVehicle", at = @At("HEAD"), cancellable = true, remap = false)
    private void chillzone$observeVehicleSetback(
        ServerPlayer player,
        @Coerce Object state,
        Vec3 target,
        String reason,
        double actual,
        double allowed,
        CallbackInfo ci
    ) {
        AntiFlyEvidenceBridge.record(player, reason, actual, allowed);
        ci.cancel();
    }

    @Inject(method = "failGlide", at = @At("HEAD"), cancellable = true, remap = false)
    private void chillzone$observeElytraFailure(
        ServerPlayer player,
        @Coerce Object state,
        Vec3 pos,
        String reason,
        double actual,
        double allowed,
        CallbackInfoReturnable<Boolean> cir
    ) {
        AntiFlyEvidenceBridge.record(player, reason, actual, allowed);
        // Returning true tells AntiFly the glide may continue. This prevents the
        // normal stopFallFlying + suppression action while preserving detection.
        cir.setReturnValue(true);
    }
}
