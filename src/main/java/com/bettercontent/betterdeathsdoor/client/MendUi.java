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
    private static final Feedback FEEDBACK = new Feedback();

    /** Client-only, subject-bound feedback; no treatment timing or ordering authority. */
    public static final class Feedback {
        private UUID subject;
        private float progress;
        private String status = "";
        private int statusTicks;
        public void observe(UUID id, boolean active) {
            if (!Objects.equals(subject, id)) { clear(); subject = id; }
            if (!active) progress = 0;
        }
        public void treatment(UUID id, float value, String message) {
            // Closed screens and another subject cannot consume delayed feedback.
            if (!Objects.equals(subject, id) || subject == null) return;
            progress = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
            status = message == null ? "" : message;
            statusTicks = 100;
        }
        public float progress() { return progress; }
        public String status() { return status; }
        public void tick() { if (statusTicks > 0 && --statusTicks == 0) status = ""; }
        public void clear() { subject = null; progress = 0; status = ""; statusTicks = 0; }
    }
    public record Presentation(String action, boolean enabled, String status, String detail,
                               float progress, boolean active) {}
    public static void observe(BodyView body) {
        if (body == null) FEEDBACK.clear();
        else FEEDBACK.observe(body.playerId(), body.treatmentActive());
    }
    public static Presentation presentation(BodyView body) {
        observe(body);
        if (body == null) return new Presentation("MEND", false, "Body state unavailable", "Waiting for body sync", 0, false);
        int injuries = body.regions().stream().mapToInt(BodyView.RegionView::total).sum();
        boolean active = body.treatmentActive();
        String status = FEEDBACK.status();
        if (status.isBlank()) status = active ? "Applying treatment" : injuries == 0 ? "No active injuries" : "Ready to mend";
        String detail = active ? "Mending " + Math.round(FEEDBACK.progress() * 100) + "%"
                : injuries == 0 ? "No mend needed" : "Mend ~" + Math.round(body.treatmentSeconds() * injuries) + "s";
        return new Presentation(active ? "CANCEL" : "MEND", active || injuries > 0,
                status, detail, active ? FEEDBACK.progress() : 0, active);
    }
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
        if (!presentation(body).enabled()) return;
        int action = body.treatmentActive() ? BodyActionPacket.CANCEL_TREATMENT : BodyActionPacket.START_TREATMENT;
        RevivalNetwork.CHANNEL.sendToServer(new BodyActionPacket(body.playerId(), action));
    }
    public static void treatment(UUID subject, float value, String message) {
        FEEDBACK.treatment(subject, value, message);
    }
    public static void tick() { FEEDBACK.tick(); }
    public static void clear() { FEEDBACK.clear(); }
    public static void progress(GuiGraphics graphics, Font font, BodyView body,
            int x, int y, int width) {
        refreshTheme();
        Presentation view = presentation(body);
        graphics.drawString(font, font.plainSubstrByWidth(view.detail(), width), x, y, MUTED, false);
        graphics.drawString(font, font.plainSubstrByWidth(view.status(), width), x, y + 10, MUTED, false);
        if (view.active()) {
            graphics.fill(x, y + 20, x + width, y + 22, BetterUiTheme.color(0xFFBDA479, 0xFF405E4A));
            graphics.fill(x, y + 20, x + (int) (width * view.progress()), y + 22, GREEN);
        }
    }
}
