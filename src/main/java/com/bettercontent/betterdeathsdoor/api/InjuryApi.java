package com.bettercontent.betterdeathsdoor.api;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import com.bettercontent.betterdeathsdoor.state.BodySnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Read-only server integration; bodily state remains owned by Revival. */
public final class InjuryApi {
    private InjuryApi() {}
    public static int activeMaimCount(Player player) {
        return player instanceof ServerPlayer server ? RevivalManager.state(server).activeMaims().size() : 0;
    }
    public static boolean isAtDeathsDoor(Player player) {
        return player instanceof ServerPlayer server && RevivalManager.state(server).atDoor();
    }
    public static float semanticHealth(Player player) { return isAtDeathsDoor(player) ? 0 : player.getHealth(); }
    public static double deathProbability(Player player) {
        return player instanceof ServerPlayer server ? RevivalManager.snapshot(server).deathProbability() : 0;
    }
    public static BodySnapshot snapshot(ServerPlayer player) { return RevivalManager.snapshot(player); }
}
