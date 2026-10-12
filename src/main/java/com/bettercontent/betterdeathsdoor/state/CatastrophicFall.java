package com.bettercontent.betterdeathsdoor.state;

/** Only final native HP damage counts: armor, resistance, absorption and Forge admission precede this. */
public final class CatastrophicFall {
    private CatastrophicFall() {}
    public static boolean bypasses(boolean fall, float acceptedHpDamage, float preHitMaxHealth) {
        return fall && Float.isFinite(acceptedHpDamage) && Float.isFinite(preHitMaxHealth)
            && preHitMaxHealth > 0 && acceptedHpDamage >= 2.0 * preHitMaxHealth;
    }
}
