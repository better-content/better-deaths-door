package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.records.LifeRecords;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerPlayer.class)
public abstract class LifeNativeServerPlayerMixin {
    @Inject(method = "setGameMode", at = @At("RETURN"))
    private void life$modeOutcome(GameType mode, CallbackInfoReturnable<Boolean> ci) {
        LifeRecords.observeLevel((ServerPlayer) (Object) this);
    }
    @Inject(method = {"setExperienceLevels", "setExperiencePoints", "giveExperienceLevels"}, at = @At("RETURN"))
    private void life$levelOutcome(int amount, CallbackInfo ci) {
        LifeRecords.observeLevel((ServerPlayer) (Object) this);
    }
}
