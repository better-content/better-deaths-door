package com.bettercontent.betterdeathsdoor.state;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeartbeatDeadlineTest {
    @Test void entryAndDamageHaveInclusiveStartAndExclusiveDeadline() {
        BodyState body = new BodyState();
        body.enterDoor(100);
        assertEquals(600, body.heartbeatRemainingTicks(100));
        assertEquals(1, body.heartbeatRemainingTicks(699));
        assertEquals(0, body.heartbeatRemainingTicks(700));
        body.refreshHeartbeat(710);
        assertEquals(600, body.heartbeatRemainingTicks(710));
        assertEquals(0, body.heartbeatRemainingTicks(1310));
    }
    @Test void refreshBeforeExpiryAndHealingReentryAreIndependentOfTrauma() {
        BodyState body = new BodyState(); body.enterDoor(10);
        body.addTrauma(10); body.refreshHeartbeat(300);
        assertEquals(600, body.heartbeatRemainingTicks(300));
        assertEquals(1, body.traumaCount(1000));
        assertEquals(0, body.heartbeatRemainingTicks(1000));
        assertTrue(body.tryHeal(1000));
        body.refreshHeartbeat(1001);
        assertEquals(0, body.heartbeatRemainingTicks(1001));
        body.enterDoor(1002);
        assertEquals(600, body.heartbeatRemainingTicks(1002));
        body.clear(); assertEquals(0, body.heartbeatRemainingTicks(1002));
    }
    @Test void saveReloadAndNonDeathCopyKeepTheOriginalDeadline() {
        BodyState body = new BodyState(); body.enterDoor(100);
        assertEquals(300, BodyState.load(body.save(), 400).heartbeatRemainingTicks(400));
        assertEquals(300, body.copy().heartbeatRemainingTicks(400));
        assertEquals(0, BodyState.load(body.save(), 800).heartbeatRemainingTicks(800));
        var old = body.save(); old.remove("heartbeatUntil");
        assertTrue(BodyState.load(old, 200).atDoor());
        assertEquals(0, BodyState.load(old, 200).heartbeatRemainingTicks(200));
    }
}
