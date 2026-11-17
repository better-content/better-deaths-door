package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.RevivalMod;
import com.bettercontent.betterdeathsdoor.mixin.DeathCauseAccessor;
import com.bettercontent.betterdeathsdoor.network.BodyView;
import com.bettercontent.betterdeathsdoor.records.LifeRecap;
import com.bettercontent.betterdeathsdoor.records.LifeMetric;
import com.bettercontent.betterdeathsdoor.state.Region;
import com.bettercontent.betterdeathsdoor.state.MaimType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

/** Single death-layout owner. Server snapshots supply records; rendering never awards them. */
@Mod.EventBusSubscriber(modid = RevivalMod.MOD_ID, value = Dist.CLIENT)
public final class DeathRecapOverlay {
    public record Bounds(int x, int y, int width, int height) {}
    private static final int INK = 0xFFF4E6C7, MUTED = 0xFFC4B99E, GOLD = 0xFFA48657;
    private static final int PAPER = 0xFF182B26, FIELD = 0xFF263A30, GREEN = 0xFFA8CEB2;
    private static final ResourceLocation ICONS = new ResourceLocation(RevivalMod.MOD_ID, "textures/gui/life_record_icons.png");
    private static final DeathPresentationSession SESSION = new DeathPresentationSession(Util::getMillis);
    private static Screen hintScreen;
    private static int hintHeight, scroll, causeLines = 1;
    private static Button detailsButton, scoutButton, nativeRespawn;
    private static boolean respawnReadyBeforePending;
    private static ScoutDeathAction scoutAction;
    private static LifeRecap frozen;
    private DeathRecapOverlay() {}

    /** Registered only by an installed server-authoritative scout companion. */
    public static void registerScoutAction(ScoutDeathAction action) { scoutAction = java.util.Objects.requireNonNull(action); }
    private static ScoutDeathAction.State scoutState(Screen screen) {
        if (!(screen instanceof DeathScreen death) || scoutAction == null || nativeRespawn == null)
            return ScoutDeathAction.State.hidden();
        var mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isDeadOrDying() || mc.level == null
                || mc.level.getLevelData().isHardcore()) return ScoutDeathAction.State.hidden();
        return scoutAction.state(death);
    }

    private static boolean reducedMotion() { return InjuryClientConfig.REDUCED_MOTION.get(); }
    private static int cardCount() { return frozen == null ? 3 : frozen.cards().size(); }
    private static DeathRecapLayout layout(Screen screen) {
        return DeathRecapLayout.fit(screen.width, screen.height, causeLines, cardCount(),
                hintScreen == screen ? hintHeight : 0, SESSION.owns(screen) && SESSION.details(),
                scoutState(screen).available());
    }
    /** Discovery Guides supplies measured copy; its position and clipping remain owned here. */
    public static Bounds reserveHint(Screen screen, int measuredHeight) {
        hintScreen = screen; hintHeight = Math.max(0, Math.min(1024, measuredHeight));
        var l = layout(screen);
        return new Bounds(l.x(), l.contentTop() + l.hintTop() - l.clampScroll(scroll), l.width(), hintHeight);
    }
    public static Bounds reservedBounds(int width, int height) {
        var l = DeathRecapLayout.fit(width, height, causeLines, cardCount(), hintHeight, SESSION.details(),
                hintScreen != null && scoutState(hintScreen).available());
        return new Bounds(l.x(), l.contentTop(), l.width(), l.viewportHeight());
    }
    public static boolean hintVisible(Screen screen) {
        return SESSION.owns(screen) && SESSION.hint(cardCount(), reducedMotion())
                && layout(screen).hintIntersectsViewport(scroll, hintHeight);
    }
    public static boolean hintFullyVisible(Screen screen) {
        if (!hintVisible(screen)) return false;
        var l = layout(screen);
        int top = l.contentTop() + l.hintTop() - l.clampScroll(scroll);
        return top >= l.contentTop() && top + hintHeight <= l.contentBottom();
    }
    public static Bounds contentViewport(Screen screen) {
        var l = layout(screen); return new Bounds(l.x(), l.contentTop(), l.width(), l.viewportHeight());
    }
    public static boolean detailsVisible(Screen screen) {
        return SESSION.owns(screen) && SESSION.summary(reducedMotion());
    }
    public static void reset() {
        SESSION.clear(); frozen = null; hintScreen = null; hintHeight = 0;
        scroll = 0; causeLines = 1; detailsButton = null; scoutButton = null; nativeRespawn = null;
        respawnReadyBeforePending = false;
    }

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen screen)) return;
        // Reinitialization restores native widgets, but must not restart the death episode clock.
        var nativeButtons = event.getListenersList().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
        var respawnLabel = Component.translatable("deathScreen.respawn").getString();
        var titleLabel = Component.translatable("deathScreen.titleScreen").getString();
        var respawn = nativeButtons.stream().filter(b -> b.getMessage().getString().equals(respawnLabel)).findFirst();
        var title = nativeButtons.stream().filter(b -> b.getMessage().getString().equals(titleLabel)).findFirst();
        nativeRespawn = respawn.orElse(null);
        if (respawn.isPresent() && title.isPresent() && respawn.get() != title.get()) {
            int width = Math.max(1, Math.min(200, (screen.width - 30) / 2));
            respawn.get().setWidth(width); respawn.get().setX(screen.width / 2 - width - 3);
            title.get().setWidth(width); title.get().setX(screen.width / 2 + 3);
            respawn.get().setY(Math.max(0, screen.height - 32));
            title.get().setY(Math.max(0, screen.height - 32));
        }
        var l = layout(screen);
        detailsButton = Button.builder(Component.translatable("screen.better_deaths_door.details"), button -> {
            if (!detailsVisible(screen)) return;
            SESSION.toggleDetails(); scroll = 0; updateDetails(screen);
        }).bounds(screen.width / 2 - 55, l.detailsY(), 110, 18).build();
        detailsButton.visible = detailsVisible(screen); detailsButton.active = detailsButton.visible;
        event.addListener(detailsButton);
        scoutButton = Button.builder(Component.translatable("screen.better_deaths_door.scout"), button -> {
            if (nativeRespawn != null && nativeRespawn.active && scoutState(screen).available()
                    && !scoutState(screen).pending()) scoutAction.request(screen);
        }).bounds(screen.width / 2 - 78, l.scoutY(), 156, 18).build();
        scoutButton.visible = scoutState(screen).available(); scoutButton.active = scoutButton.visible;
        event.addListener(scoutButton);
    }
    @SubscribeEvent
    public static void close(ScreenEvent.Closing event) {
        if (SESSION.owns(event.getScreen())) {
            if (scoutAction != null && event.getScreen() instanceof DeathScreen screen) scoutAction.close(screen);
            reset();
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockRespawnClickWhilePending(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof DeathScreen screen) || nativeRespawn == null
                || event.getButton() != 0 || !scoutState(screen).pending()) return;
        if (event.getMouseX() >= nativeRespawn.getX() && event.getMouseX() < nativeRespawn.getX() + nativeRespawn.getWidth()
                && event.getMouseY() >= nativeRespawn.getY() && event.getMouseY() < nativeRespawn.getY() + nativeRespawn.getHeight())
            event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockRespawnKeyWhilePending(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof DeathScreen screen) || nativeRespawn == null
                || !scoutState(screen).pending() || screen.getFocused() != nativeRespawn) return;
        if (event.getKeyCode() == 257 || event.getKeyCode() == 335 || event.getKeyCode() == 32) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof DeathScreen screen)) return;
        if (SESSION.owns(screen)) updateDetails(screen);
    }
    private static void updateDetails(Screen screen) {
        if (detailsButton == null) return;
        var l = layout(screen);
        var state = scoutState(screen);
        detailsButton.visible = detailsVisible(screen);
        detailsButton.active = detailsButton.visible;
        detailsButton.setX(screen.width / 2 - (state.available() && !l.stackedActions() ? 136 : 55));
        detailsButton.setY(l.detailsY());
        detailsButton.setMessage(Component.translatable(SESSION.details()
                ? "screen.better_deaths_door.summary" : "screen.better_deaths_door.details"));
        if (scoutButton != null) {
            scoutButton.visible = state.available();
            scoutButton.active = state.available() && !state.pending()
                    && nativeRespawn != null && nativeRespawn.active;
            scoutButton.setX(screen.width / 2 + (l.stackedActions() ? -78 : -20));
            scoutButton.setY(l.scoutY());
        }
        if (nativeRespawn != null) {
            if (state.pending()) {
                respawnReadyBeforePending |= nativeRespawn.active;
                nativeRespawn.active = false;
            } else if (respawnReadyBeforePending) {
                nativeRespawn.active = true;
                respawnReadyBeforePending = false;
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof DeathScreen screen)) return;
        var mc = Minecraft.getInstance(); var g = event.getGuiGraphics();
        if (SESSION.render(screen)) {
            frozen = null; scroll = 0;
            if (InjuryClientConfig.SOUND.get() && mc.player != null && ClientRevivalState.recap() != null)
                mc.player.playSound(SoundEvents.BASALT_BREAK, .35f, .65f);
        }
        // First immutable packet wins for this episode; duplicate/late data never resets timing.
        if (frozen == null) frozen = ClientLifeRecords.latest();
        Component cause = ((DeathCauseAccessor) screen).betterDeathsDoor$causeOfDeath();
        int width = Math.max(1, Math.min(560, screen.width - 24));
        var causeText = cause == null ? List.<net.minecraft.util.FormattedCharSequence>of()
                : mc.font.split(cause, Math.max(1, width - 24));
        causeLines = Math.min(3, causeText.size());
        var l = layout(screen); scroll = l.clampScroll(scroll); updateDetails(screen);
        boolean clipped = false;
        g.pose().pushPose();
        try {
            g.pose().translate(0, 0, 500);
            g.fill(0, 0, screen.width, screen.height, 0xFF101C18);
            g.fill(l.x() - 3, 8, l.x() + l.width() + 3, screen.height - 6, GOLD);
            g.fill(l.x() - 2, 9, l.x() + l.width() + 2, screen.height - 7, PAPER);
            centered(g, screen.getTitle().getString(), l.x(), 23, l.width(), INK);
            for (int i = 0; i < causeLines; i++) {
                var text = causeText.get(i);
                g.drawString(mc.font, text, l.x() + (l.width() - mc.font.width(text)) / 2, 43 + i * 10, MUTED, false);
            }
            g.fill(l.x() + 10, l.contentTop() - 7, l.x() + l.width() - 10, l.contentTop() - 6, GOLD);
            if (l.viewportHeight() > 0) {
                g.enableScissor(l.x(), l.contentTop(), l.x() + l.width(), l.contentBottom());
                clipped = true;
                if (SESSION.summary(reducedMotion())) drawSummary(g, l, l.contentTop() - scroll);
                if (SESSION.details()) drawDetails(g, l, l.contentTop() + l.summaryHeight() - scroll);
                else drawCards(g, l);
                g.disableScissor(); clipped = false;
                if (l.maxScroll() > 0) {
                    int height = Math.max(8, l.viewportHeight() * l.viewportHeight() / Math.max(1, l.contentHeight()));
                    int y = l.contentTop() + (l.viewportHeight() - height) * scroll / l.maxScroll();
                    g.fill(l.x() + l.width() - 2, l.contentTop(), l.x() + l.width(), l.contentBottom(), FIELD);
                    g.fill(l.x() + l.width() - 2, y, l.x() + l.width(), y + height, GOLD);
                }
            }
            var scoutError = scoutState(screen).error();
            if (scoutError != null) text(g, scoutError.getString(), l.x() + 8, l.contentBottom() + 2,
                    l.width() - 16, 0xFFED9E91);
            drawButtons(screen, event);
            if (cause != null && causeText.size() > causeLines && event.getMouseX() >= l.x()
                    && event.getMouseX() < l.x() + l.width() && event.getMouseY() >= 43
                    && event.getMouseY() < l.contentTop() - 6)
                g.renderTooltip(mc.font, cause, event.getMouseX(), event.getMouseY());
        } finally { if (clipped) g.disableScissor(); g.pose().popPose(); }
    }
    private static void drawSummary(GuiGraphics g, DeathRecapLayout l, int y) {
        g.fill(l.x() + 6, y, l.x() + l.width() - 6, y + 38, FIELD);
        String status = frozen == null ? "Life record syncing…" : frozen.partial() ? "PARTIAL LIFE · since tracking began"
                : !frozen.eligible() ? "INELIGIBLE LIFE · no record awards" : "THIS LIFE";
        text(g, status, l.x() + 12, y + 3, l.width() - 24, GOLD);
        if (frozen != null) {
            text(g, format(LifeMetric.DURATION, value(LifeMetric.DURATION)) + "  ·  Peak Lv "
                    + format(LifeMetric.XP_LEVEL, value(LifeMetric.XP_LEVEL)) + "  ·  Mobs "
                    + format(LifeMetric.MOB_KILLS, value(LifeMetric.MOB_KILLS)), l.x() + 12, y + 15, l.width() - 24, INK);
        }
        BodyView body = ClientRevivalState.recap();
        if (body != null) {
            int active = body.regions().stream().mapToInt(BodyView.RegionView::total).sum();
            int treated = body.regions().stream().mapToInt(BodyView.RegionView::treated).sum();
            text(g, "Injuries " + active + " active · " + treated + " treated", l.x() + 12, y + 27, l.width() - 24, MUTED);
        }
    }
    private static double value(LifeMetric metric) { return frozen.values().get(metric.ordinal()); }
    private static void drawCards(GuiGraphics g, DeathRecapLayout l) {
        if (frozen == null) return;
        if (frozen.cards().isEmpty()) {
            if (!SESSION.card(0, reducedMotion())) return;
            int y = l.contentTop() + l.cardsTop() - scroll;
            g.fill(l.x() + 6, y, l.x() + l.width() - 6, y + l.cardHeight(), FIELD);
            text(g, frozen.partial() ? "Tracking began during this life." : !frozen.eligible()
                    ? "This life is not eligible for personal bests." : "No new or close personal bests this life.",
                    l.x() + 14, y + 12, l.width() - 28, INK);
            text(g, frozen.partial() ? "Full-life comparisons start after respawn." : "Your established records are preserved.",
                    l.x() + 14, y + 28, l.width() - 28, MUTED);
            return;
        }
        for (int i = 0; i < frozen.cards().size(); i++) {
            if (!SESSION.card(i, reducedMotion())) continue;
            var card = frozen.cards().get(i);
            int x = l.x() + 6, y = l.contentTop() + l.cardsTop() + i * (l.cardHeight() + 8) - scroll;
            int w = l.width() - 12;
            g.fill(x, y, x + w, y + l.cardHeight(), FIELD);
            g.fill(x, y, x + 2, y + l.cardHeight(), GOLD);
            g.blit(ICONS, x + 8, y + 8, card.metric().ordinal() * 16, 0, 16, 16, 160, 16);
            String label = Component.translatable("record.better_deaths_door." + card.metric().name().toLowerCase(Locale.ROOT)).getString();
            text(g, label + " · " + format(card.metric(), card.value()), x + 30, y + 6, w - 38, INK);
            text(g, badges(card), x + 30, y + 19, w - 38, GOLD);
            String comparison = card.previousBest() > 0 ? "Previous best " + format(card.metric(), card.previousBest())
                    + (card.value() > card.previousBest() ? " · +" + format(card.metric(), card.value() - card.previousBest()) : "")
                    : "First completed-life baseline";
            text(g, comparison, x + 10, y + 33, w - 20, MUTED);
            int barX = x + 10, barY = y + l.cardHeight() - 10, barW = Math.max(1, w - 20);
            double maximum = Math.max(1, Math.max(card.value(), card.previousBest()));
            g.fill(barX, barY, barX + barW, barY + 4, 0xFF10221C);
            g.fill(barX, barY, barX + (int) Math.round(barW * card.value() / maximum), barY + 4, 0xFF6FA18A);
            if (card.previousBest() > 0) {
                int mark = barX + Math.min(barW - 1, (int) Math.round(barW * card.previousBest() / maximum));
                g.fill(mark, barY - 2, mark + 1, barY + 6, GOLD);
            }
        }
    }
    private static String badges(LifeRecap.Card card) {
        return card.badges().stream().map(badge -> switch (badge) {
            case BEST -> "NEW BEST";
            case MILESTONE -> "MILESTONE " + card.milestoneTier();
            case MATCHED -> "MATCHED BEST";
            case NEAR -> "CLOSE TO YOUR BEST";
            case FIRST -> "FIRST RECORD";
        }).collect(java.util.stream.Collectors.joining(" · "));
    }
    private static void drawDetails(GuiGraphics g, DeathRecapLayout l, int y) {
        int x = l.x() + 10, w = l.width() - 20;
        var damage = ClientRevivalState.recapDamage();
        text(g, "DAMAGE RECORD · " + damage.hits() + " accepted hits", x, y + 3, w, GOLD);
        text(g, "Incoming " + amount(damage.incoming()) + " · Mitigated " + amount(damage.mitigation()), x, y + 19, w, INK);
        text(g, "Absorbed " + amount(damage.absorption()) + " · Applied " + amount(damage.applied()), x, y + 34, w, INK);
        text(g, "HP lost " + amount(damage.healthLost()) + " · Unknown " + amount(damage.unknown()), x, y + 49, w, INK);
        double total = Math.max(1, damage.incoming()); int barY = y + 66;
        g.fill(x, barY, x + w, barY + 6, FIELD);
        int a = (int) Math.round(w * Math.max(0, Math.min(1, damage.absorption() / total)));
        int m = (int) Math.round(w * Math.max(0, Math.min(1, damage.mitigation() / total)));
        int h = (int) Math.round(w * Math.max(0, Math.min(1, damage.healthLost() / total)));
        g.fill(x, barY, x + a, barY + 6, 0xFF597F75);
        g.fill(x + a, barY, Math.min(x + w, x + a + m), barY + 6, GREEN);
        g.fill(Math.min(x + w, x + a + m), barY, Math.min(x + w, x + a + m + h), barY + 6, 0xFFED9E91);
        int bodyY = y + 90;
        text(g, "BODY RECORD · active + treated", x, bodyY, w, GOLD);
        int columns = l.width() < 340 ? 1 : 2, cellWidth = w / columns;
        BodyView body = ClientRevivalState.recap();
        for (Region region : Region.values()) {
            int i = region.ordinal(), cx = x + i % columns * cellWidth, cy = bodyY + 18 + i / columns * 56;
            if (body == null) { text(g, "Body recap unavailable", cx, cy + 4, cellWidth - 8, MUTED); continue; }
            var r = body.region(region);
            g.fill(cx, cy, cx + cellWidth - 6, cy + 50, FIELD);
            text(g, BodyView.label(region), cx + 5, cy + 4, cellWidth - 16, INK);
            text(g, "Active " + r.total() + " · " + types(r, false), cx + 5, cy + 18, cellWidth - 16, 0xFFED9E91);
            text(g, "Treated " + r.treated() + " · " + types(r, true), cx + 5, cy + 32, cellWidth - 16, GREEN);
        }
        int metricsY = bodyY + 18 + (columns == 1 ? 6 : 3) * 56 + 20;
        if (frozen != null) for (LifeMetric metric : LifeMetric.values()) {
            String label = Component.translatable("record.better_deaths_door." + metric.name().toLowerCase(Locale.ROOT)).getString();
            text(g, label + ": " + format(metric, value(metric)), x, metricsY + metric.ordinal() * 15, w, INK);
        }
    }
    private static String types(BodyView.RegionView region, boolean treated) {
        StringBuilder text = new StringBuilder();
        for (MaimType type : MaimType.values()) {
            int count = treated ? region.treatedCount(type) : region.count(type);
            if (count > 0) { if (!text.isEmpty()) text.append(" "); text.append(BodyView.label(type)).append(" ").append(count); }
        }
        return text.isEmpty() ? "none" : text.toString();
    }
    public static String format(LifeMetric metric, double value) {
        return switch (metric) {
            case DURATION -> {
                long seconds = (long) (value / 20);
                yield String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
            }
            case HUNGER_RESTORED -> amount(value / 2) + " haunches";
            case HP_LOST -> amount(value / 2) + " hearts";
            default -> String.format(Locale.ROOT, "%,.0f", value);
        };
    }
    private static String amount(double value) {
        return !Double.isFinite(value) ? "unknown" : Math.abs(value) >= 10000
                ? String.format(Locale.ROOT, "%.2g", value) : String.format(Locale.ROOT, "%.1f", value);
    }
    private static void text(GuiGraphics g, String value, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        String shown = font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
        g.drawString(font, shown, x, y, color, false);
    }
    private static void centered(GuiGraphics g, String value, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        text(g, value, x + Math.max(0, (width - font.width(value)) / 2), y, width, color);
    }
    private static void drawButtons(DeathScreen screen, ScreenEvent.Render.Post event) {
        var g = event.getGuiGraphics(); var font = Minecraft.getInstance().font;
        for (var listener : screen.children()) if (listener instanceof Button button && button.visible) {
            int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
            boolean hover = button.active && event.getMouseX() >= x && event.getMouseX() < x + w
                    && event.getMouseY() >= y && event.getMouseY() < y + h;
            g.fill(x, y, x + w, y + h, !button.active ? 0xFF22362D : hover ? 0xFF547B61 : 0xFF314F3E);
            g.fill(x, y, x + w, y + 1, GOLD);
            g.drawString(font, button.getMessage(), x + (w - font.width(button.getMessage())) / 2,
                    y + (h - font.lineHeight) / 2, button.active ? INK : MUTED, false);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof DeathScreen screen) || !SESSION.owns(screen)) return;
        var l = layout(screen);
        if (!l.contentContains(event.getMouseX(), event.getMouseY())) return;
        scroll = l.clampScroll(scroll - (int) Math.signum(event.getScrollDelta()) * 28);
        event.setCanceled(true);
    }
}
