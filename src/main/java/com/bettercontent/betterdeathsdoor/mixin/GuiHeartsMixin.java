package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.client.ClientRevivalState;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Prevent the engine's tiny survival sentinel from producing a half heart. */
@Mixin(Gui.class)
public abstract class GuiHeartsMixin {
    private boolean revival$atDoor(Player player) {
        return ClientRevivalState.get(player.getUUID()).map(view -> view.atDoor()).orElse(false);
    }
    @ModifyVariable(method = "renderHearts", at = @At("HEAD"), argsOnly = true, index = 8)
    private int revival$emptyCurrentHearts(int health, net.minecraft.client.gui.GuiGraphics graphics,
            Player player) {
        return revival$atDoor(player) ? 0 : health;
    }
    @ModifyVariable(method = "renderHearts", at = @At("HEAD"), argsOnly = true, index = 9)
    private int revival$emptyRememberedHearts(int health, net.minecraft.client.gui.GuiGraphics graphics,
            Player player) {
        return revival$atDoor(player) ? 0 : health;
    }
}
