package com.bettercontent.betterdeathsdoor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageLedgerTest {
    @Test void survivingHitRecordsOutcomeOnceAndKeepsUnobservableRemainder() {
        var result = DamageLedger.accountHit(DamageLedger.Summary.empty(), 10, 8, 8, 3, 0, 0, 20, 17);
        assertEquals(1, result.hits());
        assertEquals(10, result.incoming());
        assertEquals(0, result.mitigation());
        assertEquals(3, result.applied());
        assertEquals(3, result.healthLost());
        assertEquals(7, result.unknown());
    }

    @Test void absorptionIsAttributedBeforeHealthLossAtFinalDeath() {
        var result = DamageLedger.accountHit(DamageLedger.Summary.empty(), 12, 12, 8, 8, 4, 0, 1, 0);
        assertEquals(4, result.absorption());
        assertEquals(8, result.applied());
        assertEquals(1, result.healthLost());
        assertEquals(0, result.unknown());
    }

    @Test void aRepeatedDeathEventCannotBeRepresentedAsTheSameHit() {
        var once = DamageLedger.accountHit(DamageLedger.Summary.empty(), 5, 5, 5, 5, 0, 0, 1, 0);
        var twice = DamageLedger.accountHit(once, 5, 5, 5, 5, 0, 0, 1, 0);
        assertEquals(1, once.hits());
        assertEquals(2, twice.hits());
    }
}
