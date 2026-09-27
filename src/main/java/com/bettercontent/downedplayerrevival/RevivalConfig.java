package com.bettercontent.downedplayerrevival;

import com.bettercontent.downedplayerrevival.state.BodyTuning;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

/** All combat tuning is server-owned and travels with the body snapshot. */
public final class RevivalConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue GLOBAL_DEATH, HEAD_DEATH, RESTING_EFFECT, TRAUMA_INCREMENT,
        TORSO_LIMIT, TORSO_K, LEG_LIMIT, LEG_K, ARM_INCREMENT, TREATMENT_BASE, TREATMENT_ARM, INTERACTION_DISTANCE;
    public static final ForgeConfigSpec.IntValue HEALING_LOCK, TRAUMA_LIFETIME;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TYPE_OVERRIDES, BYPASS_TYPES;
    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("deathsDoor");
        GLOBAL_DEATH = b.defineInRange("deathPerMaim", .05, .000001, 1);
        HEAD_DEATH = b.defineInRange("additionalDeathPerHeadMaim", .1, .000001, 1);
        HEALING_LOCK = b.comment("All HP healing is rejected for this many ticks after each zero crossing.").defineInRange("healingLockTicks", 40, 0, 1200);
        TRAUMA_LIFETIME = b.defineInRange("traumaLifetimeTicks", 1200, 1, 72000);
        RESTING_EFFECT = b.defineInRange("restingFunctionalMultiplier", .5, 0, 1);
        TRAUMA_INCREMENT = b.defineInRange("functionalIncrementPerTrauma", .1, 0, 1);
        TORSO_LIMIT = b.defineInRange("torsoMaximumReduction", .5, 0, .5);
        TORSO_K = b.defineInRange("torsoHalfSaturation", 3., .01, 1000);
        LEG_LIMIT = b.defineInRange("eachLegMaximumReduction", .5, 0, .5);
        LEG_K = b.defineInRange("legHalfSaturation", 3., .01, 1000);
        ARM_INCREMENT = b.defineInRange("armReductionPerMaim", .1, 0, 1);
        TREATMENT_BASE = b.defineInRange("treatmentBaseSeconds", 2., .05, 120);
        TREATMENT_ARM = b.defineInRange("treatmentAddedSecondsPerArmMaim", 8. / 3., 0, 120);
        INTERACTION_DISTANCE = b.defineInRange("treatmentDistance", 3., .1, 8);
        TYPE_OVERRIDES = b.comment("Damage registry ID=CRACKED|BURNT|OPENED|LOW_OXYGEN. Overrides precede damage/weapon tags.")
            .defineListAllowEmpty("damageTypeOverrides", List.of(), value -> value instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+=(CRACKED|BURNT|OPENED|LOW_OXYGEN)"));
        BYPASS_TYPES = b.comment("Registry IDs or existing explicit special-kill message IDs; bypass borrowed time.")
            .defineListAllowEmpty("bypassDamageTypes", List.of("gorgon", "sgcraft:transient", "sgcraft:iris", "vampirism_dbno", "hordes:infection", "minecraft:generic_kill", "minecraft:out_of_world"), value -> value instanceof String s && !s.isBlank());
        b.pop(); SPEC = b.build();
    }
    public static BodyTuning tuning() {
        return new BodyTuning(GLOBAL_DEATH.get(), HEAD_DEATH.get(), RESTING_EFFECT.get(), TRAUMA_INCREMENT.get(),
            TORSO_LIMIT.get(), TORSO_K.get(), LEG_LIMIT.get(), LEG_K.get(), ARM_INCREMENT.get(),
            TREATMENT_BASE.get(), TREATMENT_ARM.get(), HEALING_LOCK.get(), TRAUMA_LIFETIME.get());
    }
    private RevivalConfig() {}
}
