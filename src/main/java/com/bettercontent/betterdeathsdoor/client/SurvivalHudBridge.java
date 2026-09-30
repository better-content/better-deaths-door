package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.bettersurvivalhud.client.api.DynamicSurvivalHudClientApi;
import net.minecraftforge.fml.ModList;

/** Optional, typed connection to the client presentation owner. */
final class SurvivalHudBridge {
    private static final boolean AVAILABLE = ModList.get().isLoaded("better_survival_hud");

    static void update(boolean door, float health, double probability) {
        if (AVAILABLE) Present.update(door, health, probability);
    }

    static float alpha(float partial) {
        return AVAILABLE ? Present.alpha(partial) : 1;
    }

    /** Loaded only when the optional provider is present. */
    private static final class Present {
        static void update(boolean door, float health, double probability) {
            DynamicSurvivalHudClientApi.setInjuryHealth(door, health, probability);
        }

        static float alpha(float partial) {
            return DynamicSurvivalHudClientApi.healthAlpha(partial);
        }
    }
}
