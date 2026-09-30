package com.bettercontent.betterdeathsdoor.client;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
/** Body controls take visual priority; queued and visible vanilla toasts resume afterward. */
public final class InjuryToastClock {
    private static final PresentationClock CLOCK = new PresentationClock();
    public static boolean updateAndBlocked() {
        var mc = Minecraft.getInstance();
        var screen = mc.screen;
        boolean atDoor = mc.player != null && ClientRevivalState.get(mc.player.getUUID()).map(body -> body.atDoor()).orElse(false);
        boolean blocked = atDoor || screen instanceof MendScreen || screen instanceof DeathScreen && ClientRevivalState.recap() != null;
        CLOCK.update(Util.getMillis(), blocked);
        return blocked;
    }
    public static long now() { return CLOCK.now(Util.getMillis()); }
}
