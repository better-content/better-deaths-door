package com.bettercontent.betterdeathsdoor;

import com.bettercontent.betterdeathsdoor.gametest.RevivalGameTests;
import com.bettercontent.betterdeathsdoor.client.InjuryClientConfig;
import com.bettercontent.betterdeathsdoor.network.RevivalNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(RevivalMod.MOD_ID)
public final class RevivalMod {
    public static final String MOD_ID = "better_deaths_door";
    public RevivalMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RevivalConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, InjuryClientConfig.SPEC);
        InjuryItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerGameTests);
        RevivalNetwork.register();
        MinecraftForge.EVENT_BUS.register(RevivalForgeEvents.class);
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        event.register(RevivalGameTests.class);
    }
}
