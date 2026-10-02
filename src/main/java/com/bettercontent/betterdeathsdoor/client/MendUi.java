package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.gameplaynotices.BetterUiTheme;
import com.bettercontent.betterdeathsdoor.network.*;
import com.bettercontent.betterdeathsdoor.state.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

/** Shared controls and compact maim list for both inventory and teammate views. */
public final class MendUi {
    public static int PAPER = 0xFFEFE3C4, INK = 0xFF254637, MUTED = 0xFF58654B;
    public static int RED = 0xFF9A4B42, GREEN = 0xFF496951;
    private static float progress;
    private static String status = "";
    private static int statusTicks;
    private MendUi() {}
    public static void refreshTheme() {
        PAPER = BetterUiTheme.color(0xFFEFE3C4, 0xFF182B26);
        INK = BetterUiTheme.color(0xFF254637, 0xFFF4E6C7);
        MUTED = BetterUiTheme.color(0xFF58654B, 0xFFC4B99E);
        RED = BetterUiTheme.color(0xFF9A4B42, 0xFFED9E91);
        GREEN = BetterUiTheme.color(0xFF496951, 0xFFA8CEB2);
    }
    public record Entry(Region region, MaimType type, int count) {}
    public static List<Entry> entries(BodyView body) {
        List<Entry> result = new ArrayList<>();
        for (Region region : Region.values())
            for (MaimType type : MaimType.values()) {
                int count = body.region(region).count(type);
                if (count > 0) result.add(new Entry(region, type, count));
            }
        return result;
    }
    public static int maxScroll(BodyView body, int height) {
        return Math.max(0, entries(body).size() * 24 - height);
    }
    public static void renderList(GuiGraphics graphics, Font font, BodyView body,
            int x, int y, int width, int height, int scroll) {
        refreshTheme();
        graphics.enableScissor(x, y, x + width, y + height);
        List<Entry> entries = entries(body);
        if (entries.isEmpty()) graphics.drawString(font, "No maims", x + 3, y + 8, GREEN, false);
        for (int i = 0; i < entries.size(); i++) {
            int row = y + i * 24 - scroll;
            if (row + 24 < y || row > y + height) continue;
            Entry entry = entries.get(i);
            graphics.fill(x, row, x + width - 2, row + 22, i % 2 == 0
                ? BetterUiTheme.color(0xFFF9EFD7, 0xFF263A30) : BetterUiTheme.color(0xFFE7D7B6, 0xFF2D4237));
            String region = BodyView.label(entry.region);
            String type = BodyView.label(entry.type) + "×" + entry.count;
            graphics.drawString(font, font.plainSubstrByWidth(region, width - 8),
                    x + 3, row + 2, INK, false);
            graphics.drawString(font, font.plainSubstrByWidth(type, width - 8),
                    x + 3, row + 12, RED, false);
        }
        graphics.disableScissor();
    }
    public static void toggle(BodyView body) {
        int action = body.treatmentActive() ? BodyActionPacket.CANCEL_TREATMENT : BodyActionPacket.START_TREATMENT;
        RevivalNetwork.CHANNEL.sendToServer(new BodyActionPacket(body.playerId(), action));
    }
    public static void treatment(float value, String message) {
        progress = Math.max(0, Math.min(1, value));
        status = message; statusTicks = 100;
    }
    public static void tick() { if (statusTicks > 0 && --statusTicks == 0) status = ""; }
    public static void clear() { progress = 0; status = ""; statusTicks = 0; }
    public static void progress(GuiGraphics graphics, Font font, BodyView body,
            int x, int y, int width) {
        refreshTheme();
        String label = body.treatmentActive()
                ? "Mending " + Math.round(progress * 100) + "%"
                : status.isBlank() ? "Arms first" : status;
        graphics.drawString(font, font.plainSubstrByWidth(label, width), x, y, MUTED, false);
        graphics.fill(x, y + 11, x + width, y + 13, BetterUiTheme.color(0xFFBDA479, 0xFF405E4A));
        if (body.treatmentActive())
            graphics.fill(x, y + 11, x + (int) (width * progress), y + 13, GREEN);
    }
}
