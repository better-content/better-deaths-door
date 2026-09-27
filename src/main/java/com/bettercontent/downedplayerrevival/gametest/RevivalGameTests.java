package com.bettercontent.downedplayerrevival.gametest;

import com.bettercontent.downedplayerrevival.RevivalConfig;
import com.bettercontent.downedplayerrevival.DamageLedger;
import com.bettercontent.downedplayerrevival.RevivalManager;
import com.bettercontent.downedplayerrevival.RevivalMod;
import com.bettercontent.downedplayerrevival.api.event.InjuryEvent;
import com.bettercontent.downedplayerrevival.state.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Real ServerPlayer hurt/heal/death calls exercise transformed production code. No player input is synthesized. */
@GameTestHolder(RevivalMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RevivalGameTests {
    private RevivalGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void lethalDamageEntersDoorWithoutDeathOrLossOfAgency(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.atReady(() -> {
            f.player.experienceLevel = 7;
            f.player.setSprinting(true);
            require(f.player.hurt(f.player.damageSources().fall(), 100), "Lethal fall was not accepted");
            BodyState body = RevivalManager.state(f.player);
            require(body.atDoor() && f.player.isAlive(), "Lethal damage did not preserve the living player at Death's Door");
            require(body.activeMaims().size() == 1, "Zero crossing must add exactly one maim");
            require(body.traumaCount(RevivalManager.now(f.player)) == 1, "Accepted entry hit must add one trauma");
            require(RevivalManager.snapshot(f.player).health() == 0, "Snapshot must expose semantic zero");
            require(f.player.getForcedPose() == null && f.player.getPose() != Pose.SWIMMING, "Death's Door forced a crawling pose");
            require(f.player.isSprinting(), "Death's Door disabled sprinting");
            require(f.probe.deaths == 0 && f.probe.finalDeaths == 0 && f.player.experienceLevel == 7, "Surviving entry ran final-death consequences");
            require(!f.player.getPersistentData().contains("downed_player_revival:recap"), "Surviving zero crossing created a final-death recap");
            require(DamageLedger.snapshot(f.player).hits() == 1, "Surviving hit was not counted once in this life");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void allHealingRejectsUntilTickFortyAndThenPreservesInjuries(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.runAtTickTime(65, () -> {
            try {
                require(f.player.hurt(f.player.damageSources().fall(), 100), "Entry damage was rejected");
                long entered = RevivalManager.now(f.player);
                f.player.heal(4);
                require(RevivalManager.state(f.player).atDoor(), "heal() bypassed the healing lock");
                f.player.setHealth(4);
                require(RevivalManager.state(f.player).atDoor(), "Direct health restoration bypassed the lock");
                require(f.player.getHealth() == RevivalManager.SENTINEL, "Rejected healing changed the sentinel");
                helper.runAtTickTime(helper.getTick() + 39, () -> {
                    try {
                        require(RevivalManager.now(f.player) == entered + 39, "Fixture clock drifted before the lock boundary");
                        f.player.heal(4);
                        require(RevivalManager.state(f.player).atDoor(), "Healing unlocked before tick forty");
                    } catch (Throwable failure) { f.fail(failure); }
                });
                helper.runAtTickTime(helper.getTick() + 40, () -> f.finish(() -> {
                    f.player.heal(4);
                    require(!RevivalManager.state(f.player).atDoor(), "Positive healing did not exit Death's Door at tick forty");
                    close(f.player.getHealth(), 4, "Healing retained an extra sentinel buffer");
                    require(RevivalManager.state(f.player).activeMaims().size() == 1, "Healing erased the injury");
                    require(f.probe.deaths == 0 && f.probe.finalDeaths == 0, "Healing a survived episode ran death consequences");
                }));
            } catch (Throwable failure) { f.fail(failure); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void positiveHpAndAbsorptionProduceTraumaWithoutMaims(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.atReady(() -> {
            require(f.player.hurt(f.player.damageSources().generic(), 2), "Ordinary hit was rejected");
            close(f.player.getHealth(), 18, "Ordinary positive-HP damage changed");
            require(RevivalManager.state(f.player).activeMaims().isEmpty(), "Positive HP damage maimed");
            require(RevivalManager.state(f.player).traumaCount(RevivalManager.now(f.player)) == 1, "Ordinary hit did not add trauma");
            f.player.invulnerableTime = 0;
            f.player.setAbsorptionAmount(4);
            f.player.hurt(f.player.damageSources().generic(), 2);
            close(f.player.getHealth(), 18, "Absorption did not protect HP");
            close(f.player.getAbsorptionAmount(), 2, "Absorption cost changed");
            require(RevivalManager.state(f.player).traumaCount(RevivalManager.now(f.player)) == 2, "Fully absorbed hit must add trauma once");
            require(RevivalManager.state(f.player).activeMaims().isEmpty(), "Fully absorbed hit maimed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void hurtImmunityRejectsNoiseButAddsNoNewRefractoryPeriod(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.atReady(() -> {
            require(f.player.hurt(f.player.damageSources().generic(), 2), "Initial hit was rejected");
            require(!f.player.hurt(f.player.damageSources().generic(), 2), "Equal repeat bypassed vanilla hurt immunity");
            require(RevivalManager.state(f.player).traumaCount(RevivalManager.now(f.player)) == 1, "Rejected hit generated trauma");
            // Vanilla accepts only the increase during immunity. No additional mod cooldown may block it.
            require(f.player.hurt(f.player.damageSources().generic(), 4), "Larger same-tick hit was blocked by an extra cooldown");
            require(RevivalManager.state(f.player).traumaCount(RevivalManager.now(f.player)) == 2, "Accepted same-tick delta did not add trauma");
            close(f.player.getHealth(), 16, "Vanilla hurt-immunity damage delta changed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void zeroMaimDoorHitGuaranteesOneAndPositiveHpProtectsAtCertainRisk(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.atReady(() -> {
            BodyState body = RevivalManager.state(f.player);
            body.enterDoor(RevivalManager.now(f.player), RevivalConfig.tuning());
            RevivalManager.setHealthInternal(f.player, RevivalManager.SENTINEL);
            require(f.player.hurt(f.player.damageSources().fall(), 2), "Death's Door hit was rejected");
            require(f.player.isAlive() && body.activeMaims().size() == 1, "Zero-maim hit did not guarantee precisely one injury");
            require(f.probe.deaths == 0, "Zero-maim hit entered final-death listeners");
            body.exitDoor();
            RevivalManager.setHealthInternal(f.player, .5f);
            for (int i = 0; i < 20; i++) body.addMaim(Region.LEFT_ARM, MaimType.CRACKED, RevivalManager.now(f.player));
            int before = body.activeMaims().size();
            f.player.invulnerableTime = 0;
            require(f.player.hurt(f.player.damageSources().fall(), 1000), "Positive-HP lethal hit was rejected");
            require(f.player.isAlive() && body.atDoor() && body.activeMaims().size() == before + 1, "Positive HP failed to protect against a death roll at certain injury risk");
            require(f.probe.deaths == 0, "Zero crossing at certain injury risk ran death listeners");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void finalDeathUsesOriginalSourceOnceAndTotemCannotPreventIt(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.atReady(() -> {
            f.player.experienceLevel = 7;
            f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            require(f.player.hurt(f.player.damageSources().fall(), 100), "Entry hit was rejected");
            require(f.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "Entry consumed a totem instead of entering Death's Door");
            for (int i = 0; i < 20; i++) RevivalManager.state(f.player).addMaim(Region.LEFT_ARM, MaimType.CRACKED, RevivalManager.now(f.player));
            f.player.invulnerableTime = 0;
            DamageSource original = f.player.damageSources().generic();
            f.player.hurt(original, 3);
            require(!f.player.isAlive() && RevivalManager.isFinalized(f.player), "Certain death was prevented, possibly by a totem");
            require(f.probe.deaths == 1 && f.probe.finalDeaths == 1, "Expected exactly one ordinary and confirmed final-death event");
            require(f.probe.source == original && f.probe.finalSource == original, "Final death lost original damage attribution");
            require(f.probe.xpAtDeath == 7, "Ordinary death listeners did not see held XP");
            require(RevivalManager.state(f.player).activeMaims().isEmpty(), "Confirmed final death retained current-life injuries");
            require(f.probe.recapMaims >= 21, "Final recap did not preserve active-at-death injuries");
            f.player.die(original);
            require(f.probe.deaths == 1 && f.probe.finalDeaths == 1, "Repeated die() duplicated final-death integrations");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void voidBypassesDoorAndCanceledDeathDoesNotFinalize(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.atReady(() -> {
            f.probe.cancel = true;
            f.player.hurt(f.player.damageSources().fellOutOfWorld(), 100);
            require(!RevivalManager.isFinalized(f.player) && f.probe.finalDeaths == 0, "Canceled ordinary death finalized bodily state");
            f.probe.cancel = false;
            RevivalManager.setHealthInternal(f.player, 20);
            f.player.invulnerableTime = 0;
            f.player.hurt(f.player.damageSources().fellOutOfWorld(), 100);
            require(RevivalManager.isFinalized(f.player) && f.probe.finalDeaths == 1, "Void did not bypass Death's Door");
            require(f.probe.recapMaims == 0, "Void bypass fabricated an injury");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void regionalAttributesComposeAndRecoverWithoutGrantingHp(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.atReady(() -> {
            double normalMax = f.player.getMaxHealth();
            double normalSpeed = f.player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            BodyState body = RevivalManager.state(f.player);
            for (int i = 0; i < 3; i++) {
                body.addMaim(Region.TORSO, MaimType.BURNT, RevivalManager.now(f.player));
                body.addMaim(Region.LEFT_LEG, MaimType.CRACKED, RevivalManager.now(f.player));
            }
            for (int i = 0; i < 5; i++) body.addTrauma(RevivalManager.now(f.player));
            RevivalManager.refresh(f.player);
            close(f.player.getMaxHealth(), normalMax * .75, "Torso penalty did not compose with max health");
            close(f.player.getAttributeValue(Attributes.MOVEMENT_SPEED), normalSpeed * .75, "Leg penalty did not compose with movement");
            float injuredHealth = f.player.getHealth();
            while (body.cureOldest(Region.TORSO, MaimType.BURNT, "downed_player_revival:balm", RevivalManager.now(f.player)).isPresent()) { }
            RevivalManager.refresh(f.player);
            close(f.player.getMaxHealth(), normalMax, "Curing torso failed to restore maximum health");
            close(f.player.getHealth(), injuredHealth, "Restoring maximum health granted free HP");
            require(body.treatmentHistory().size() == 3, "Cure lost treatment history");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void treatmentRequiresStartAndCuresWithoutItems(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.runAtTickTime(65, () -> {
            try {
                RevivalManager.state(f.player).addMaim(Region.LEFT_LEG, MaimType.BURNT, RevivalManager.now(f.player));
                f.player.getInventory().clearContent();
                RevivalManager.openBody(f.player, f.player);
                helper.runAtTickTime(helper.getTick() + 20, () -> {
                    try { require(RevivalManager.state(f.player).activeMaims().size() == 1, "Opening the screen started treatment");
                        RevivalManager.startTreatment(f.player, f.player);
                        helper.runAtTickTime(helper.getTick() + 45, () -> f.finish(() -> {
                            require(RevivalManager.state(f.player).activeMaims().isEmpty(), "Completed treatment did not cure");
                            require(RevivalManager.state(f.player).treatmentHistory().size() == 1, "Completed treatment lost history");
                            require(RevivalManager.state(f.player).treatmentHistory().get(0).itemId().equals("downed_player_revival:care"), "Care recorded a consumable");
                            require(f.player.getInventory().isEmpty(), "Treatment created or consumed inventory items");
                        }));
                    } catch (Throwable failure) { f.fail(failure); }
                });
            } catch (Throwable failure) { f.fail(failure); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 230)
    public static void treatmentQueueTreatsArmsFirst(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.runAtTickTime(65, () -> {
            try {
                var body = RevivalManager.state(f.player);
                body.addMaim(Region.HEAD, MaimType.CRACKED, RevivalManager.now(f.player));
                body.addMaim(Region.LEFT_ARM, MaimType.BURNT, RevivalManager.now(f.player));
                RevivalManager.openBody(f.player, f.player);
                require(RevivalManager.treatmentOrder().get(0) == Region.LEFT_ARM, "Arms must be treated first");
                RevivalManager.startTreatment(f.player, f.player);
                helper.runAtTickTime(helper.getTick() + 80, () -> {
                    try {
                        require(body.activeMaims().size() == 1 && body.activeMaims().get(0).region() == Region.HEAD,
                            "Queue did not treat the arm before the head");
                    } catch (Throwable failure) { f.fail(failure); }
                });
                helper.runAtTickTime(helper.getTick() + 135, () -> f.finish(() -> {
                    require(body.activeMaims().isEmpty(), "Queue did not continue to the next region");
                    require(body.treatmentHistory().size() == 2, "Queue did not record each completed step once");
                }));
            } catch (Throwable failure) { f.fail(failure); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void damageInterruptsTreatmentWithoutConsumption(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.runAtTickTime(65, () -> {
            try {
                RevivalManager.state(f.player).addMaim(Region.LEFT_LEG, MaimType.CRACKED, RevivalManager.now(f.player));
                f.player.getInventory().clearContent();
                RevivalManager.openBody(f.player, f.player);
                RevivalManager.startTreatment(f.player, f.player);
                helper.runAtTickTime(helper.getTick() + 10, () -> {
                    try { require(f.player.hurt(f.player.damageSources().generic(), 1), "Interrupting damage was rejected"); }
                    catch (Throwable failure) { f.fail(failure); }
                });
                helper.runAtTickTime(helper.getTick() + 50, () -> f.finish(() -> {
                    require(RevivalManager.state(f.player).activeMaims().size() == 1, "Interrupted treatment silently completed");
                    require(RevivalManager.state(f.player).treatmentHistory().isEmpty(), "Interrupted treatment wrote history");
                    require(f.player.getInventory().isEmpty(), "Interrupted treatment changed inventory");
                }));
            } catch (Throwable failure) { f.fail(failure); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void concurrentSelfAndTeammateTreatmentCuresAndConsumesOnlyOnce(GameTestHelper helper) {
        Fixture subject = new Fixture(helper);
        Fixture teammate = new Fixture(helper);
        helper.runAtTickTime(65, () -> {
            try {
                teammate.player.setPos(subject.player.getX() + 1, subject.player.getY(), subject.player.getZ());
                RevivalManager.state(subject.player).addMaim(Region.LEFT_LEG, MaimType.CRACKED, RevivalManager.now(subject.player));
                subject.player.getInventory().clearContent(); teammate.player.getInventory().clearContent();
                RevivalManager.openBody(subject.player, subject.player);
                RevivalManager.openBody(teammate.player, subject.player);
                RevivalManager.startTreatment(subject.player, subject.player);
                RevivalManager.startTreatment(teammate.player, subject.player);
                helper.runAtTickTime(helper.getTick() + 45, () -> {
                    try {
                        require(RevivalManager.state(subject.player).activeMaims().isEmpty(), "Concurrent treatment failed to cure");
                        require(RevivalManager.state(subject.player).treatmentHistory().size() == 1, "Concurrent treatment wrote duplicate history");
                        require(subject.player.getInventory().isEmpty() && teammate.player.getInventory().isEmpty(), "Concurrent treatment changed inventory");
                        teammate.cleanup(); subject.finish(() -> { });
                    } catch (Throwable failure) { teammate.cleanup(); subject.fail(failure); }
                });
            } catch (Throwable failure) { teammate.cleanup(); subject.fail(failure); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void reloadPreservesDoorDeadlineMaimsTraumaAndTreatmentHistory(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.atReady(() -> {
            f.player.hurt(f.player.damageSources().fall(), 100);
            BodyState body = RevivalManager.state(f.player);
            body.addMaim(Region.LEFT_ARM, MaimType.OPENED, RevivalManager.now(f.player));
            body.cureOldest(Region.LEFT_ARM, MaimType.OPENED, "downed_player_revival:soocher", RevivalManager.now(f.player));
            long deadline = body.healingLockedUntil();
            var maims = body.activeMaims(); var history = body.treatmentHistory(); var trauma = body.traumaExpiries();
            RevivalManager.logout(f.player);
            BodyState restored = RevivalManager.state(f.player);
            require(restored != body, "Logout did not evict the cached body");
            require(restored.atDoor() && restored.healingLockedUntil() == deadline, "Reload changed Death's Door or its deadline");
            require(restored.activeMaims().equals(maims) && restored.treatmentHistory().equals(history), "Reload changed injuries or treatment history");
            require(restored.traumaExpiries().equals(trauma), "Reload changed independent trauma deadlines");
            require(f.probe.deaths == 0, "Disconnect caused final death");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void actualPoisonWitherAndFireRespectTheirDamageAndImmunityRules(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.runAtTickTime(65, () -> {
            try {
                BodyState body = RevivalManager.state(f.player);
                body.enterDoor(RevivalManager.now(f.player));
                RevivalManager.setHealthInternal(f.player, RevivalManager.SENTINEL);
                net.minecraft.world.effect.MobEffects.POISON.applyEffectTick(f.player, 0);
                require(body.activeMaims().isEmpty() && body.traumaCount(RevivalManager.now(f.player)) == 0, "Vanilla poison should stop below one HP and produce no accepted hit");
                net.minecraft.world.effect.MobEffects.WITHER.applyEffectTick(f.player, 0);
                require(body.activeMaims().size() == 1 && body.traumaCount(RevivalManager.now(f.player)) == 1, "Actual wither tick did not guarantee one injury and one trauma");
                net.minecraft.world.effect.MobEffects.WITHER.applyEffectTick(f.player, 0);
                require(body.activeMaims().size() == 1 && body.traumaCount(RevivalManager.now(f.player)) == 1, "Immediate repeated wither tick bypassed vanilla hurt immunity");
                Maim maim = body.activeMaims().get(0);
                body.cureOldest(maim.region(), maim.type(), maim.type().defaultTreatment(), RevivalManager.now(f.player));
                helper.runAtTickTime(helper.getTick() + 20, () -> f.finish(() -> {
                    require(f.player.hurt(f.player.damageSources().onFire(), 1), "Fire remained blocked after vanilla hurt immunity expired");
                    require(body.activeMaims().size() == 1 && body.activeMaims().get(0).type() == MaimType.BURNT, "Accepted burning damage did not create exactly one Burnt injury");
                    require(body.traumaCount(RevivalManager.now(f.player)) == 2, "Accepted fire tick did not add precisely one trauma");
                    require(body.atDoor() && f.player.isAlive(), "Zero active maims failed to guarantee survival of the fire tick");
                }));
            } catch (Throwable failure) { f.fail(failure); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void acceptedAbsorbedHitPublishesOnlyExistingInjuryAmplification(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.atReady(() -> {
            f.player.setAbsorptionAmount(10);
            require(f.player.hurt(f.player.damageSources().generic(), 1), "Absorbed hit not accepted");
            require(f.probe.traumas == 1 && f.probe.amplifications == 0, "Healthy hit claimed an existing injury");
            RevivalManager.state(f.player).addMaim(Region.LEFT_ARM, MaimType.CRACKED, RevivalManager.now(f.player));
            f.player.invulnerableTime = 0;
            require(f.player.hurt(f.player.damageSources().generic(), 1), "Second absorbed hit not accepted");
            require(f.probe.traumas == 2 && f.probe.amplifications == 1, "Absorbed hit did not publish existing arm amplification");
            require(RevivalManager.state(f.player).activeMaims().size() == 1, "Absorbed hit added an injury");
        });
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void close(double actual, double expected, String message) { require(Math.abs(actual - expected) < .00001, message + " (expected " + expected + ", got " + actual + ")"); }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer player;
        final DeathProbe probe;
        final io.netty.channel.embedded.EmbeddedChannel channel;
        boolean finished;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "body-" + UUID.randomUUID().toString().substring(0, 8)));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            player.getFoodData().setFoodLevel(19);
            probe = new DeathProbe(player);
            MinecraftForge.EVENT_BUS.register(probe);
        }
        void atReady(Runnable checks) { helper.runAtTickTime(65, () -> finish(checks)); }
        void finish(Runnable checks) {
            if (finished) return;
            try { checks.run(); cleanup(); helper.succeed(); }
            catch (Throwable failure) { fail(failure); }
        }
        void fail(Throwable failure) {
            if (finished) return;
            failure.printStackTrace(); cleanup(); helper.fail(failure.toString());
        }
        void cleanup() {
            if (finished) return;
            finished = true;
            MinecraftForge.EVENT_BUS.unregister(probe);
            player.server.getPlayerList().remove(player);
            player.discard();
            channel.finishAndReleaseAll();
        }
    }
    public static final class DeathProbe {
        final ServerPlayer player;
        int deaths, finalDeaths, xpAtDeath, recapMaims, traumas, amplifications;
        DamageSource source, finalSource;
        boolean cancel;
        DeathProbe(ServerPlayer player) { this.player = player; }
        @SubscribeEvent(priority = EventPriority.NORMAL)
        public void ordinary(LivingDeathEvent event) {
            if (event.getEntity() != player) return;
            if (cancel) { event.setCanceled(true); return; }
            deaths++; source = event.getSource(); xpAtDeath = player.experienceLevel;
        }
        @SubscribeEvent public void trauma(InjuryEvent.TraumaIncreased event) {
            if(event.getEntity()!=player)return;
            traumas++;
            if(event.amplifiedExistingInjury())amplifications++;
        }
        @SubscribeEvent
        public void finalized(InjuryEvent.FinalDeath event) {
            if (event.getEntity() != player) return;
            finalDeaths++; finalSource = event.source(); recapMaims = event.snapshot().activeMaims().size();
        }
    }
}
