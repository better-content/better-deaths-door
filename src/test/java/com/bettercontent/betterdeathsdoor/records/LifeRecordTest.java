package com.bettercontent.betterdeathsdoor.records;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LifeRecordTest {
    @Test void fixedOrderAndNativeMilestones() {
        assertEquals(10, LifeMetric.values().length);
        assertArrayEquals(new double[]{100,500,1000}, LifeMetric.HP_LOST.milestones());
        assertArrayEquals(new double[]{24000,72000,168000}, LifeMetric.DURATION.milestones());
    }
    @Test void freezesBeforeBestsAndIdempotentFinalHit() {
        LifeRecord r = new LifeRecord(false); r.add(LifeMetric.HP_LOST, 18); r.level(27);
        LifeRecap first = r.freeze(); r.add(LifeMetric.HP_LOST, 2);
        assertSame(first, r.freeze()); assertEquals(18, first.value(LifeMetric.HP_LOST));
        assertEquals(0, first.previousBests().get(6));
        UUID id = r.lifeId(); r.nextLife(); assertNotEquals(id, r.lifeId());
        r.add(LifeMetric.HP_LOST, 20); LifeRecap second = r.freeze();
        assertEquals(18, second.previousBests().get(6)); assertEquals(0, second.value(LifeMetric.XP_LEVEL));
    }
    @Test void partialFirstLifeNeverSetsBestsOrCardsAndNextLifeIsFull() {
        LifeRecord r = new LifeRecord(true); r.level(30);
        LifeRecap partial = r.freeze(); assertTrue(partial.partial()); assertTrue(partial.cards().isEmpty());
        r.nextLife(); r.level(10); LifeRecap full = r.freeze();
        assertFalse(full.partial()); assertEquals(0, full.previousBests().get(1)); assertFalse(full.cards().isEmpty());
    }
    @Test void creativeOrAdventureInvalidateWholeLifeNotOnlyTick() {
        LifeRecord r = new LifeRecord(false); r.level(30); r.invalidate();
        assertFalse(r.freeze().eligible()); assertTrue(r.freeze().cards().isEmpty()); assertEquals(0, r.bests[1]);
        r.nextLife(); assertTrue(r.freeze().eligible());
    }
    @Test void finiteNonnegativeOverflowSafe() {
        LifeRecord r = new LifeRecord(false);
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0}) r.add(LifeMetric.HP_LOST, bad);
        assertEquals(0, r.values[6]);
        r.add(LifeMetric.HP_LOST, Double.MAX_VALUE); r.add(LifeMetric.HP_LOST, Double.MAX_VALUE);
        assertEquals((double)Long.MAX_VALUE, r.values[6]);
        assertThrows(IllegalArgumentException.class, () -> LifeRecap.check(Double.NaN));
    }
    @Test void strictBestRankIsRelativeNotAbsoluteAndTiesUseMetricOrder() {
        LifeRecord r = new LifeRecord(false); r.bests[0] = 1000; r.bests[1] = 1; r.bests[2] = 1; r.bests[3] = 1;
        r.add(LifeMetric.DURATION, 2000); r.level(3); r.add(LifeMetric.MOB_KILLS, 3); r.add(LifeMetric.BLOCKS_PLACED, 3);
        var cards = r.freeze().cards(); assertEquals(3, cards.size());
        assertEquals(List.of(LifeMetric.XP_LEVEL, LifeMetric.MOB_KILLS, LifeMetric.BLOCKS_PLACED), cards.stream().map(LifeRecap.Card::metric).toList());
    }
    @Test void nearNinetyPercentBoundaryAndZeroNeverCreatesFakeBest() {
        LifeRecord r = new LifeRecord(false); r.bests[7] = 1; r.bests[8] = 1;
        r.add(LifeMetric.CHECKS_SURVIVED, .9); r.add(LifeMetric.DOOR_ENTRIES, .899999);
        var cards = r.freeze().cards(); assertEquals(1, cards.size()); assertEquals(List.of(LifeRecap.Badge.NEAR), cards.get(0).badges());
        assertTrue(new LifeRecord(false).freeze().cards().isEmpty());
    }
    @Test void sameMetricBestMilestoneBadgesMerge() {
        LifeRecord r = new LifeRecord(false); r.bests[7] = 1; r.add(LifeMetric.CHECKS_SURVIVED, 10);
        var card = r.freeze().cards().get(0); assertEquals(3, card.milestoneTier());
        assertTrue(card.badges().containsAll(List.of(LifeRecap.Badge.BEST, LifeRecap.Badge.MILESTONE)));
    }
    @Test void matchedAndFirstBaselinesAreHonest() {
        LifeRecord r = new LifeRecord(false); r.bests[1] = 2; r.level(2); r.add(LifeMetric.HP_LOST, 1);
        var cards = r.freeze().cards(); assertEquals(LifeRecap.Badge.MATCHED, cards.get(0).badges().get(0));
        assertEquals(LifeRecap.Badge.FIRST, cards.get(1).badges().get(0));
    }
    @Test void immutableDtoRejectsUnboundedOrCorruptInput() {
        List<Double> values = new ArrayList<>(Collections.nCopies(10, 0d));
        LifeRecap recap = new LifeRecap(1, UUID.randomUUID(), false, true, values, values, List.of());
        values.set(0, 9d); assertEquals(0, recap.values().get(0));
        assertThrows(UnsupportedOperationException.class, () -> recap.values().set(0, 2d));
        assertThrows(IllegalArgumentException.class, () -> new LifeRecap(99, UUID.randomUUID(), false, true, values, values, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new LifeRecap(1, UUID.randomUUID(), false, true, List.of(), values, List.of()));
    }
}
