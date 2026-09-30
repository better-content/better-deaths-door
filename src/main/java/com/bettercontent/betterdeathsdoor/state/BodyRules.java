package com.bettercontent.betterdeathsdoor.state;

/** Pure arithmetic; the server owns all inputs and random decisions. */
public final class BodyRules {
    private BodyRules() {}
    public static double deathProbability(int maims, int headMaims) { return deathProbability(maims, headMaims, BodyTuning.DEFAULT); }
    public static double deathProbability(int maims, int headMaims, BodyTuning t) {
        return Math.min(1, Math.max(0, maims) * t.globalDeathPerMaim() + Math.max(0, headMaims) * t.headDeathPerMaim());
    }
    public static double functionalMultiplier(int trauma) { return functionalMultiplier(trauma, BodyTuning.DEFAULT); }
    public static double functionalMultiplier(int trauma, BodyTuning t) {
        return Math.min(1, t.restingFunctionalMultiplier() + Math.max(0, trauma) * t.traumaIncrement());
    }
    public static double torsoReduction(int maims, int trauma) { return torsoReduction(maims, trauma, BodyTuning.DEFAULT); }
    public static double torsoReduction(int maims, int trauma, BodyTuning t) {
        return functionalMultiplier(trauma, t) * saturating(maims, t.torsoLimit(), t.torsoHalfSaturation());
    }
    public static double legReduction(int maims, int trauma) { return legReduction(maims, trauma, BodyTuning.DEFAULT); }
    public static double legReduction(int maims, int trauma, BodyTuning t) {
        return functionalMultiplier(trauma, t) * saturating(maims, t.legLimit(), t.legHalfSaturation());
    }
    public static double movementReduction(int left, int right, int trauma) { return movementReduction(left, right, trauma, BodyTuning.DEFAULT); }
    public static double movementReduction(int left, int right, int trauma, BodyTuning t) {
        return Math.min(1, legReduction(left, trauma, t) + legReduction(right, trauma, t));
    }
    public static double armReduction(int combinedArms, int trauma) { return armReduction(combinedArms, trauma, BodyTuning.DEFAULT); }
    public static double armReduction(int combinedArms, int trauma, BodyTuning t) {
        return functionalMultiplier(trauma, t) * Math.min(1, Math.max(0, combinedArms) * t.armIncrement());
    }
    public static double treatmentSeconds(int combinedArms, int trauma) { return treatmentSeconds(combinedArms, trauma, BodyTuning.DEFAULT); }
    public static double treatmentSeconds(int combinedArms, int trauma, BodyTuning t) {
        return t.treatmentBaseSeconds() + t.treatmentArmSeconds() * Math.max(0, combinedArms) * functionalMultiplier(trauma, t);
    }
    private static double saturating(int count, double limit, double half) {
        double n = Math.max(0, count);
        return limit * n / (n + half);
    }
}
