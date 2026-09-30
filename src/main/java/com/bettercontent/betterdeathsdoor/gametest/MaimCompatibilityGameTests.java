package com.bettercontent.betterdeathsdoor.gametest;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import com.bettercontent.betterdeathsdoor.RevivalMod;
import com.bettercontent.betterdeathsdoor.api.event.InjuryEvent;
import com.bettercontent.betterdeathsdoor.state.MaimType;
import com.bettercontent.betterdeathsdoor.state.Region;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(RevivalMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaimCompatibilityGameTests {
    @GameTest(template = "empty", timeoutTicks = 130)
    public static void enchantedMeleeAndSweepScaleOnce(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.runAtTickTime(65, () -> f.run(() -> {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.enchant(Enchantments.SHARPNESS, 5);
            sword.enchant(Enchantments.SWEEPING_EDGE, 3);
            f.player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            // Choose an explicit ten-point base so the additive enchantment contribution is observable.
            f.player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
            f.player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);
            // Keep both targets within the halved attack reach, independently of the damage assertion.
            Cow primary = f.cow(1, 0), swept = f.cow(1, .6);
            f.player.attack(primary);
            double healthyPrimary = 100 - primary.getHealth(), healthySweep = 100 - swept.getHealth();
            close(healthyPrimary, 13, "Unmaimed enchanted damage changed");
            close(healthySweep, 10.75, "Unmaimed sweeping damage changed");

            for (int i = 0; i < 5; i++) {
                RevivalManager.state(f.player).addMaim(Region.LEFT_ARM, MaimType.CRACKED, RevivalManager.now(f.player));
                RevivalManager.state(f.player).addTrauma(RevivalManager.now(f.player));
            }
            RevivalManager.refresh(f.player);
            primary.setHealth(100); swept.setHealth(100);
            primary.invulnerableTime = 0; swept.invulnerableTime = 0;
            f.player.attack(primary);
            close(100 - primary.getHealth(), healthyPrimary / 2, "Enchantment damage did not scale exactly once");
            close(100 - swept.getHealth(), healthySweep / 2, "Sweeping's additive floor did not scale exactly once");
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void canceledRolledDeathGuaranteesOneMaim(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.runAtTickTime(65, () -> f.run(() -> {
            for (int i = 0; i < 10; i++)
                RevivalManager.state(f.player).addMaim(Region.HEAD, MaimType.CRACKED, RevivalManager.now(f.player));
            RevivalManager.state(f.player).enterDoor(RevivalManager.now(f.player));
            RevivalManager.setHealthInternal(f.player, RevivalManager.SENTINEL);
            int before = RevivalManager.state(f.player).activeMaims().size();
            f.probe.cancel = true;
            f.player.hurt(f.player.damageSources().generic(), 2);
            require(f.player.isAlive() && !RevivalManager.isFinalized(f.player), "Canceled death did not preserve life");
            require(!RevivalManager.isTerminating(f.player), "Canceled roll retained terminating state");
            require(RevivalManager.state(f.player).activeMaims().size() == before + 1, "Canceled rolled death must add exactly one maim");
            require(f.probe.maims == 1 && f.probe.finalDeaths == 0, "Canceled roll published incorrect lifecycle events");
            RevivalManager.canceledDeath(f.player);
            require(RevivalManager.state(f.player).activeMaims().size() == before + 1, "Replayed cancellation duplicated its maim");
            f.probe.cancel = false;
            f.player.invulnerableTime = 0;
            f.player.hurt(f.player.damageSources().generic(), 2);
            require(RevivalManager.isFinalized(f.player) && f.probe.finalDeaths == 1, "Next uncanceled roll failed to complete final death once");
            RevivalManager.canceledDeath(f.player);
            require(RevivalManager.state(f.player).activeMaims().isEmpty(), "Confirmed death retained a pending fallback injury");
        }));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < .0001, message + ": " + actual + " expected " + expected);
    }
    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final Probe probe;
        final List<Cow> cows = new ArrayList<>();
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "maim-" + UUID.randomUUID().toString().substring(0, 8)));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            BlockPos position = helper.absolutePos(new BlockPos(1, 3, 1));
            player.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
            player.getFoodData().setFoodLevel(19);
            probe = new Probe(player);
            MinecraftForge.EVENT_BUS.register(probe);
        }
        Cow cow(double dx, double dz) {
            Cow cow = EntityType.COW.create(helper.getLevel());
            cow.setNoAi(true); cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            cow.setHealth(100);
            cow.setPos(player.getX() + dx, player.getY(), player.getZ() + dz);
            helper.getLevel().addFreshEntity(cow);
            cows.add(cow);
            return cow;
        }
        void run(Runnable checks) {
            try {
                player.setOnGround(true); player.setSprinting(false); player.fallDistance = 0;
                player.setDeltaMovement(Vec3.ZERO);
                checks.run(); helper.succeed();
            } catch (Throwable failure) { failure.printStackTrace(); helper.fail(failure.toString()); }
            finally {
                MinecraftForge.EVENT_BUS.unregister(probe);
                cows.forEach(Cow::discard);
                player.server.getPlayerList().remove(player); player.discard(); channel.finishAndReleaseAll();
            }
        }
    }
    public static final class Probe {
        final ServerPlayer player;
        boolean cancel;
        int maims, finalDeaths;
        Probe(ServerPlayer player) { this.player = player; }
        @SubscribeEvent(priority = EventPriority.NORMAL)
        public void cancel(LivingDeathEvent event) { if (event.getEntity() == player && cancel) event.setCanceled(true); }
        @SubscribeEvent
        public void maim(InjuryEvent.MaimAdded event) { if (event.getEntity() == player) maims++; }
        @SubscribeEvent
        public void death(InjuryEvent.FinalDeath event) { if (event.getEntity() == player) finalDeaths++; }
    }
}
