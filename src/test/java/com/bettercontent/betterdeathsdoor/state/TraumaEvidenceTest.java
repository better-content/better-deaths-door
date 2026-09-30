package com.bettercontent.betterdeathsdoor.state;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class TraumaEvidenceTest {
    private final UUID player = UUID.randomUUID();
    private BodySnapshot snapshot(BodyState body, int trauma) {
        return new BodySnapshot(player, 2, 20, false, 0, 100, body.activeMaims(), List.of(), trauma);
    }
    @Test void onlyFunctionalPreexistingInjuriesQualify() {
        var body = new BodyState();
        assertFalse(snapshot(body, 1).traumaAmplifies(snapshot(body, 0)));
        body.addMaim(Region.HEAD, MaimType.CRACKED, 1);
        assertFalse(snapshot(body, 1).traumaAmplifies(snapshot(body, 0)));
        body.addMaim(Region.TORSO, MaimType.OPENED, 2);
        assertTrue(snapshot(body, 1).traumaAmplifies(snapshot(body, 0)));
        assertFalse(snapshot(body, 6).traumaAmplifies(snapshot(body, 5)));
        assertFalse(snapshot(body, 0).traumaAmplifies(snapshot(body, 1)));
    }
    @Test void newInjuryCannotMasqueradeAsTraumaAmplification() {
        var body = new BodyState();
        var before = snapshot(body, 0);
        body.addMaim(Region.LEFT_LEG, MaimType.BURNT, 2);
        assertFalse(snapshot(body, 1).traumaAmplifies(before));
    }
}
