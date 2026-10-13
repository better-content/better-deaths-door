package com.bettercontent.betterdeathsdoor.client;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DeathRevealScheduleTest {
    @Test void discreteOneHundredFiftyMillisecondStages() {
        assertFalse(DeathRevealSchedule.summary(0, false));
        assertFalse(DeathRevealSchedule.summary(149, false));
        assertTrue(DeathRevealSchedule.summary(150, false));
        for (int i = 0; i < 3; i++) {
            long due = 150L * (i + 2);
            assertFalse(DeathRevealSchedule.card(due - 1, i, false));
            assertTrue(DeathRevealSchedule.card(due, i, false));
        }
        assertFalse(DeathRevealSchedule.card(Long.MAX_VALUE, -1, false));
        assertFalse(DeathRevealSchedule.card(Long.MAX_VALUE, 3, false));
        for (int cards = 0; cards <= 3; cards++) {
            long due = 150L * (Math.max(1, cards) + 2);
            assertFalse(DeathRevealSchedule.hint(due - 1, cards, false));
            assertTrue(DeathRevealSchedule.hint(due, cards, false));
        }
        assertTrue(DeathRevealSchedule.hint(750, 3, false));
    }
    @Test void reducedMotionIsImmediateButDoesNotCreateExtraTargets() {
        assertTrue(DeathRevealSchedule.summary(0, true));
        assertTrue(DeathRevealSchedule.card(0, 0, true));
        assertTrue(DeathRevealSchedule.hint(0, 3, true));
        assertFalse(DeathRevealSchedule.card(0, 3, true));
    }
    @Test void oneEpisodeSurvivesResizeDuplicatePacketsAndLateData() {
        var clock = new AtomicLong(1000);
        var session = new DeathPresentationSession(clock::get);
        Object screen = new Object();
        assertTrue(session.render(screen));
        clock.set(1499);
        assertFalse(session.render(screen)); // A resized/reinitialized screen remains the same episode.
        assertEquals(499, session.elapsed());
        assertTrue(session.summary(false));
        assertTrue(session.card(0, false));
        assertTrue(session.card(1, false));
        assertFalse(session.card(2, false));
        // Arrival of an immutable recap just chooses its actual count, without resetting time.
        assertTrue(session.hint(1, false));
        assertFalse(session.hint(3, false));
        clock.set(1750); assertTrue(session.hint(3, false));
    }
    @Test void detailsFinishRevealAndReturningDoesNotReplay() {
        var clock = new AtomicLong(0); var session = new DeathPresentationSession(clock::get);
        session.render(new Object()); clock.set(150);
        assertTrue(session.summary(false));
        session.toggleDetails(); assertTrue(session.details());
        assertTrue(session.hint(3, false));
        session.toggleDetails(); assertFalse(session.details());
        assertTrue(session.card(2, false));
    }
    @Test void closeAndNewDeathStartNewEpisodeAndBackwardClockDoesNotReveal() {
        var clock = new AtomicLong(500); var session = new DeathPresentationSession(clock::get);
        Object first = new Object(); session.render(first); clock.set(1000);
        session.clear(); assertFalse(session.owns(first));
        Object second = new Object(); assertTrue(session.render(second));
        assertFalse(session.summary(false));
        clock.set(900); assertEquals(0, session.elapsed());
        clock.set(1150); assertTrue(session.summary(false));
    }
}
