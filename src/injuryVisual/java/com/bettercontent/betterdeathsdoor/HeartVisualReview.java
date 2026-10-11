package com.bettercontent.betterdeathsdoor;

import com.bettercontent.betterdeathsdoor.client.ClientRevivalState;
import com.bettercontent.betterdeathsdoor.client.InjuryClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Focused native heart review; never packaged, never changes player input. */
@Mod.EventBusSubscriber(modid = RevivalMod.MOD_ID, value = Dist.CLIENT)
public final class HeartVisualReview {
    private record Step(String name, int scale, Consumer<ServerPlayer> action, int ticks,
                        boolean door, int expectedHealth) {}
    private static final String PROFILE = System.getProperty("injury.review.hearts");
    private static final List<Step> STEPS = new ArrayList<>();
    private static boolean starting, ready;
    private static int index = -1, ticks, observations, doorFrames, captures;
    private static long started;
    private static CompletableFuture<Void> command;
    private static String capture, worldName;
    private static int lifecycle, lifecycleObservations;
    private static boolean capturing;
    private static final Set<String> SEEN = new HashSet<>();
    private HeartVisualReview() {}

    public static void observe(String renderer, int current, int remembered, long blink) {
        if (PROFILE == null || !ready || index < 0) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getCameraEntity() != mc.player) return;
        String expectedRenderer = PROFILE.equals("absent") || PROFILE.equals("DISABLE") ? "Forge" : "Mantle";
        require(renderer.equals(expectedRenderer), "Wrong active renderer: " + renderer);
        var view = ClientRevivalState.get(mc.player.getUUID()).orElse(null);
        if (view == null) return;
        if (view.atDoor()) {
            require(current == 0 && remembered == 0 && blink == 0,
                    "Door fill/history/blink: " + current + "/" + remembered + "/" + blink);
            require(mc.player.getHealth() > 0 && mc.player.isAlive(), "Door must keep entity alive");
        }
        if (index >= STEPS.size()) {
            boolean matches = switch (lifecycle) {
                case 2 -> !view.atDoor() && mc.player.isAlive() && current > 1;
                case 3, 6 -> view.atDoor();
                case 7 -> !view.atDoor() && current == 1;
                default -> false;
            };
            if (!matches) return;
            lifecycleObservations++;
            if (SEEN.add("lifecycle:" + lifecycle)) {
                System.out.println("HEART_REVIEW_LIFECYCLE profile=" + PROFILE + " phase=" + lifecycle
                        + " renderer=" + renderer + " atDoor=" + view.atDoor() + " current=" + current
                        + " remembered=" + remembered + " engineHealth=" + mc.player.getHealth());
                capture = "lifecycle-" + lifecycle;
            }
            return;
        }
        Step step = STEPS.get(index);
        if (SEEN.add("raw:" + step.name())) System.out.println("HEART_REVIEW_RAW " + step.name()
                + " current=" + current + " state=" + view.atDoor() + " commandDone=" + (command != null && command.isDone()));
        if (command == null || !command.isDone() || view.atDoor() != step.door()) return;
        if (!step.door() && current != step.expectedHealth()) return;
        observations++;
        if (view.atDoor()) {
            doorFrames++;
            if (doorFrames == 1) capture = step.name() + "-first-sync";
            else if (doorFrames == 3) capture = step.name() + "-hurt";
        }
        if (SEEN.add(step.name())) {
            System.out.println("HEART_REVIEW_OBSERVATION profile=" + PROFILE + " step=" + step.name()
                    + " renderer=" + renderer + " camera=" + mc.player.getUUID() + " atDoor=" + view.atDoor()
                    + " engineHealth=" + mc.player.getHealth() + " current=" + current + " remembered=" + remembered
                    + " blink=" + blink + " absorption=" + mc.player.getAbsorptionAmount()
                    + " maximum=" + mc.player.getMaxHealth() + " lock=" + view.healingLockTicks());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void render(RenderGuiEvent.Post event) {
        if (PROFILE == null || capture == null || capturing) return;
        String name = capture; capture = null; capturing = true;
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {
            System.out.println("HEART_REVIEW_CAPTURE " + PROFILE + " " + name + " " + message.getString());
            mc.execute(() -> { captures++; capturing = false; });
        });
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (PROFILE == null || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (!starting && (mc.screen instanceof TitleScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)
                && mc.getOverlay() == null) {
            starting = true; started = System.currentTimeMillis();
            require(ModList.get().isLoaded("mantle") != PROFILE.equals("absent"), "Mantle presence mismatch");
            if (!PROFILE.equals("absent")) {
                try {
                    Class<?> config = Class.forName("slimeknights.mantle.config.Config");
                    Object spec = config.getField("HEART_RENDERER").get(null);
                    require(spec.getClass().getMethod("get").invoke(spec).toString().equals(PROFILE),
                            "Mantle config mismatch");
                } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            }
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(30);
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
            worldName = "heart-review-" + System.currentTimeMillis();
            mc.createWorldOpenFlows().createFreshLevel(worldName,
                    new LevelSettings("Heart review", GameType.SURVIVAL, false, Difficulty.NORMAL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(245, false, false),
                    registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            return;
        }
        if (!starting) return;
        require(System.currentTimeMillis() - started < 1_200_000, "Review exceeded twenty-minute budget");
        if (lifecycle == 5 && mc.player == null && mc.getSingleplayerServer() == null) {
            lifecycle = 6; ticks = 0;
            mc.createWorldOpenFlows().loadLevel(new TitleScreen(), worldName);
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (!ready) {
            // Respect the native server's initial spawn damage immunity before intended hits.
            if (++ticks < 100) return;
            InjuryClientConfig.SOUND.set(false);
            // Keep the drawer visible for baseline inspection; no physical sneak or input synthesis.
            if (ModList.get().isLoaded("better_survival_hud"))
                com.bettercontent.bettersurvivalhud.config.DynamicSurvivalHudConfig.DYNAMIC_HUD_ENABLED.set(false);
            System.out.println("HEART_REVIEW_PROFILE " + PROFILE + " hudFading=false");
            prepare(); ready = true;
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                for (String rule : List.of("naturalRegeneration", "doMobSpawning", "doDaylightCycle"))
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule " + rule + " false");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
            });
            next(); return;
        }
        if (index >= STEPS.size()) { lifecycleTick(mc); return; }
        if (command == null || !command.isDone()) return;
        command.join();
        if (capturing || capture != null) return;
        if (++ticks < STEPS.get(index).ticks()) return;
        require(observations > 0, "No matching actual renderer observations: " + STEPS.get(index).name());
        if (STEPS.get(index).door()) {
            var view = ClientRevivalState.get(mc.player.getUUID()).orElseThrow();
            require(view.atDoor() && view.healingLockTicks() == 0, "Door not observed beyond healing deadline");
            require(mc.player.getHealth() == RevivalManager.SENTINEL, "Settled client sentinel changed");
        }
        // Capture the settled state on the next complete native frame before moving on.
        if (!SEEN.contains("captured:" + STEPS.get(index).name())) {
            SEEN.add("captured:" + STEPS.get(index).name());
            System.out.println("HEART_REVIEW_SETTLED profile=" + PROFILE + " step=" + STEPS.get(index).name()
                    + " observations=" + observations + " engineHealth=" + mc.player.getHealth()
                    + " absorption=" + mc.player.getAbsorptionAmount());
            require(mc.player.getAbsorptionAmount() == (STEPS.get(index).name().contains("abstrue") ? 4 : 0),
                    "Absorption changed during rendering review");
            capture = STEPS.get(index).name() + "-settled"; return;
        }
        next();
    }

    private static void next() {
        var mc = Minecraft.getInstance();
        if (++index == STEPS.size()) {
            lifecycle = 1; ticks = lifecycleObservations = 0;
            command = mc.getSingleplayerServer().submit(() -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                p.removeAllEffects(); p.setInvulnerable(false);
                require(p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE), "Final death rejected");
            });
            return;
        }
        Step step = STEPS.get(index);
        ticks = observations = doorFrames = 0;
        mc.options.guiScale().set(step.scale()); mc.resizeDisplay();
        command = mc.getSingleplayerServer().submit(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            step.action().accept(player);
            require(player.isAlive(), "Unexpected fixture death " + step.name());
            require(RevivalManager.state(player).atDoor() == step.door(), "Server Door mismatch " + step.name());
            if (step.door()) require(player.getHealth() == RevivalManager.SENTINEL, "Server sentinel changed");
        });
    }

    private static void lifecycleTick(Minecraft mc) {
        if (command != null) {
            if (!command.isDone()) return;
            command.join();
        }
        if (capturing || capture != null) return;
        var view = ClientRevivalState.get(mc.player.getUUID()).orElse(null);
        if (lifecycle == 1 && !mc.player.isAlive()) {
            System.out.println("HEART_REVIEW_FINAL_DEATH " + PROFILE);
            // Production respawn packet, not a simulated button/key event.
            mc.player.respawn(); lifecycle = 2; ticks = lifecycleObservations = 0;
        } else if (lifecycle == 2 && mc.player.isAlive() && view != null && !view.atDoor()) {
            if (++ticks < 100) return;
            require(lifecycleObservations > 0 && mc.player.getHealth() > 1, "Respawn retained stale zero presentation");
            lifecycle = 3; ticks = lifecycleObservations = 0;
            command = mc.getSingleplayerServer().submit(() -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                p.removeAllEffects(); p.setInvulnerable(false); p.invulnerableTime = 0;
                RevivalManager.debugScenario(p, "healthy");
                require(p.hurt(p.damageSources().fall(), p.getMaxHealth() * 2), "Reconnect Door damage rejected");
                require(RevivalManager.state(p).atDoor() && p.getHealth() == RevivalManager.SENTINEL,
                        "Reconnect fixture did not enter real Door");
                p.setInvulnerable(true);
            });
        } else if (lifecycle == 3 && view != null && view.atDoor()) {
            if (++ticks < 75) return;
            require(lifecycleObservations > 0, "No pre-disconnect Door drawing");
            lifecycle = 5; ticks = lifecycleObservations = 0; command = null;
            mc.level.disconnect(); mc.clearLevel(new TitleScreen());
        } else if (lifecycle == 6 && view != null && view.atDoor()) {
            if (++ticks < 75) return;
            require(lifecycleObservations > 0, "No restored Door drawing after rejoin");
            lifecycle = 7; ticks = lifecycleObservations = 0;
            command = mc.getSingleplayerServer().submit(() -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                require(RevivalManager.state(p).atDoor() && p.getHealth() == RevivalManager.SENTINEL,
                        "Reload lost sentinel/authoritative Door state");
                p.heal(1);
            });
        } else if (lifecycle == 7 && view != null && !view.atDoor()) {
            if (++ticks < 30) return;
            require(lifecycleObservations > 0 && mc.player.getHealth() == 1, "Rejoin healing did not restore half-heart");
            System.out.println("HEART_REVIEW_COMPLETE profile=" + PROFILE + " steps=" + STEPS.size()
                    + " captures=" + captures + " finalDeathRespawn=true saveRejoin=true");
            mc.stop();
        }
    }

    private static void prepare() {
        for (int max : new int[]{20, 60}) for (int scale : new int[]{2, 3, 4}) for (boolean absorption : new boolean[]{false, true}) {
            String label = "hp" + max + "-scale" + scale + "-abs" + absorption;
            // Across the matrix each scale is also reviewed in normal, poison or wither colors.
            var effect = scale == 2 ? null : scale == 3 ? MobEffects.POISON : MobEffects.WITHER;
            Consumer<ServerPlayer> healthy = p -> {
                p.setInvulnerable(false); p.removeAllEffects();
                RevivalManager.debugScenario(p, "healthy");
                p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
                RevivalManager.setHealthInternal(p, max);
                if (absorption) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 12000, 0, false, false));
                if (effect != null) p.addEffect(new MobEffectInstance(effect, 12000, 0, false, false));
                p.setInvulnerable(true);
            };
            STEPS.add(new Step(label + "-healthy", scale, healthy, 25, false, max));
            STEPS.add(new Step(label + "-half", scale, p -> {
                p.removeAllEffects(); p.setInvulnerable(false); p.invulnerableTime = 0;
                require(p.hurt(p.damageSources().fall(), max - 1), "Half-heart damage rejected");
                if (absorption) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 12000, 0, false, false));
                if (effect != null) p.addEffect(new MobEffectInstance(effect, 12000, 0, false, false));
                p.setInvulnerable(true);
            }, 25, false, 1));
            Consumer<ServerPlayer> enter = p -> {
                p.removeAllEffects(); p.setInvulnerable(false); p.invulnerableTime = 0;
                require(p.hurt(p.damageSources().fall(), max * 2), "Lethal damage rejected");
                if (absorption) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 12000, 0, false, false));
                if (effect != null) p.addEffect(new MobEffectInstance(effect, 12000, 0, false, false));
                p.setInvulnerable(true);
            };
            Consumer<ServerPlayer> heal = p -> {
                p.removeAllEffects(); p.heal(1);
                require(!RevivalManager.state(p).atDoor() && p.getHealth() == 1, "Production heal failed");
                if (absorption) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 12000, 0, false, false));
                if (effect != null) p.addEffect(new MobEffectInstance(effect, 12000, 0, false, false));
            };
            STEPS.add(new Step(label + "-door", scale, enter, 75, true, 0));
            STEPS.add(new Step(label + "-heal", scale, heal, 25, false, 1));
            STEPS.add(new Step(label + "-repeat-door", scale, enter, 75, true, 0));
            STEPS.add(new Step(label + "-repeat-heal", scale, heal, 25, false, 1));
        }
    }

    private static void require(boolean pass, String message) {
        if (!pass) throw new IllegalStateException("HEART_REVIEW_FAILED " + PROFILE + ": " + message);
    }
}
