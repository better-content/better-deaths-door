package com.bettercontent.betterdeathsdoor.client;
import net.minecraftforge.common.ForgeConfigSpec;
public final class InjuryClientConfig {
 public static final ForgeConfigSpec SPEC;
 public static final ForgeConfigSpec.BooleanValue REDUCED_MOTION,SOUND;
 static {var b=new ForgeConfigSpec.Builder();REDUCED_MOTION=b.comment("Reduce bodily screen transitions and edge impulses.").define("reducedMotion",false);SOUND=b.comment("Play injury, treatment and death-pressure sounds.").define("injurySounds",true);SPEC=b.build();}
}
