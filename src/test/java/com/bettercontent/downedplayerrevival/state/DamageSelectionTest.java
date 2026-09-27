package com.bettercontent.downedplayerrevival.state;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DamageSelectionTest {
    @Test void originAndElevationBiasOnlyCorrespondingRegions() {
        Map<Region,Double> leftAbove = DamageSelection.regionWeights(new DamageSelection.Context(-1, 40, false, false));
        assertEquals(3, leftAbove.get(Region.LEFT_ARM)); assertEquals(1, leftAbove.get(Region.RIGHT_ARM));
        assertEquals(3, leftAbove.get(Region.HEAD)); assertEquals(6, leftAbove.get(Region.TORSO));
        Map<Region,Double> leftBelow = DamageSelection.regionWeights(new DamageSelection.Context(-1, -40, false, false));
        assertEquals(9, leftBelow.get(Region.LEFT_LEG)); assertEquals(3, leftBelow.get(Region.RIGHT_LEG));
        Map<Region,Double> explosion = DamageSelection.regionWeights(new DamageSelection.Context(-1, 40, false, true));
        assertEquals(2, explosion.get(Region.LEFT_ARM)); assertEquals(4, explosion.get(Region.TORSO));
    }
    @Test void fallDistributionHasOnlyTorsoAndLegsAndStableBoundaries() {
        var fall = new DamageSelection.Context(1, 90, true, false);
        assertEquals(Region.TORSO, DamageSelection.selectRegion(fall, 0));
        assertEquals(Region.LEFT_LEG, DamageSelection.selectRegion(fall, .1));
        assertEquals(Region.RIGHT_LEG, DamageSelection.selectRegion(fall, .56));
        assertEquals(Region.RIGHT_LEG, DamageSelection.selectRegion(fall, Math.nextDown(1.0)));
        assertThrows(IllegalArgumentException.class, () -> DamageSelection.selectRegion(fall, 1));
    }
    @Test void classificationHasExplicitOverridesTagsAndDeliberateUnknownFallback() {
        assertEquals(MaimType.BURNT, DamageSelection.selectType("custom:acid", Set.of("acid"), false, false, Map.of()));
        assertEquals(MaimType.BURNT, DamageSelection.selectType("custom:freeze", Set.of("freezing"), false, false, Map.of()));
        assertEquals(MaimType.CRACKED, DamageSelection.selectType("custom:hammer", Set.of("blunt"), false, true, Map.of()));
        assertEquals(MaimType.OPENED, DamageSelection.selectType("custom:shot", Set.of(), true, false, Map.of()));
        assertEquals(MaimType.OPENED, DamageSelection.selectType("custom:sword", Set.of(), false, true, Map.of()));
        assertEquals(MaimType.CRACKED, DamageSelection.selectType("custom:unknown", Set.of(), false, false, Map.of()));
        assertEquals(MaimType.LOW_OXYGEN, DamageSelection.selectType("minecraft:drown", Set.of(), false, false, Map.of()));
        assertEquals(MaimType.LOW_OXYGEN, DamageSelection.selectType("custom:asphyxia", Set.of("low_oxygen"), false, false, Map.of()));
        assertEquals(MaimType.OPENED, DamageSelection.selectType("custom:heat", Set.of("fire"), false, false, Map.of("custom:heat", MaimType.OPENED)));
    }
}
