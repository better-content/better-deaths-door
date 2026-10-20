package com.bettercontent.betterdeathsdoor.diagnostics;

/** Observes the existing resolved native consequence, never draws another outcome. */
public final class DoorObservation {
    private DoorObservation() {}
    public static void resolved(PlaytestLog log, PlaytestLog.Context context, String result, String region, String type) {
        if (!log.enabled("damage")) return;
        log.debug("damage", "door.resolve", context, "resolved", result,
            () -> PlaytestLog.fields("region", region, "type", type));
    }
}
