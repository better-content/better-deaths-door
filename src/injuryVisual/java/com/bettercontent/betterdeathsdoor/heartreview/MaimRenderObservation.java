package com.bettercontent.betterdeathsdoor.heartreview;

import com.bettercontent.betterdeathsdoor.client.ClientRevivalState;
import com.bettercontent.betterdeathsdoor.state.MaimType;
import com.bettercontent.betterdeathsdoor.state.Region;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.client.renderer.patched.entity.PPlayerRenderer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import java.util.HashSet;
import java.util.Set;

/** Bounded READ-ONLY native dispatch observations, only in isolated manual-review support JAR. */
@Mod.EventBusSubscriber(modid = HeartReviewSupport.MOD_ID, value = Dist.CLIENT)
public final class MaimRenderObservation {
    private static final Set<String> SEEN = new HashSet<>();
    private MaimRenderObservation() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void observeLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity() instanceof AbstractClientPlayer player)
            observe(player, event.getRenderer(), event.isCanceled(), event.getPartialTick(), "living-pre");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void observePlayer(RenderPlayerEvent.Pre event) {
        net.minecraft.world.entity.Entity entity = event.getEntity();
        if (entity instanceof AbstractClientPlayer player)
            observe(player, event.getRenderer(), event.isCanceled(), event.getPartialTick(), "player-pre");
    }

    @SuppressWarnings("unchecked")
    private static void observe(AbstractClientPlayer player, LivingEntityRenderer<?, ?> vanillaRenderer,
            boolean canceled, float partialTick, String phase) {
        if (!"1".equals(System.getenv("INJURY_REVIEW_MANUAL")) || SEEN.size() >= 12) return;
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player) return;
        try {
            StringBuilder counts = new StringBuilder();
            int total = 0;
            for (Region region : Region.values()) for (MaimType type : MaimType.values()) {
                int count = ClientRevivalState.count(player.getUUID(), region, type);
                total += count;
                counts.append(region).append(':').append(type).append('=').append(count).append(',');
            }
            // Public renderer identity only; private layer registration remains a pinned
            // native contract test, not an unlisted runtime-reflection boundary.
            String rendererIdentity = vanillaRenderer.getClass().getName();
            String nativeDetails = "Epic absent";
            if (ModList.get().getModContainerById("epicfight")
                    .map(mod -> "20.14.17".equals(mod.getModInfo().getVersion().toString())).orElse(false)) {
                AbstractClientPlayerPatch<AbstractClientPlayer> patch = EpicFightCapabilities.getEntityPatch(
                        player, AbstractClientPlayerPatch.class);
                var renderer = ClientEngine.getInstance().renderEngine.getEntityRenderer(EntityType.PLAYER);
                nativeDetails = "nativeRenderer=" + (renderer == null ? "null" : renderer.getClass().getName())
                        + " patch=" + (patch == null ? "null" : patch.getClass().getName())
                        + " override=" + (patch == null ? "null" : patch.overrideRender());
                if (renderer instanceof PPlayerRenderer playerRenderer && patch != null) {
                    HumanoidMesh mesh = playerRenderer.getMeshProvider(patch).get();
                    StringBuilder parts = new StringBuilder();
                    for (String part : new String[] {"head", "hat", "torso", "jacket", "leftArm", "leftSleeve", "rightArm", "rightSleeve", "leftLeg", "leftPants", "rightLeg", "rightPants"}) {
                        parts.append(part).append(':').append(mesh.hasPart(part) ? mesh.getPart(part).isHidden() : "missing").append(',');
                    }
                    nativeDetails += " parts=" + parts;
                }
            }
            String key = phase + "|" + total + "|" + counts + "|" + (mc.screen == null ? "world" : mc.screen.getClass().getName())
                    + "|" + rendererIdentity + "|" + nativeDetails;
            if (SEEN.add(key)) System.out.println("MAIM_RENDER_OBSERVATION uuid=" + player.getUUID()
                    + " model=" + player.getModelName() + " invisible=" + player.isInvisible()
                    + " spectator=" + player.isSpectator() + " canceled=" + canceled
                    + " partial=" + partialTick + " " + key);
        } catch (RuntimeException error) {
            if (SEEN.add("error:" + error)) System.out.println("MAIM_RENDER_OBSERVATION_FAILED " + error);
        }
    }
}
