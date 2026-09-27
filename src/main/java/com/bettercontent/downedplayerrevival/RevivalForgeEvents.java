package com.bettercontent.downedplayerrevival;

import com.bettercontent.downedplayerrevival.state.Region;
import com.bettercontent.downedplayerrevival.network.RevivalNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class RevivalForgeEvents {
    private RevivalForgeEvents() {}
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !RevivalManager.eligible(player)
                || RevivalManager.isTerminating(player) || RevivalManager.bypasses(event.getSource())) return;
        RevivalManager.interceptedDirectDeath(player, event.getSource());
        if (!RevivalManager.isTerminating(player)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTotem(LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) RevivalManager.tick(event.getServer());
    }
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RevivalManager.login(player);
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RevivalManager.logout(player);
    }
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer old && event.getEntity() instanceof ServerPlayer next)
            RevivalManager.clonePlayer(old, next, event.isWasDeath());
    }
    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { RevivalManager.closeBody(player); RevivalManager.refresh(player); }
    }
    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject)
            RevivalNetwork.startTracking(viewer, subject);
    }
    @SubscribeEvent
    public static void stopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject)
            RevivalNetwork.stopTracking(viewer, subject);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject
                && RevivalManager.validTreatmentTarget(viewer, subject)) {
            RevivalManager.openBody(viewer, subject); event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && RevivalManager.snapshot(player).regionalReduction(Region.LEFT_ARM) >= 1)
            event.setCanceled(true);
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { RevivalManager.shutdown(); }
}
