package com.bettercontent.betterdeathsdoor.state;

/** One explicit set of tunable bodily rules. Region and medicine never select separate debuffs. */
public record BodyTuning(double globalDeathPerMaim, double headDeathPerMaim,
                         double restingFunctionalMultiplier, double traumaIncrement,
                         double torsoLimit, double torsoHalfSaturation,
                         double legLimit, double legHalfSaturation, double armIncrement,
                         double treatmentBaseSeconds, double treatmentArmSeconds,
                         int healingLockTicks, int traumaLifetimeTicks) {
    public static final BodyTuning DEFAULT = new BodyTuning(.05, .10, .5, .1,
        .5, 3, .5, 3, .1, 2, 8.0 / 3.0, 40, 1200);
    public BodyTuning {
        double[] values = {globalDeathPerMaim, headDeathPerMaim, restingFunctionalMultiplier,
            traumaIncrement, torsoLimit, legLimit, armIncrement, treatmentBaseSeconds, treatmentArmSeconds};
        for (double value : values) if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid body coefficient");
        if (!Double.isFinite(torsoHalfSaturation) || torsoHalfSaturation <= 0 || !Double.isFinite(legHalfSaturation)
            || legHalfSaturation <= 0 || healingLockTicks < 0 || traumaLifetimeTicks <= 0
            || restingFunctionalMultiplier > 1 || torsoLimit > 1 || legLimit > .5)
            throw new IllegalArgumentException("Invalid body curve or deadline");
    }
}
