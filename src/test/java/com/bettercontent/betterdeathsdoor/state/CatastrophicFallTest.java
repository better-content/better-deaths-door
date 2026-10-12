package com.bettercontent.betterdeathsdoor.state;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatastrophicFallTest {
    @Test void inclusiveThresholdScalesWithPreHitMaxHealth() {
        for (float max : new float[]{10, 20, 60, 200}) {
            assertFalse(CatastrophicFall.bypasses(true, Math.nextDown(2 * max), max));
            assertTrue(CatastrophicFall.bypasses(true, 2 * max, max));
            assertTrue(CatastrophicFall.bypasses(true, Math.nextUp(2 * max), max));
            assertFalse(CatastrophicFall.bypasses(false, 1000 * max, max));
        }
    }
    @Test void zeroCanceledMitigatedAndInvalidDamageCannotBypass() {
        assertFalse(CatastrophicFall.bypasses(true, 0, 20));
        assertFalse(CatastrophicFall.bypasses(true, 39, 20));
        assertFalse(CatastrophicFall.bypasses(true, Float.NaN, 20));
        assertFalse(CatastrophicFall.bypasses(true, Float.POSITIVE_INFINITY, 20));
        assertFalse(CatastrophicFall.bypasses(true, 40, 0));
        assertFalse(CatastrophicFall.bypasses(true, 40, Float.NaN));
    }
}
