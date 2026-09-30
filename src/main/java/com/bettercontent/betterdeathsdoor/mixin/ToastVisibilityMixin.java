package com.bettercontent.betterdeathsdoor.mixin;
import com.bettercontent.betterdeathsdoor.client.InjuryToastClock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ToastComponent.class)
public abstract class ToastVisibilityMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void injuryReserveControls(GuiGraphics graphics, CallbackInfo callback) {
        if (InjuryToastClock.updateAndBlocked()) callback.cancel();
    }
}
