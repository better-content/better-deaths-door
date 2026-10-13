package com.bettercontent.betterdeathsdoor.heartreview;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import com.bettercontent.betterdeathsdoor.RevivalMod;
import com.bettercontent.betterdeathsdoor.records.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** NONSHIPPING isolated scenes. Prepared ledger inputs; final death/packets/screens remain native. */
@Mod.EventBusSubscriber(modid = HeartReviewSupport.MOD_ID)
public final class LifeVisualReview {
    private static final Set<String> SCENES = Set.of("none", "first", "best", "milestone", "matched", "near", "partial", "ineligible");
    private LifeVisualReview() {}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) throws Exception {
        String directory = System.getProperty("life.review.root");
        if (directory == null || event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().size() != 1) return;
        Path root = Path.of(directory), request = root.resolve("life-scene.request");
        if (!Files.isRegularFile(request)) return;
        String scene = Files.readString(request).strip();
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        if (!player.isAlive()) return; // Native respawn must complete before the next fixture.
        Files.delete(request);
        if (!SCENES.contains(scene)) throw new IllegalArgumentException("Unknown life review scene " + scene);
        RevivalManager.debugScenario(player, "long-history");
        player.setAbsorptionAmount(0); player.setHealth(player.getMaxHealth());
        player.experienceLevel = 0; player.experienceProgress = 0;
        // This is a fresh one-player isolated ledger, never a gameplay command or production API.
        var data = new LifeRecordsData();
        var record = data.getOrCreate(player.getUUID(), scene.equals("partial"));
        if (!Set.of("first", "partial", "ineligible").contains(scene)) {
            for (LifeMetric metric : LifeMetric.values()) {
                if (scene.equals("milestone") && Set.of(LifeMetric.BLOCKS_MINED, LifeMetric.HUNGER_RESTORED, LifeMetric.CHECKS_SURVIVED).contains(metric)) continue;
                double old = switch (scene) {
                    case "best" -> switch (metric) { case XP_LEVEL -> 20; case MOB_KILLS -> 100; case INJURIES_TREATED -> 10; default -> 1_000_000; };
                    case "matched", "near" -> switch (metric) { case XP_LEVEL -> 20; case MOB_KILLS -> 50; case HP_LOST -> 100; default -> 1_000_000; };
                    default -> 1_000_000;
                };
                record.add(metric, old);
            }
            record.freeze(); record.nextLife();
        }
        switch (scene) {
            case "first", "partial", "ineligible" -> {
                record.add(LifeMetric.DURATION, 24_000); record.level(10);
                record.add(LifeMetric.MOB_KILLS, 3); record.add(LifeMetric.BLOCKS_MINED, 25);
            }
            case "best" -> {
                record.level(35); record.add(LifeMetric.MOB_KILLS, 180); record.add(LifeMetric.INJURIES_TREATED, 20);
            }
            case "milestone" -> {
                record.add(LifeMetric.BLOCKS_MINED, 500); record.add(LifeMetric.HUNGER_RESTORED, 1000);
                record.add(LifeMetric.CHECKS_SURVIVED, 5);
            }
            case "matched" -> {
                record.level(20); record.add(LifeMetric.MOB_KILLS, 50); record.add(LifeMetric.HP_LOST, 100 - player.getHealth());
            }
            case "near" -> {
                record.level(18); record.add(LifeMetric.MOB_KILLS, 45); record.add(LifeMetric.HP_LOST, 91 - player.getHealth());
            }
            default -> { }
        }
        if (scene.equals("ineligible")) record.invalidate();
        server.overworld().getDataStorage().set(RevivalMod.MOD_ID + "_life_records", data);
        // getOrCreate already dirtied this newly prepared ledger.
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        if (player.isAlive() || record.latest() == null) throw new IllegalStateException("Scene did not commit a native final death");
        Files.writeString(root.resolve("life-server-" + scene + ".txt"), "nativePlayer=" + player.getUUID()
                + "\nscene=" + scene + "\nrecap=" + record.latest() + "\n");
        System.out.println("LIFE_REVIEW_NATIVE_DEATH " + scene + " life=" + record.lifeId());
    }
}
