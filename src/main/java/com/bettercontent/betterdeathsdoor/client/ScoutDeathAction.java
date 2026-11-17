package com.bettercontent.betterdeathsdoor.client;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;

/** Optional single-owner UI bridge. A server-authoritative companion supplies eligibility and acknowledgments. */
public interface ScoutDeathAction {
    record State(boolean available, boolean pending, Component error) {
        public static State hidden() { return new State(false, false, null); }
    }
    State state(DeathScreen screen);
    void request(DeathScreen screen);
    default void close(DeathScreen screen) {}
}
