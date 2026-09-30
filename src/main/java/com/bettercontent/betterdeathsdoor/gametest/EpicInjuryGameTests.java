package com.bettercontent.betterdeathsdoor.gametest;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import com.bettercontent.betterdeathsdoor.RevivalMod;
import com.bettercontent.betterdeathsdoor.compat.EpicInjuryCompat;
import com.bettercontent.betterdeathsdoor.state.MaimType;
import com.bettercontent.betterdeathsdoor.state.Region;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.collider.MultiOBBCollider;
import yesman.epicfight.api.collider.OBBCollider;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Generated only when the actual optional dependency is loaded. Default verification never silently skips a named test. */
@GameTestHolder(RevivalMod.MOD_ID)
public final class EpicInjuryGameTests {
    private EpicInjuryGameTests() {}
    @GameTestGenerator
    public static Collection<TestFunction> epicTests() {
        if (!ModList.get().isLoaded("epicfight")) return List.of();
        return List.of(test("epic_single_collider_contracts_actual_selection", Loaded::singleCollider),
            test("epic_multi_collider_contracts_each_sample_once", Loaded::multiCollider),
            test("epic_full_arm_impairment_blocks_phase_and_both_hands", Loaded::fullImpairment),
            test("epic_damage_source_enters_door_once", Loaded::damageSource));
    }
    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction("epic_injury", name, RevivalMod.MOD_ID + ":empty", 150, 0, true, body);
    }

    /** Keep optional classes behind the conditional test generator's class-loading boundary. */
    private static final class Loaded {
        static void singleCollider(GameTestHelper helper) {
            Fixture f = new Fixture(helper);
            f.run(() -> {
                RecordingCollider collider = new RecordingCollider(new ArrayList<>());
                AttackAnimation animation = Animations.FIST_AUTO1.get();
                var root = f.patch.getArmature().rootJoint;
                collider.updateAndSelectCollideEntity(f.patch, animation, 0, .1f, root, 1);
                OpenMatrix4f baseline = collider.last();
                OpenMatrix4f modelBefore = new OpenMatrix4f(f.patch.getModelMatrix(1));
                Vec3 center = collider.center();
                Pig pig = f.pigAt(new Vec3(-center.x, center.y - .45, -center.z));
                require(collider.updateAndSelectCollideEntity(f.patch, animation, 0, .1f, root, 1).contains(pig), "Healthy actual OBB failed to select the target at its center");
                var authored = collider.serialize(new net.minecraft.nbt.CompoundTag());
                impair(f.player, 5);
                var selected = collider.updateAndSelectCollideEntity(f.patch, animation, 0, .1f, root, 1);
                close(collider.last().toScaleVector().x / baseline.toScaleVector().x, .5, "Collider redirect failed to contract by exactly one impairment factor");
                require(!selected.contains(pig), "Injured actual OBB retained the distant baseline target");
                require(collider.serialize(new net.minecraft.nbt.CompoundTag()).equals(authored), "Reach contraction mutated shared authored collider geometry");
                close(f.patch.getModelMatrix(1).toScaleVector().x, modelBefore.toScaleVector().x, "Compat mutated the actor model matrix");
                close(EpicInjuryCompat.factor(f.patch), .5, "Server patch did not use authoritative injury state");
                RevivalManager.state(f.player).clear(); RevivalManager.refresh(f.player);
                require(collider.updateAndSelectCollideEntity(f.patch, animation, 0, .1f, root, 1).contains(pig), "Curing did not restore baseline collider selection");
                close(collider.last().toScaleVector().x, baseline.toScaleVector().x, "Uninjured collider geometry changed after recovery");
            });
        }
        static void multiCollider(GameTestHelper helper) {
            Fixture f = new Fixture(helper);
            f.run(() -> {
                List<OpenMatrix4f> transforms = new ArrayList<>();
                MultiOBBCollider multi = new MultiOBBCollider(new RecordingCollider(transforms), new RecordingCollider(transforms), new RecordingCollider(transforms));
                var animation = Animations.FIST_AUTO1.get(); var root = f.patch.getArmature().rootJoint;
                multi.updateAndSelectCollideEntity(f.patch, animation, 0, .1f, root, 1);
                require(transforms.size() >= 3, "Actual MultiCollider did not evaluate its sweep samples");
                List<OpenMatrix4f> baseline = List.copyOf(transforms); transforms.clear();
                impair(f.player, 5);
                multi.updateAndSelectCollideEntity(f.patch, animation, 0, .1f, root, 1);
                require(transforms.size() == baseline.size(), "Impairment changed authored sample count");
                for (int i = 0; i < transforms.size(); i++) close(transforms.get(i).toScaleVector().x / baseline.get(i).toScaleVector().x, .5, "MultiCollider sample was not contracted exactly once");
            });
        }
        static void fullImpairment(GameTestHelper helper) {
            Fixture f = new Fixture(helper);
            f.run(() -> {
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                f.player.setItemInHand(InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                float main = f.patch.getReach(InteractionHand.MAIN_HAND), off = f.patch.getReach(InteractionHand.OFF_HAND);
                require(main > 0 && off > 0, "Healthy patch must have usable reach in both hands");
                impair(f.player, 5);
                close(f.patch.getReach(InteractionHand.MAIN_HAND), main * .5, "Epic mainhand reach was not scaled once");
                close(f.patch.getReach(InteractionHand.OFF_HAND), off * .5, "Epic offhand reach was not scaled once");
                impair(f.player, 10);
                close(f.patch.getReach(InteractionHand.MAIN_HAND), 0, "Full impairment left mainhand reach");
                close(f.patch.getReach(InteractionHand.OFF_HAND), 0, "Full impairment left offhand reach");
                List<OpenMatrix4f> transforms = new ArrayList<>();
                var phase = new AttackAnimation.Phase(InteractionHand.MAIN_HAND, f.patch.getArmature().rootJoint, new RecordingCollider(transforms));
                require(phase.getCollidingEntities(f.patch, Animations.FIST_AUTO1.get(), 0, .1f, 1).isEmpty(), "Full impairment returned a degenerate point hit");
                require(transforms.isEmpty(), "Full impairment failed to short-circuit the actual Epic attack phase");
            });
        }
        static void damageSource(GameTestHelper helper) {
            Fixture target = new Fixture(helper);
            target.run(() -> {
                var zombie = EntityType.ZOMBIE.create(helper.getLevel());
                require(zombie != null, "Failed to create Epic Fight attacker");
                zombie.setNoAi(true); zombie.setNoGravity(true);
                zombie.setPos(target.player.position().add(2, 0, 0));
                helper.getLevel().addFreshEntity(zombie); target.spawned.add(zombie);
                zombie.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                var source = yesman.epicfight.world.damagesource.EpicFightDamageSources.mobAttack(zombie)
                    .setAnimation(Animations.FIST_AUTO1).setUsedItem(net.minecraft.world.item.ItemStack.EMPTY);
                require(RevivalManager.type(source) == MaimType.CRACKED, "Epic captured empty offhand was misclassified using current mainhand sword");
                source.setUsedItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                zombie.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                require(RevivalManager.type(source) == MaimType.OPENED, "Epic captured weapon was lost after the attacker changed held item");
                require(target.player.hurt(source, 100), "Actual Epic Fight mob damage source was rejected");
                var state = RevivalManager.state(target.player);
                require(target.player.isAlive() && state.atDoor(), "Epic Fight lethal damage did not enter Death's Door");
                require(state.activeMaims().size() == 1 && state.traumaCount(RevivalManager.now(target.player)) == 1, "Epic Fight damage generated duplicate injury or trauma");
                require(state.activeMaims().get(0).type() == MaimType.OPENED, "Epic weapon context was lost while creating the actual injury");
            });
        }
        private static void impair(ServerPlayer p, int arms) {
            var state = RevivalManager.state(p); state.clear();
            for (int i = 0; i < arms; i++) state.addMaim(i % 2 == 0 ? Region.LEFT_ARM : Region.RIGHT_ARM, MaimType.CRACKED, RevivalManager.now(p));
            for (int i = 0; i < 5; i++) state.addTrauma(RevivalManager.now(p));
            RevivalManager.refresh(p);
        }
    }
    private static final class RecordingCollider extends OBBCollider {
        final List<OpenMatrix4f> transforms;
        RecordingCollider(List<OpenMatrix4f> transforms) { super(.35, .35, .35, 0, .9, -3); this.transforms = transforms; }
        @Override public void transform(OpenMatrix4f matrix) { transforms.add(new OpenMatrix4f(matrix)); super.transform(matrix); }
        @Override public OBBCollider deepCopy() { return new RecordingCollider(transforms); }
        OpenMatrix4f last() { return transforms.get(transforms.size() - 1); }
        Vec3 center() { return worldCenter; }
    }
    private static final class Fixture {
        final GameTestHelper helper; final ServerPlayer player; final ServerPlayerPatch patch;
        final io.netty.channel.embedded.EmbeddedChannel channel;
        final List<Entity> spawned = new ArrayList<>();
        boolean closed;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "epic-" + UUID.randomUUID().toString().substring(0, 8)));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.getFoodData().setFoodLevel(19);
            var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            patch = EpicFightCapabilities.getServerPlayerPatch(player);
            require(patch != null, "Real Epic Fight player capability did not attach");
        }
        Pig pigAt(Vec3 pos) {
            Pig pig = EntityType.PIG.create(helper.getLevel());
            require(pig != null, "Failed to create collision target");
            pig.setNoAi(true); pig.setNoGravity(true); pig.setPos(pos); helper.getLevel().addFreshEntity(pig); spawned.add(pig); return pig;
        }
        void run(Runnable assertions) {
            helper.runAtTickTime(65, () -> {
                try { assertions.run(); close(); helper.succeed(); }
                catch (Throwable failure) { failure.printStackTrace(); close(); helper.fail(failure.toString()); }
            });
        }
        void close() { if (closed) return; closed = true; spawned.forEach(Entity::discard); player.server.getPlayerList().remove(player); player.discard(); channel.finishAndReleaseAll(); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void close(double actual, double expected, String message) { require(Math.abs(actual - expected) < .0001, message + ": expected " + expected + ", got " + actual); }
}
