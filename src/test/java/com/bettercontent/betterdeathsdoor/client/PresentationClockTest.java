package com.bettercontent.betterdeathsdoor.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PresentationClockTest {
    @Test void hiddenTimeDoesNotConsumeToastLifetime() {
        var clock = new PresentationClock();
        clock.update(100, false); assertEquals(100, clock.now(100));
        clock.update(200, true); assertEquals(200, clock.now(3000));
        clock.update(5000, false); assertEquals(200, clock.now(5000));
        assertEquals(300, clock.now(5100));
        clock.update(5200, true); clock.update(8000, false);
        assertEquals(400, clock.now(8000));
    }
}
