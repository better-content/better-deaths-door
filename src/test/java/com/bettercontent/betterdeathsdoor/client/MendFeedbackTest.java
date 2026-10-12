package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.network.BodyView;
import com.bettercontent.betterdeathsdoor.state.Region;
import com.bettercontent.betterdeathsdoor.state.MaimType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MendFeedbackTest {
    private BodyView body(UUID subject, int injuries, boolean active) {
        var regions = Arrays.stream(Region.values()).map(region -> new BodyView.RegionView(region,
                region == Region.values()[0] ? Map.of(MaimType.values()[0], injuries) : Map.of(),
                0, Map.of())).toList();
        return new BodyView(subject, "Subject", 20, 20, false, 0, 0, 1200,
                0, 1, 2.5, regions, active, 0);
    }

    @Test void activePresentationOffersCancelAndNeverAnIdleEstimate() {
        MendUi.clear();
        var body = body(UUID.randomUUID(), 3, true);
        MendUi.observe(body);
        MendUi.treatment(body.playerId(), .42f, "Applying treatment");
        var view = MendUi.presentation(body);
        assertEquals("CANCEL", view.action());
        assertTrue(view.enabled());
        assertTrue(view.active());
        assertEquals("Mending 42%", view.detail());
        assertEquals("Applying treatment", view.status());
        assertEquals(.42f, view.progress());
        MendUi.clear();
    }

    @Test void idleEstimateAndDisabledReasonsAreSharedByBothScreens() {
        MendUi.clear();
        UUID subject = UUID.randomUUID();
        var idle = MendUi.presentation(body(subject, 3, false));
        assertEquals("MEND", idle.action());
        assertTrue(idle.enabled());
        assertEquals("Mend ~8s", idle.detail());
        assertEquals(0, idle.progress());
        var healthy = MendUi.presentation(body(subject, 0, false));
        assertFalse(healthy.enabled());
        assertEquals("No active injuries", healthy.status());
        var unavailable = MendUi.presentation(null);
        assertFalse(unavailable.enabled());
        assertEquals("Body state unavailable", unavailable.status());
        // State unavailable also disposes previous subject feedback.
        MendUi.treatment(subject, .9f, "Late");
        assertEquals("Ready to mend", MendUi.presentation(body(subject, 1, false)).status());
        MendUi.clear();
    }

    @Test void stoppedTreatmentClearsProgressButKeepsBriefTerminalReason() {
        var feedback = new MendUi.Feedback();
        UUID subject = UUID.randomUUID();
        feedback.observe(subject, true);
        feedback.treatment(subject, .65f, "Applying treatment");
        assertEquals(.65f, feedback.progress());
        feedback.treatment(subject, 0, "Treatment cancelled");
        feedback.observe(subject, false);
        assertEquals(0, feedback.progress());
        for (int tick = 0; tick < 99; tick++) feedback.tick();
        assertEquals("Treatment cancelled", feedback.status());
        feedback.tick();
        assertEquals("", feedback.status());
    }

    @Test void changingSubjectOrClosingRejectsDelayedPackets() {
        var feedback = new MendUi.Feedback();
        UUID first = UUID.randomUUID(), next = UUID.randomUUID();
        feedback.observe(first, true);
        feedback.treatment(first, .9f, "Applying treatment");
        feedback.observe(next, true);
        assertEquals(0, feedback.progress());
        assertEquals("", feedback.status());
        feedback.treatment(first, .9f, "Late packet");
        assertEquals("", feedback.status());
        feedback.treatment(next, .3f, "Applying treatment");
        feedback.clear();
        feedback.treatment(next, .8f, "Late after close");
        assertEquals(0, feedback.progress());
        assertEquals("", feedback.status());
        feedback.observe(next, true);
        assertEquals(0, feedback.progress());
    }

    @Test void progressIsFiniteClampedAndResetsWhenInactive() {
        var feedback = new MendUi.Feedback();
        UUID subject = UUID.randomUUID();
        feedback.observe(subject, true);
        feedback.treatment(subject, 4, "Applying treatment");
        assertEquals(1, feedback.progress());
        feedback.treatment(subject, -1, null);
        assertEquals(0, feedback.progress());
        assertEquals("", feedback.status());
        feedback.treatment(subject, Float.NaN, "Interrupted");
        assertEquals(0, feedback.progress());
        feedback.treatment(subject, .8f, "Applying treatment");
        feedback.observe(subject, false);
        assertEquals(0, feedback.progress());
    }

    @Test void completionCancellationAndInterruptionShareTheBriefStatusLifetime() {
        for (String reason : new String[]{"Treatment complete", "Treatment cancelled", "Treatment interrupted"}) {
            var feedback = new MendUi.Feedback();
            UUID subject = UUID.randomUUID();
            feedback.observe(subject, true);
            feedback.treatment(subject, 0, reason);
            feedback.observe(subject, false);
            assertEquals(reason, feedback.status());
            for (int tick = 0; tick < 100; tick++) feedback.tick();
            assertEquals("", feedback.status());
        }
    }
}
