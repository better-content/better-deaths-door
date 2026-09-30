package com.bettercontent.betterdeathsdoor.mixin;
import com.bettercontent.betterdeathsdoor.client.InjuryToastClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(targets="net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class ToastClockMixin {
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/Util;getMillis()J"))
    private long injuryToastTime() { return InjuryToastClock.now(); }
}
