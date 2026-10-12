package com.bettercontent.betterdeathsdoor.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeartbeatClockTest {
    @Test void tickBoundaryAndPeriodicSyncDoNotRestartWindow() {
        HeartbeatClock clock = new HeartbeatClock(); clock.sync(true, 600);
        for (int i = 0; i < 599; i++) assertTrue(clock.tick(true, 1));
        assertEquals(1, clock.remainingTicks());
        clock.sync(true, 1); assertTrue(clock.tick(true, 1));
        assertEquals(0, clock.remainingTicks()); assertFalse(clock.tick(true, 1));
        clock.sync(true, 0); assertFalse(clock.tick(true, 1));
    }
    @Test void damageRefreshRearmsButSoundDisabledStillConsumesTime() {
        HeartbeatClock clock = new HeartbeatClock(); clock.sync(true, 600);
        for (int i = 0; i < 600; i++) assertFalse(clock.tick(false, 40));
        assertEquals(0, clock.remainingTicks());
        clock.sync(true, 600);
        for (int i = 0; i < 39; i++) assertFalse(clock.tick(true, 40));
        assertTrue(clock.tick(true, 40));
        clock.sync(false, 600); assertFalse(clock.tick(true, 1));
        clock.sync(true, 300); clock.reset(); assertFalse(clock.tick(true, 1));
    }
}
