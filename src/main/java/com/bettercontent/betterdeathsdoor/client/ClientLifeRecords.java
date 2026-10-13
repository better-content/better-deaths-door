package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.records.LifeRecap;

/** Client-thread immutable presentation cache, cleared by the owning respawn/disconnect sequence. */
public final class ClientLifeRecords {
    private static LifeRecap latest;
    private ClientLifeRecords() {}
    public static LifeRecap latest() { return latest; }
    public static void accept(LifeRecap recap) { latest = recap; }
    public static void clear() { latest = null; }
}
