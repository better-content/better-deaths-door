package com.bettercontent.betterdeathsdoor.state;

import com.bettercontent.betterdeathsdoor.diagnostics.DoorObservation;
import com.bettercontent.betterdeathsdoor.diagnostics.PlaytestLog;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DoorObservationTest {
    @Test void observingResolvedHitsPreservesActualRollCountAndConsequences() {
        for (boolean enabled : new boolean[]{false,true}) {
            var messages = new ArrayList<String>();
            var log = new PlaytestLog("better_deaths_door", new PlaytestLog.Sink() {
                public boolean enabled(String category, String level) { return enabled; }
                public void write(String category, String level, String message) { messages.add(message); }
            }, () -> 0L, new Properties());
            var body = new BodyState();
            var rolls = new AtomicInteger();
            var first = body.resolveHit(true, Region.HEAD, MaimType.CRACKED, 10,
                () -> { rolls.incrementAndGet(); return 0; }, BodyTuning.DEFAULT);
            DoorObservation.resolved(log, PlaytestLog.Context.server("minecraft:overworld",10),first.name(),"HEAD","CRACKED");
            var second = body.resolveHit(true, Region.TORSO, MaimType.OPENED, 11,
                () -> { rolls.incrementAndGet(); return 0; }, BodyTuning.DEFAULT);
            DoorObservation.resolved(log, PlaytestLog.Context.server("minecraft:overworld",11),second.name(),"TORSO","OPENED");
            assertEquals(BodyState.HitResult.ENTERED_DOOR,first);
            assertEquals(BodyState.HitResult.FINAL_DEATH,second);
            assertEquals(1,rolls.get());
            assertEquals(1,body.activeMaims().size());
            assertEquals(enabled ? 2 : 0,messages.size());
            if(enabled) {
                assertTrue(messages.get(0).contains("reason=ENTERED_DOOR"));
                assertTrue(messages.get(1).contains("reason=FINAL_DEATH"));
                assertTrue(messages.stream().noneMatch(s -> s.contains("committed") || s.contains("payload=")));
            }
        }
    }
}
