package com.bettercontent.betterdeathsdoor.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DeathScreen.class)
public interface DeathCauseAccessor {
    @Accessor("causeOfDeath")
    Component betterDeathsDoor$causeOfDeath();
}
