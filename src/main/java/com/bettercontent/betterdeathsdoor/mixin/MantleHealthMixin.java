package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.client.ClientRevivalState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mantle owns both its heart drawing and its health history; Forge's hooks cannot affect it. */
@Pseudo
@Mixin(targets = "slimeknights.mantle.client.ExtraHeartRenderHandler", remap = false)
public abstract class MantleHealthMixin {
    @Shadow(remap = false) private int lastHealth;
    @Shadow(remap = false) private int displayHealth;
    @Shadow(remap = false) private long healthBlinkTime;

    private boolean betterDeathsDoor$cameraAtDoor() {
        var camera = Minecraft.getInstance().getCameraEntity();
        return camera instanceof Player player && ClientRevivalState.get(player.getUUID())
                .map(view -> view.atDoor()).orElse(false);
    }

    @Inject(method = "renderHealthbar(Lnet/minecraftforge/client/event/RenderGuiOverlayEvent$Pre;)V",
            at = @At("HEAD"), remap = false, require = 1)
    private void betterDeathsDoor$clearHistory(RenderGuiOverlayEvent.Pre event, CallbackInfo callback) {
        if (betterDeathsDoor$cameraAtDoor()) {
            lastHealth = 0;
            displayHealth = 0;
            healthBlinkTime = 0;
        }
    }

    // Remap only the Minecraft operation; Mantle's entry point remains unremapped.
    @WrapOperation(method = "renderHealthbar(Lnet/minecraftforge/client/event/RenderGuiOverlayEvent$Pre;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getHealth()F", remap = true),
            remap = false, require = 1, expect = 1, allow = 1)
    private float betterDeathsDoor$semanticHealth(Player player, Operation<Float> original) {
        float health = original.call(player);
        return Minecraft.getInstance().getCameraEntity() == player && betterDeathsDoor$cameraAtDoor() ? 0 : health;
    }
}
