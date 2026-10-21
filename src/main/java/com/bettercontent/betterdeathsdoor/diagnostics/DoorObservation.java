package com.bettercontent.betterdeathsdoor.diagnostics;

/** Observes the existing resolved native consequence, never draws another outcome. */
public final class DoorObservation {
    private DoorObservation() {}
    public static void treatment(PlaytestLog log, java.util.function.Supplier<PlaytestLog.Context> context,
            String event, String result, String reason, java.util.UUID healer, java.util.UUID patient,
            long selectedMaim, Long committedMaim) {
        if (!log.enabled("treatment")) return;
        log.debug("treatment", event, context.get(), result, reason,
            () -> PlaytestLog.fields("healer", healer, "patient", patient, "maim", selectedMaim)
                + (committedMaim == null ? "" : PlaytestLog.fields("committed_maim", committedMaim)));
    }
    /** Native UI reason is classified, never emitted as free text or treated as a cure commit. */
    public static void treatmentEnd(PlaytestLog log, java.util.function.Supplier<PlaytestLog.Context> context,
            java.util.UUID healer, java.util.UUID patient, long maim, String nativeReason) {
        if (!log.enabled("treatment")) return;
        String cause = switch (nativeReason == null ? "" : nativeReason) {
            case "Treatment complete" -> "native_completion";
            case "Treatment interrupted: target out of reach" -> "target_out_of_reach";
            case "Treatment interrupted: damage taken" -> "damage_taken";
            case "Treatment interrupted: life ended" -> "life_ended";
            case "Treatment interrupted: player disconnected" -> "player_disconnected";
            default -> "native_cancel";
        };
        treatment(log, context, "treatment.end", "native_completion".equals(cause) ? "completed" : "canceled",
            cause, healer, patient, maim, null);
    }
    public static void resolved(PlaytestLog log, PlaytestLog.Context context, String result, String region, String type) {
        if (!log.enabled("damage")) return;
        log.debug("damage", "door.resolve", context, "resolved", result,
            () -> PlaytestLog.fields("region", region, "type", type));
    }
}
