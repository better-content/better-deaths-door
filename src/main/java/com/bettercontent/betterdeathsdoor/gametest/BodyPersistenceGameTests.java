package com.bettercontent.betterdeathsdoor.gametest;

import com.bettercontent.betterdeathsdoor.RevivalConfig;
import com.bettercontent.betterdeathsdoor.DamageLedger;
import com.bettercontent.betterdeathsdoor.RevivalManager;
import com.bettercontent.betterdeathsdoor.RevivalMod;
import com.bettercontent.betterdeathsdoor.state.BodyState;
import com.bettercontent.betterdeathsdoor.state.MaimType;
import com.bettercontent.betterdeathsdoor.state.Region;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(RevivalMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyPersistenceGameTests {
    private static final String RECAP = "better_deaths_door:recap";
    private static final String FINALIZED = "better_deaths_door:finalized";

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void deadReconnectRetainsFrozenRecapAndRespawnClearsIt(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.run(() -> {
            BodyState body = RevivalManager.state(f.player);
            body.addMaim(Region.HEAD, MaimType.OPENED, RevivalManager.now(f.player));
            body.addMaim(Region.LEFT_LEG, MaimType.CRACKED, RevivalManager.now(f.player));
            body.cureOldest(Region.LEFT_LEG, MaimType.CRACKED, "minecraft:stick", RevivalManager.now(f.player));
            body.addTrauma(RevivalManager.now(f.player));
            f.player.hurt(f.player.damageSources().fellOutOfWorld(), 100);
            require(RevivalManager.isFinalized(f.player), "Fixture failed to complete ordinary final death");
            CompoundTag recap = f.player.getPersistentData().getCompound(RECAP).copy();
            long frozenTick = recap.getLong("tick");
            BodyState frozen = BodyState.load(recap.getCompound("body"), frozenTick);
            require(frozen.activeMaims().size() == 1 && frozen.treatmentHistory().size() == 1, "Death recap did not freeze active and treated injuries");
            require(recap.getCompound("tuning").getDouble("v0") == RevivalConfig.tuning().globalDeathPerMaim(), "Recap did not save actual death coefficients");
            require(recap.getCompound("tuning").getInt("trauma") == RevivalConfig.tuning().traumaLifetimeTicks(), "Recap did not save timing coefficients");
            DamageLedger.Summary damage = DamageLedger.Summary.load(recap.getCompound("damage"));
            require(damage.hits() == 1 && damage.incoming() >= 100 && damage.applied() >= 100,
                "Final recap did not freeze the accepted pre-mitigation hit and applied outcome");
            require(damage.healthLost() > 0 && Math.abs(damage.unknown()) < .01,
                "Final recap lost actual HP loss or invented an unobserved mitigation remainder");
            CompoundTag savedPlayer = f.player.saveWithoutId(new CompoundTag());
            RevivalManager.logout(f.player);
            Connection reconnectedWire = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel reconnectedChannel = new EmbeddedChannel(reconnectedWire);
            ServerPlayer reconnected = new ServerPlayer(f.player.server, helper.getLevel(), f.player.getGameProfile());
            reconnected.connection = new ServerGamePacketListenerImpl(f.player.server, reconnectedWire, reconnected);
            try {
                reconnected.load(savedPlayer);
                RevivalManager.login(reconnected);
                require(RevivalManager.isFinalized(reconnected), "Dead reconnect lost the finalized-life boundary");
                require(reconnected.getPersistentData().getCompound(RECAP).equals(recap), "Dead reconnect changed frozen recap data, clock, or tuning");
                require(RevivalManager.state(reconnected).activeMaims().isEmpty() && RevivalManager.state(reconnected).treatmentHistory().isEmpty(), "Dead reconnect restored active current-life injuries");
                ServerPlayer respawned = new ServerPlayer(f.player.server, helper.getLevel(), f.player.getGameProfile());
                respawned.getPersistentData().put(RECAP, recap.copy()); respawned.getPersistentData().putBoolean(FINALIZED, true);
                RevivalManager.clonePlayer(reconnected, respawned, true);
                require(!RevivalManager.isFinalized(respawned) && !respawned.getPersistentData().contains(RECAP), "Respawn retained the old life's recap/finalized marker");
                require(RevivalManager.state(respawned).activeMaims().isEmpty() && RevivalManager.state(respawned).treatmentHistory().isEmpty(), "Respawn retained current-life maims or history");
                RevivalManager.logout(respawned);
            } finally {
                RevivalManager.logout(reconnected); reconnectedChannel.finishAndReleaseAll();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void nonDeathClonePreservesBodyAndIndependentDeadlines(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.run(() -> {
            BodyState body = RevivalManager.state(f.player);
            body.enterDoor(RevivalManager.now(f.player), RevivalConfig.tuning());
            RevivalManager.setHealthInternal(f.player, RevivalManager.SENTINEL);
            body.addMaim(Region.RIGHT_ARM, MaimType.BURNT, RevivalManager.now(f.player));
            body.addMaim(Region.LEFT_LEG, MaimType.CRACKED, RevivalManager.now(f.player));
            body.cureOldest(Region.LEFT_LEG, MaimType.CRACKED, "minecraft:stick", RevivalManager.now(f.player));
            body.addTrauma(RevivalManager.now(f.player) - 5); body.addTrauma(RevivalManager.now(f.player));
            RevivalManager.refresh(f.player);
            CompoundTag expected = body.save();
            ServerPlayer next = new ServerPlayer(f.player.server, helper.getLevel(), f.player.getGameProfile());
            RevivalManager.setHealthInternal(next, RevivalManager.SENTINEL);
            RevivalManager.clonePlayer(f.player, next, false);
            try {
                require(RevivalManager.state(next).save().equals(expected), "Non-death clone changed maims, history, or independent clock deadlines");
                require(RevivalManager.snapshot(next).health() == 0, "Non-death clone lost semantic zero");
                RevivalManager.state(next).addMaim(Region.HEAD, MaimType.OPENED, RevivalManager.now(next));
                require(body.activeMaims().size() == 1, "Clone shared mutable injury storage with its predecessor");
            } finally { RevivalManager.logout(next); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void sameNamespaceDownedSaveMigratesWithoutInventedInjuries(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.run(() -> {
            RevivalManager.logout(f.player);
            CompoundTag legacy = new CompoundTag(); legacy.putInt("ticksLeft", 900); legacy.putFloat("reviveProgress", 25);
            f.player.getPersistentData().put("better_deaths_door:state", legacy);
            f.player.setForcedPose(Pose.SWIMMING);
            BodyState migrated = RevivalManager.state(f.player);
            require(migrated.atDoor() && f.player.isAlive(), "Existing downed episode was not migrated into active Death's Door");
            require(migrated.healingLockedUntil() == RevivalManager.now(f.player) + RevivalConfig.tuning().healingLockTicks(), "Migration did not start its healing lock");
            require(migrated.activeMaims().isEmpty() && migrated.treatmentHistory().isEmpty(), "Migration fabricated bodily records");
            require(f.player.getForcedPose() == null, "Migration retained forced crawling");
            require(!f.player.getPersistentData().contains("better_deaths_door:state"), "Migration retained the obsolete authority tag");
            require(f.player.getPersistentData().contains(BodyState.ROOT_TAG), "Migration failed to persist the replacement state");
        });
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class Fixture {
        final GameTestHelper helper; final ServerPlayer player; final EmbeddedChannel channel;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "save-" + UUID.randomUUID().toString().substring(0, 8)));
            Connection wire = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(wire);
            player.server.getPlayerList().placeNewPlayer(wire, player); player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
            var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1)); player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            player.getFoodData().setFoodLevel(19);
        }
        void run(Runnable checks) {
            helper.runAtTickTime(65, () -> {
                try { checks.run(); cleanup(); helper.succeed(); }
                catch (Throwable failure) { failure.printStackTrace(); cleanup(); helper.fail(failure.toString()); }
            });
        }
        void cleanup() { player.server.getPlayerList().remove(player); player.discard(); channel.finishAndReleaseAll(); }
    }
}
