package com.bettercontent.betterdeathsdoor.gametest;

import com.bettercontent.betterdeathsdoor.*;
import com.bettercontent.betterdeathsdoor.records.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(RevivalMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeRecordsGameTests {
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void nativeFoodStatsDamageXpAndLethalHitOnce(GameTestHelper helper) {
        Fixture f = new Fixture(helper, false);
        f.run(() -> {
            f.player.setExperienceLevels(37);
            f.player.getFoodData().setFoodLevel(19);
            f.player.eat(helper.getLevel(), Items.APPLE.getDefaultInstance());
            f.player.eat(helper.getLevel(), Items.APPLE.getDefaultInstance());
            f.player.getFoodData().setFoodLevel(5); // Unrelated setter is not food restoration.
            f.player.awardStat(Stats.CUSTOM.get(Stats.MOB_KILLS), 2);
            f.player.awardStat(Stats.BLOCK_MINED.get(Blocks.STONE), 3);
            f.player.setAbsorptionAmount(2);
            f.player.hurt(f.player.damageSources().generic(), 2);
            f.player.invulnerableTime = 0;
            RevivalManager.setHealthInternal(f.player, 12); // Command/modifier resets are not accepted damage.
            f.player.hurt(f.player.damageSources().fellOutOfWorld(), 100);
            LifeRecap recap = f.record().latest();
            require(recap != null && !recap.partial(), "New player must track a full first life");
            require(recap.value(LifeMetric.XP_LEVEL) == 37, "XP maximum was not captured before native death clearing");
            require(recap.value(LifeMetric.HUNGER_RESTORED) == 1, "Food must count actual positive clamped hunger, not nutrition or setters");
            require(recap.value(LifeMetric.MOB_KILLS) == 2 && recap.value(LifeMetric.BLOCKS_MINED) == 3, "Native committed stat increments lost");
            require(Math.abs(recap.value(LifeMetric.HP_LOST) - 12) < .001, "Lethal accepted HP must be clipped and counted exactly once, excluding absorption and setter resets; observed=" + recap.value(LifeMetric.HP_LOST) + "; damage=" + DamageLedger.snapshot(f.player));
            LifeRecords.finalDeath(f.player);
            require(f.record().latest() == recap, "Finalization is not life-ID idempotent");
            CompoundTag saved = LifeRecordsData.get(f.player.server).save(new CompoundTag());
            LifeRecordsData reopened = LifeRecordsData.load(saved);
            require(reopened.find(f.player.getUUID()).latest().equals(recap), "Restart changed frozen recap");
            RevivalManager.logout(f.player); RevivalManager.login(f.player);
            require(f.record().latest().equals(recap), "Reconnect changed frozen recap");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void partialBootstrapCloneAndModeEligibility(GameTestHelper helper) {
        Fixture f = new Fixture(helper, true);
        f.run(() -> {
            LifeRecords.add(f.player, LifeMetric.MOB_KILLS, 100);
            f.player.hurt(f.player.damageSources().fellOutOfWorld(), 100);
            LifeRecap first = f.record().latest(); require(first.partial() && first.cards().isEmpty(), "Existing untracked first life must be summary only");
            UUID firstId = first.lifeId();
            ServerPlayer next = f.cloneFixture();
            next.setGameMode(GameType.SURVIVAL);
            RevivalManager.clonePlayer(f.player, next, true);
            require(!f.record().lifeId().equals(firstId), "Native death clone did not begin next life");
            UUID secondId = f.record().lifeId();
            ServerPlayer dimensionClone = f.cloneFixture();
            RevivalManager.clonePlayer(next, dimensionClone, false);
            require(f.record().lifeId().equals(secondId), "Nondeath clone changed life ID");
            dimensionClone.setGameMode(GameType.SPECTATOR); LifeRecords.tick(dimensionClone);
            dimensionClone.setGameMode(GameType.SURVIVAL); LifeRecords.add(dimensionClone, LifeMetric.MOB_KILLS, 10);
            dimensionClone.setGameMode(GameType.CREATIVE); dimensionClone.setGameMode(GameType.SURVIVAL);
            dimensionClone.hurt(dimensionClone.damageSources().fellOutOfWorld(), 100);
            LifeRecap second = f.record().latest();
            require(!second.partial() && !second.eligible() && second.cards().isEmpty(), "Transient Creative must invalidate full-life awards");
            require(second.previousBests().get(LifeMetric.MOB_KILLS.ordinal()) == 0, "Partial life illegally set completed best");
            RevivalManager.logout(next); RevivalManager.logout(dimensionClone);
        });
    }
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void canceledDeathThenArenaContainedDeathRetainsLife(GameTestHelper helper) {
        Fixture f = new Fixture(helper, false);
        f.run(() -> {
            UUID id = f.record().lifeId();
            Object cancellation = new Object() {
                @SubscribeEvent(priority = EventPriority.LOWEST)
                public void cancel(LivingDeathEvent event) {
                    if (event.getEntity() == f.player) { event.setCanceled(true); RevivalManager.setHealthInternal(f.player, 20); }
                }
            };
            MinecraftForge.EVENT_BUS.register(cancellation);
            try {
                f.player.setExperienceLevels(20);
                f.player.hurt(f.player.damageSources().fellOutOfWorld(), 100);
                require(!RevivalManager.isFinalized(f.player) && f.record().latest() == null, "Canceled death finalized a life");
            } finally { MinecraftForge.EVENT_BUS.unregister(cancellation); }
            LifeRecords.registerArenaEligibility(player -> player.getUUID().equals(f.player.getUUID()));
            try {
                LifeRecords.add(f.player, LifeMetric.MOB_KILLS, 100);
                f.player.invulnerableTime = 0; f.player.hurt(f.player.damageSources().fellOutOfWorld(), 100);
                require(f.record().latest() == null && f.record().lifeId().equals(id), "Contained Arena death finalized the world life");
                CompoundTag saved = LifeRecordsData.get(f.player.server).save(new CompoundTag());
                require(LifeRecordsData.load(saved).find(f.player.getUUID()).lifeId().equals(id), "Arena life marker was not durable");
                ServerPlayer respawn = f.cloneFixture();
                RevivalManager.clonePlayer(f.player, respawn, true);
                require(f.record().lifeId().equals(id), "Arena native death clone changed life ID");
                RevivalManager.logout(respawn);
            } finally { LifeRecords.registerArenaEligibility(player -> false); }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void committedPlacementFailedCanceledAndMultiblockOnce(GameTestHelper helper) {
        Fixture f = new Fixture(helper, false);
        f.run(() -> {
            BlockPos floor = helper.absolutePos(new BlockPos(1,1,1));
            helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, Items.STONE.getDefaultInstance());
            var context = new net.minecraft.world.item.context.UseOnContext(f.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor), net.minecraft.core.Direction.UP, floor, false));
            Object cancellation = new Object() {
                @SubscribeEvent public void cancel(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
                    if (event.getEntity() == f.player) event.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.register(cancellation);
            try {
                require(net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(context) == net.minecraft.world.InteractionResult.FAIL,
                    "Canceled placement fixture did not return rollback failure");
                require(helper.getLevel().getBlockState(floor.above()).isAir(), "Canceled placement did not rollback native block");
            } finally { MinecraftForge.EVENT_BUS.unregister(cancellation); }
            require(net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(context).consumesAction(), "Native successful placement failed");
            helper.getLevel().setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, Items.OAK_DOOR.getDefaultInstance());
            var doorContext = new net.minecraft.world.item.context.UseOnContext(f.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor), net.minecraft.core.Direction.UP, floor, false));
            f.player.setPos(floor.getX()+3.5, floor.getY()+1, floor.getZ()+.5);
            require(net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(doorContext).consumesAction(), "Native multiblock placement failed");
            require(helper.getLevel().getBlockState(floor.above(2)).is(Blocks.OAK_DOOR), "Multiblock fixture did not place upper door");
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, Items.STONE.getDefaultInstance());
            // Nonreplaceable block at build limit is a failed native place, not a transaction.
            var impossible = new net.minecraft.world.item.context.UseOnContext(f.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0,helper.getLevel().getMaxBuildHeight(),0),
                    net.minecraft.core.Direction.UP, new BlockPos(0,helper.getLevel().getMaxBuildHeight(),0), false));
            net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(impossible);
            f.player.hurt(f.player.damageSources().fellOutOfWorld(), 100);
            require(f.record().latest().value(LifeMetric.BLOCKS_PLACED) == 2, "Placement must count committed single/multiblock transactions once, not canceled/failed calls");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void sameUuidFakePlayerNativeStatOutcomesCannotCreditOrInvalidateOwnerLife(GameTestHelper helper) {
        Fixture f = new Fixture(helper,false);
        f.run(() -> {
            var fake = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),f.player.getGameProfile());
            fake.setGameMode(GameType.SURVIVAL);
            // Forge's base FakePlayer.awardStat is intentionally inert. Automation integrations may
            // still call the native counter directly with their owner-profile FakePlayer as actor.
            fake.getStats().increment(fake,Stats.BLOCK_MINED.get(Blocks.STONE),5);
            fake.getStats().increment(fake,Stats.CUSTOM.get(Stats.MOB_KILLS),7);
            require(f.record().value(LifeMetric.BLOCKS_MINED) == 0 && f.record().value(LifeMetric.MOB_KILLS) == 0,
                "Owner-profile native FakePlayer automation contaminated the real player life");
            fake.setGameMode(GameType.CREATIVE); fake.setExperienceLevels(50);
            LifeRecord original = f.record(); UUID originalLife = original.lifeId(); LifeRecap originalRecap = original.latest();
            LifeRecords.login(fake); LifeRecords.tick(fake); LifeRecords.beginDeath(fake); LifeRecords.finalDeath(fake);
            LifeRecords.clonePlayer(fake,f.player,true); LifeRecords.clonePlayer(f.player,fake,true);
            LifeRecords.clonePlayer(fake,f.player,false); LifeRecords.clonePlayer(f.player,fake,false);
            LifeRecords.sendRecap(fake); LifeRecords.canceledDeath(fake); LifeRecords.logout(fake);
            require(f.record() == original && original.lifeId().equals(originalLife) && original.latest() == originalRecap,
                "Owner-profile FakePlayer lifecycle created/reset/finalized the original real-player ledger");
            f.player.awardStat(Stats.BLOCK_MINED.get(Blocks.STONE),2);
            var otherProfile = new GameProfile(UUID.randomUUID(),"automation");
            var otherFake = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),otherProfile);
            otherFake.setGameMode(GameType.CREATIVE); LifeRecords.observeLevel(otherFake);
            LifeRecords.login(otherFake); LifeRecords.beginDeath(otherFake); LifeRecords.finalDeath(otherFake);
            LifeRecords.tick(otherFake); LifeRecords.sendRecap(otherFake);
            LifeRecords.clonePlayer(otherFake,f.player,true); LifeRecords.clonePlayer(f.player,otherFake,true);
            require(LifeRecordsData.get(f.player.server).find(otherProfile.getId()) == null,"Fake actor created a player life record before eligibility exclusion");
            f.player.hurt(f.player.damageSources().fellOutOfWorld(),100);
            LifeRecap recap = f.record().latest();
            require(recap.eligible() && recap.value(LifeMetric.BLOCKS_MINED) == 2 && recap.value(LifeMetric.MOB_KILLS) == 0
                && recap.value(LifeMetric.XP_LEVEL) == 0,"Fake actor mode/XP/native stats invalidated or credited the real owner life");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fiveNativeEntryLossesReachExactHundredHpAndFinalSentinelAddsZero(GameTestHelper helper) {
        Fixture f = new Fixture(helper, false);
        f.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);
        f.player.getFoodData().setFoodLevel(19); // Keep native regeneration from healing above the exact20HP fixture.
        // Long-lived, no-gravity fixture stays in the same loaded column but outside naturally spawned
        // mobs' attack range; only the explicit native hurt/heal calls may contribute to exact milestones.
        f.player.setPos(f.player.getX(),f.player.getY()+160,f.player.getZ());
        RevivalManager.setHealthInternal(f.player,20);
        for (int index = 0; index < 5; index++) {
            int entry = index;
            helper.runAtTickTime(65+40*index, () -> {
                try {
                    if (entry > 0) {
                        f.player.heal(20);
                        require(!RevivalManager.state(f.player).atDoor() && Math.abs(f.player.getHealth()-20) < .001,
                            "Native postentry healing must restore real20HP after the lock; HP=" + f.player.getHealth() + "; door=" + RevivalManager.state(f.player).atDoor());
                    }
                    f.player.invulnerableTime = 0;
                    require(f.player.hurt(f.player.damageSources().generic(),25), "Native full20HP entry was rejected");
                    require(RevivalManager.state(f.player).atDoor() && f.player.isAlive(), "Native entry did not survive at semanticzero");
                    require(f.record().value(LifeMetric.HP_LOST) == 20*(entry+1), "Door sentinel shifted exact HP milestone: " + f.record().value(LifeMetric.HP_LOST));
                    if (entry == 4) {
                        f.player.invulnerableTime = 0;
                        f.player.hurt(f.player.damageSources().fellOutOfWorld(),1000);
                        LifeRecap recap = f.record().latest();
                        require(recap.value(LifeMetric.HP_LOST) == 100 && recap.value(LifeMetric.DOOR_ENTRIES) == 5,
                            "Already-Door final sentinel added phantom HP loss or duplicate entry");
                        require(recap.cards().stream().anyMatch(c -> c.metric() == LifeMetric.HP_LOST && c.badges().contains(LifeRecap.Badge.MILESTONE)),
                            "Fivefull20HP entry losses failed to attain100HP milestone");
                        f.cleanup(); helper.succeed();
                    }
                } catch (Throwable failure) { f.cleanup(); helper.fail(failure.toString()); }
            });
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class Fixture {
        final GameTestHelper helper; final ServerPlayer player; final EmbeddedChannel channel;
        final List<EmbeddedChannel> cloneChannels = new ArrayList<>();
        final List<ServerPlayer> clones = new ArrayList<>();
        Fixture(GameTestHelper helper, boolean partial) {
            this.helper = helper;
            UUID uuid = UUID.randomUUID();
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(uuid, "life-" + uuid.toString().substring(0,8)));
            // Existing native playerdata but no records ledger: exercise the actual first-login bootstrap boundary.
            if (partial) try {
                java.nio.file.Path file = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(uuid+".dat");
                java.nio.file.Files.createDirectories(file.getParent());
                net.minecraft.nbt.NbtIo.writeCompressed(player.saveWithoutId(new CompoundTag()),file.toFile());
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            Connection wire = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(wire);
            player.server.getPlayerList().placeNewPlayer(wire, player); player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
            BlockPos pos = helper.absolutePos(new BlockPos(1,3,1)); player.setPos(pos.getX()+.5, pos.getY(), pos.getZ()+.5);
        }
        ServerPlayer cloneFixture() {
            ServerPlayer clone = new ServerPlayer(player.server, helper.getLevel(), player.getGameProfile());
            Connection wire = new Connection(PacketFlow.SERVERBOUND); cloneChannels.add(new EmbeddedChannel(wire));
            clone.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(player.server, wire, clone);
            clones.add(clone); return clone;
        }
        LifeRecord record() { return LifeRecordsData.get(player.server).find(player.getUUID()); }
        void run(Runnable checks) {
            helper.runAtTickTime(65, () -> {
                try { checks.run(); cleanup(); helper.succeed(); }
                catch (Throwable failure) { failure.printStackTrace(); cleanup(); helper.fail(failure.toString()); }
            });
        }
        void cleanup() {
            clones.forEach(clone -> { RevivalManager.logout(clone); clone.discard(); });
            cloneChannels.forEach(EmbeddedChannel::finishAndReleaseAll);
            player.server.getPlayerList().remove(player); player.discard(); channel.finishAndReleaseAll();
        }
    }
}
