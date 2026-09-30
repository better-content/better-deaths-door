package com.bettercontent.betterdeathsdoor.state;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyRulesTest {
    @Test void probabilityUsesAllMaimsAndAdditionalHeadPressure() {
        assertEquals(0, BodyRules.deathProbability(0, 0));
        assertEquals(.05, BodyRules.deathProbability(1, 0), 1e-12);
        assertEquals(.15, BodyRules.deathProbability(1, 1), 1e-12);
        assertEquals(1, BodyRules.deathProbability(7, 7));
        for (int n = 0; n < 19; n++) assertTrue(BodyRules.deathProbability(n + 1, 0) > BodyRules.deathProbability(n, 0));
    }
    @Test void traumaScalesFunctionButIsNotAnInputToDeathOdds() {
        assertEquals(.5, BodyRules.functionalMultiplier(0));
        assertEquals(.8, BodyRules.functionalMultiplier(3), 1e-12);
        assertEquals(1, BodyRules.functionalMultiplier(5));
        assertEquals(1, BodyRules.functionalMultiplier(100));
        assertEquals(.125, BodyRules.torsoReduction(3, 0), 1e-12);
        assertEquals(.25, BodyRules.torsoReduction(3, 5), 1e-12);
    }
    @Test void legsAddIndependentlyAndApproachButDoNotReachImmobility() {
        assertEquals(.25, BodyRules.movementReduction(3, 0, 5), 1e-12);
        assertEquals(.5, BodyRules.movementReduction(3, 3, 5), 1e-12);
        assertTrue(BodyRules.movementReduction(1_000_000, 1_000_000, 5) < 1);
        assertTrue(BodyRules.movementReduction(1_000_000, 1_000_000, 5) > .999);
        assertTrue(BodyRules.torsoReduction(1_000_000, 5) < .5);
    }
    @Test void armCapDoesNotCapTreatmentTime() {
        assertEquals(.3, BodyRules.armReduction(3, 5), 1e-12);
        assertEquals(.5, BodyRules.armReduction(100, 0));
        assertEquals(1, BodyRules.armReduction(100, 5));
        assertEquals(2, BodyRules.treatmentSeconds(0, 0));
        assertEquals(6, BodyRules.treatmentSeconds(3, 0));
        assertEquals(10, BodyRules.treatmentSeconds(3, 5));
        assertTrue(BodyRules.treatmentSeconds(100, 5) > BodyRules.treatmentSeconds(10, 5));
    }
}
