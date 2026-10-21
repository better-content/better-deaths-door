package com.bettercontent.betterdeathsdoor.diagnostics;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual production treatment projection; owning BodyState/native tests retain gameplay authority. */
final class TreatmentObservationTest {
    public static void main(String[] args) { new TreatmentObservationTest().existingParticipantAndMaimIdsDistinguishAdmissionCancellationAndCommit(); }
    @Test void existingParticipantAndMaimIdsDistinguishAdmissionCancellationAndCommit() {
        var records = new ArrayList<String>(); boolean[] enabled = {false};
        var log = new PlaytestLog("better_deaths_door", new PlaytestLog.Sink() {
            public boolean enabled(String category, String level) { return enabled[0]; }
            public void write(String category, String level, String text) { records.add(text); }
        }, () -> 0L, new Properties());
        var healer = UUID.fromString("00000000-0000-0000-0000-000000000010");
        var patient = UUID.fromString("00000000-0000-0000-0000-000000000011");
        var other = UUID.fromString("00000000-0000-0000-0000-000000000012");
        var reads = new AtomicInteger();
        Supplier<PlaytestLog.Context> context = () -> { reads.incrementAndGet(); return new PlaytestLog.Context("server", "minecraft:overworld", 10, patient.toString(), ""); };
        Supplier<PlaytestLog.Context> healerContext = () -> { reads.incrementAndGet(); return new PlaytestLog.Context("server", "minecraft:overworld", 10, healer.toString(), ""); };
        for (boolean active : new boolean[]{false, true}) {
            enabled[0] = active;
            DoorObservation.treatment(log, context, "treatment.start", "admitted", "native_treatment_stored", healer, patient, 7, null);
            DoorObservation.treatmentEnd(log, healerContext, healer, patient, 7, "private_free_text_must_not_escape");
            DoorObservation.treatmentEnd(log, healerContext, healer, other, 8, "Treatment complete");
            DoorObservation.treatment(log, context, "treatment.cure", "committed", "injury_removed", healer, patient, 7, 9L);
            DoorObservation.treatmentEnd(log, healerContext, healer, patient, 7, "Treatment interrupted: damage taken");
            DoorObservation.treatmentEnd(log, healerContext, healer, patient, 7, "Treatment interrupted: life ended");
            if (!active) { assertTrue(records.isEmpty()); assertEquals(0, reads.get()); }
        }
        assertEquals(6, records.size()); assertEquals(6, reads.get());
        assertTrue(records.get(0).contains("result=admitted reason=native_treatment_stored"));
        assertTrue(records.get(1).contains("result=canceled reason=native_cancel"));
        assertTrue(records.get(2).contains("result=completed reason=native_completion"));
        assertTrue(records.get(3).contains("result=committed reason=injury_removed"));
        assertTrue(records.get(0).contains("healer=" + healer + " patient=" + patient + " maim=7"));
        assertTrue(records.get(2).contains("healer=" + healer + " patient=" + other + " maim=8"));
        assertTrue(records.get(3).contains("maim=7 committed_maim=9"), "actual committed record is not inferred from selected maim");
        assertTrue(records.get(4).contains("result=canceled reason=damage_taken"));
        assertTrue(records.get(5).contains("result=canceled reason=life_ended"));
        assertTrue(records.subList(0, 3).stream().noneMatch(s -> s.contains("committed_maim=")));
        assertTrue(records.stream().noneMatch(s -> s.contains("private_free_text") || s.contains("name=") || s.contains("nbt=") || s.contains("progress=") || s.contains("lifeId=")));
    }
}
