package com.bettercontent.betterdeathsdoor.state;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BodyStateTest {
    private static final BodyTuning T = BodyTuning.DEFAULT;
    @Test void positiveHealthNeverMaimsAndCrossingNeverRollsDeathEvenWithCertainOdds() {
        BodyState state = new BodyState();
        assertEquals(BodyState.HitResult.HEALTH_DAMAGE, state.resolveHit(false, Region.TORSO, MaimType.OPENED, 0, () -> fail("Unexpected roll"), T));
        assertTrue(state.activeMaims().isEmpty());
        for (int i = 0; i < 7; i++) state.addMaim(Region.HEAD, MaimType.CRACKED, i);
        assertEquals(BodyState.HitResult.ENTERED_DOOR, state.resolveHit(true, Region.TORSO, MaimType.OPENED, 10, () -> fail("Entry must not roll"), T));
        assertEquals(8, state.activeMaims().size());
        assertEquals(BodyState.HitResult.FINAL_DEATH, state.resolveHit(true, Region.TORSO, MaimType.OPENED, 11, () -> .99, T));
        assertEquals(8, state.activeMaims().size());
    }
    @Test void zeroMaimDoorHitCannotKillAndEachSurvivedHitAddsExactlyOne() {
        BodyState state = new BodyState(); state.enterDoor(0);
        assertEquals(BodyState.HitResult.MAIMED, state.resolveHit(true, Region.HEAD, MaimType.CRACKED, 1, () -> fail("Zero odds need no roll"), T));
        // Pre-hit probability is .15; post-hit would be .30. A .20 roll must survive.
        assertEquals(BodyState.HitResult.MAIMED, state.resolveHit(true, Region.HEAD, MaimType.CRACKED, 1, () -> .20, T));
        assertEquals(2, state.activeMaims().size());
        assertEquals(BodyState.HitResult.FINAL_DEATH, state.resolveHit(true, Region.TORSO, MaimType.OPENED, 1, () -> .20, T));
        assertEquals(2, state.activeMaims().size());
    }
    @Test void healingBoundaryIsFortyTicksAndHitsDoNotRestartIt() {
        BodyState state = new BodyState(); state.enterDoor(100); state.addMaim(Region.LEFT_LEG, MaimType.CRACKED, 100);
        state.enterDoor(120);
        assertFalse(state.tryHeal(139)); assertTrue(state.atDoor());
        assertTrue(state.tryHeal(140)); assertFalse(state.atDoor()); assertEquals(1, state.activeMaims().size());
        state.enterDoor(141); assertEquals(181, state.healingLockedUntil());
    }
    @Test void independentTraumaExpiriesRetainStacksBeyondFunctionalCap() {
        BodyState state = new BodyState();
        for (int i = 0; i < 8; i++) state.addTrauma(i * 10L);
        assertEquals(8, state.traumaCount(1199)); assertEquals(7, state.traumaCount(1200));
        assertEquals(1, BodyRules.functionalMultiplier(state.traumaCount(1200)));
        assertEquals(5, state.traumaCount(1220)); assertEquals(4, state.traumaCount(1230));
        assertTrue(state.expireTrauma(1270)); assertEquals(0, state.traumaCount(1270));
    }
    @Test void curingOldestMatchingInjuryPreservesOtherInjuriesAndHistory() {
        BodyState state = new BodyState();
        Maim old = state.addMaim(Region.LEFT_LEG, MaimType.CRACKED, 2);
        Maim newer = state.addMaim(Region.LEFT_LEG, MaimType.CRACKED, 3);
        state.addMaim(Region.LEFT_LEG, MaimType.BURNT, 1);
        TreatmentRecord cured = state.cureOldest(Region.LEFT_LEG, MaimType.CRACKED, "minecraft:stick", 10).orElseThrow();
        assertEquals(old.id(), cured.maimId()); assertTrue(state.activeMaims().contains(newer));
        assertEquals(2, state.count(Region.LEFT_LEG)); assertEquals(1, state.treatmentHistory().size());
        assertTrue(state.cureOldest(Region.HEAD, MaimType.CRACKED, "minecraft:stick", 10).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> state.activeMaims().clear());
    }
    @Test void persistenceAndCopiesRetainHistoryWithoutSlots() {
        BodyState state = new BodyState(); state.enterDoor(100); state.addTrauma(100);
        for (int i = 0; i < 500; i++) {
            state.addMaim(Region.RIGHT_ARM, MaimType.OPENED, i);
            state.cureOldest(Region.RIGHT_ARM, MaimType.OPENED, "better_deaths_door:soocher", i);
        }
        state.addMaim(Region.TORSO, MaimType.BURNT, 500);
        BodyState restored = BodyState.load(state.save(), 120);
        assertEquals(state.activeMaims(), restored.activeMaims());
        assertEquals(state.treatmentHistory(), restored.treatmentHistory()); assertEquals(500, restored.treatmentHistory().size());
        assertTrue(restored.healingLocked(139)); assertEquals(1, restored.traumaCount(120));
        BodyState copy = restored.copy(); copy.clear(); assertEquals(500, restored.treatmentHistory().size());
        assertTrue(BodyState.load(state.save(), 1300).traumaExpiries().isEmpty());
    }
    @Test void snapshotIsImmutableAndUsesActualTuningForPressure() {
        BodyState state = new BodyState(); state.enterDoor(10); state.addMaim(Region.HEAD, MaimType.BURNT, 10);
        BodyTuning tuning = new BodyTuning(.2, .3, .5, .1, .5, 3, .5, 3, .1, 2, 8.0/3, 40, 1200);
        BodySnapshot snapshot = BodySnapshot.of(UUID.randomUUID(), .01f, 20, state, 11, tuning);
        assertEquals(0, snapshot.health()); assertEquals(.5, snapshot.deathProbability()); assertEquals(39, snapshot.healingLockTicks());
        state.clear(); assertEquals(1, snapshot.activeMaims().size());
    }
}
