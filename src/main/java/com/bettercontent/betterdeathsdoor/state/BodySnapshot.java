package com.bettercontent.betterdeathsdoor.state;

import java.util.List;
import java.util.UUID;

/** Immutable presentation data. History may be a page in screen-specific transport. */
public record BodySnapshot(UUID playerId, float health, float maxHealth, boolean atDoor,
                           long healingLockedUntil, long serverTick, List<Maim> activeMaims,
                           List<TreatmentRecord> treatmentHistory, int traumaCount, BodyTuning tuning) {
    public BodySnapshot { activeMaims = List.copyOf(activeMaims); treatmentHistory = List.copyOf(treatmentHistory); }
    public BodySnapshot(UUID playerId, float health, float maxHealth, boolean atDoor,
                        long healingLockedUntil, long serverTick, List<Maim> activeMaims,
                        List<TreatmentRecord> treatmentHistory, int traumaCount) {
        this(playerId, health, maxHealth, atDoor, healingLockedUntil, serverTick, activeMaims, treatmentHistory, traumaCount, BodyTuning.DEFAULT);
    }
    public static BodySnapshot of(UUID id, float health, float maxHealth, BodyState state, long now, BodyTuning tuning) {
        return new BodySnapshot(id, state.atDoor() ? 0 : health, maxHealth, state.atDoor(),
            state.healingLockedUntil(), now, state.activeMaims(), state.treatmentHistory(), state.traumaCount(now), tuning);
    }
    public static BodySnapshot of(UUID id, float health, float maxHealth, BodyState state, long now) {
        return new BodySnapshot(id, state.atDoor() ? 0 : health, maxHealth, state.atDoor(),
            state.healingLockedUntil(), now, state.activeMaims(), state.treatmentHistory(), state.traumaCount(now));
    }
    /** Compare only the pre-hit injury set, never attribute a newly added maim to trauma. */
    public boolean traumaAmplifies(BodySnapshot before) {
        if (traumaCount <= before.traumaCount || !playerId.equals(before.playerId)) return false;
        BodySnapshot sameInjuries = new BodySnapshot(playerId, health, maxHealth, atDoor,
            healingLockedUntil, serverTick, before.activeMaims, before.treatmentHistory, traumaCount, before.tuning);
        for (Region region : Region.values())
            if (region != Region.HEAD && before.count(region) > 0
                && sameInjuries.regionalReduction(region) > before.regionalReduction(region)) return true;
        return false;
    }
    public int count(Region region) { return (int) activeMaims.stream().filter(m -> m.region() == region).count(); }
    public double deathProbability() { return BodyRules.deathProbability(activeMaims.size(), count(Region.HEAD), tuning); }
    public double functionalMultiplier() { return BodyRules.functionalMultiplier(traumaCount, tuning); }
    public double regionalReduction(Region region) {
        return switch(region) {
            case HEAD -> Math.min(1, count(region) * tuning.headDeathPerMaim());
            case TORSO -> BodyRules.torsoReduction(count(region), traumaCount, tuning);
            case LEFT_LEG, RIGHT_LEG -> BodyRules.legReduction(count(region), traumaCount, tuning);
            case LEFT_ARM, RIGHT_ARM -> BodyRules.armReduction(count(Region.LEFT_ARM) + count(Region.RIGHT_ARM), traumaCount, tuning);
        };
    }
    public double treatmentSeconds() { return BodyRules.treatmentSeconds(count(Region.LEFT_ARM) + count(Region.RIGHT_ARM), traumaCount, tuning); }
    public long healingLockTicks() { return Math.max(0, healingLockedUntil - serverTick); }
}
