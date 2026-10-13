package com.bettercontent.betterdeathsdoor.heartreview;

import com.bettercontent.betterdeathsdoor.client.ClientLifeRecords;
import com.bettercontent.betterdeathsdoor.client.InjuryClientConfig;
import com.bettercontent.betterdeathsdoor.review.DeathPresentationObservation;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Production framebuffer observations; ledger/view fixtures are labeled API-prepared, not human input. */
@Mod.EventBusSubscriber(modid = HeartReviewSupport.MOD_ID, value = Dist.CLIENT)
public final class LifeVisualClient {
    private static final List<String> SCENES = List.of("none", "first", "best", "milestone", "matched", "near", "partial", "ineligible");
    private static final long[] TIMES = {0, 100, 175, 325, 475, 625, 775, 1300, 2200};
    private static int scene = -1, frame, readyTicks, waitingTicks;
    private static boolean waitingCaptured;
    private static Object observed;
    private static boolean capturing, detailsPrepared, awaitingRespawn;
    private static long finishedAt;
    private LifeVisualClient() {}
    private static Path root() { return Path.of(System.getProperty("life.review.root")); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (System.getProperty("life.review.root") == null || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            if (++waitingTicks % 100 == 0) {
                System.out.println("LIFE_REVIEW_WAITING " + (mc.screen == null ? "no-screen" : mc.screen.getClass().getName()));
                if (!waitingCaptured && mc.getOverlay() == null) {
                    waitingCaptured = true;
                    Screenshot.grab(mc.gameDirectory, "life-initial-wait.png", mc.getMainRenderTarget(),
                            message -> System.out.println("LIFE_REVIEW_INITIAL_CAPTURE " + message.getString()));
                }
            }
            return;
        }
        if (scene < 0 && ++readyTicks >= 40) {
            InjuryClientConfig.REDUCED_MOTION.set(Boolean.getBoolean("life.review.reduced"));
            InjuryClientConfig.SOUND.set(false);
            next();
        }
        if (!awaitingRespawn || Util.getMillis() - finishedAt < 300) return;
        if (scene + 1 == SCENES.size()) {
            Files.writeString(root().resolve("life-client-complete.txt"), "Native DeathScreen scenes captured: " + SCENES.size());
            mc.stop(); return;
        }
        if (!mc.player.isAlive()) {
            mc.player.respawn(); // Native ClientCommandPacket path. Not a pointer-control acceptance test.
            mc.setScreen(null); // Same client-side close used by native Respawn; no artificial input.
            return;
        }
        if (ClientLifeRecords.latest() != null) return;
        awaitingRespawn = false; next();
    }
    private static void next() throws Exception {
        scene++; frame = 0; observed = null; detailsPrepared = false;
        Files.writeString(root().resolve("life-scene.request"), SCENES.get(scene));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(TickEvent.RenderTickEvent event) throws Exception {
        // End-of-frame observes every Screen Render.Post subscriber, including Discovery LOWEST.
        if (event.phase != TickEvent.Phase.END || System.getProperty("life.review.root") == null
                || scene < 0 || awaitingRespawn || Minecraft.getInstance().getOverlay() != null
                || !(Minecraft.getInstance().screen instanceof DeathScreen screen)) return;
        var session = DeathPresentationObservation.lifeReviewSession();
        if (observed != screen) {
            observed = screen;
            log("EPISODE scene=" + SCENES.get(scene) + " screen=" + screen.getClass().getName()
                    + " identity=" + System.identityHashCode(screen) + " gui=" + screen.width + "x" + screen.height
                    + " reduced=" + InjuryClientConfig.REDUCED_MOTION.get());
        }
        long elapsed = session.elapsed();
        if (!detailsPrepared && elapsed >= 1050) {
            session.toggleDetails(); detailsPrepared = true;
            log("API_PREPARED_DETAILS elapsed=" + elapsed + " scene=" + SCENES.get(scene));
            return;
        }
        if (capturing || frame >= TIMES.length || elapsed < TIMES[frame]) return;
        var mc = Minecraft.getInstance();
        var recap = ClientLifeRecords.latest();
        String nativeButtons = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .map(b -> b.getMessage().getString() + ":active=" + b.active + ":visible=" + b.visible).toList().toString();
        String name = String.format(java.util.Locale.ROOT, "life-%s-%02d-%dms.png", SCENES.get(scene), frame, elapsed);
        log("FRAME name=" + name + " elapsed=" + elapsed + " details=" + session.details()
                + " buttons=" + nativeButtons + " recap=" + recap);
        capturing = true;
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
            System.out.println("LIFE_REVIEW_CAPTURE " + name + " " + message.getString());
            capturing = false; frame++;
            if (frame == TIMES.length) { awaitingRespawn = true; finishedAt = Util.getMillis(); }
        }));
    }
    private static void log(String line) throws Exception {
        Files.writeString(root().resolve("life-frame-sequence.txt"), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("LIFE_REVIEW " + line);
    }
}
