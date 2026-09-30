package com.bettercontent.betterdeathsdoor.network;

import com.bettercontent.betterdeathsdoor.*;
import com.bettercontent.betterdeathsdoor.state.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;

public final class RevivalNetwork {
    private static final String PROTOCOL = "7";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RevivalMod.MOD_ID, "main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static final Map<UUID, UUID> VIEWERS = new HashMap<>();
    private RevivalNetwork() {}
    public static void clear() { VIEWERS.clear(); }
    public static void register() {
        CHANNEL.registerMessage(0, StateSyncPacket.class, StateSyncPacket::encode,
                StateSyncPacket::decode, StateSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, BodyActionPacket.class, BodyActionPacket::encode,
                BodyActionPacket::decode, BodyActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, UiControlPacket.class, UiControlPacket::encode,
                UiControlPacket::decode, UiControlPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, MaimSyncPacket.class, MaimSyncPacket::encode,
                MaimSyncPacket::decode, MaimSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static boolean canInspect(ServerPlayer viewer, ServerPlayer subject) {
        return RevivalManager.validTreatmentTarget(viewer, subject);
    }
    public static boolean isViewing(ServerPlayer viewer, ServerPlayer subject) {
        return subject != null && isViewing(viewer, subject.getUUID());
    }
    public static boolean isViewing(ServerPlayer viewer, UUID subject) {
        return subject.equals(VIEWERS.get(viewer.getUUID()));
    }
    public static BodyView view(ServerPlayer viewer, ServerPlayer subject, BodySnapshot snapshot) {
        List<BodyView.RegionView> rows = new ArrayList<>();
        for (Region region : Region.values()) {
            Map<MaimType, Integer> active = new EnumMap<>(MaimType.class);
            Map<MaimType, Integer> treated = new EnumMap<>(MaimType.class);
            for (MaimType type : MaimType.values()) {
                active.put(type, (int) snapshot.activeMaims().stream()
                        .filter(maim -> maim.region() == region && maim.type() == type).count());
                treated.put(type, (int) snapshot.treatmentHistory().stream()
                        .filter(record -> record.region() == region && record.type() == type).count());
            }
            rows.add(new BodyView.RegionView(region, active, snapshot.regionalReduction(region), treated));
        }
        return new BodyView(subject.getUUID(), subject.getGameProfile().getName(),
                snapshot.health(), snapshot.maxHealth(), snapshot.atDoor(),
                (int) snapshot.healingLockTicks(), snapshot.traumaCount(),
                snapshot.tuning().traumaLifetimeTicks(), snapshot.deathProbability(),
                snapshot.functionalMultiplier(), RevivalManager.snapshot(viewer).treatmentSeconds(),
                rows, RevivalManager.isTreating(viewer, subject));
    }
    public static void sendBody(ServerPlayer viewer, ServerPlayer subject, BodySnapshot snapshot) {
        if (!canInspect(viewer, subject)) return;
        VIEWERS.put(viewer.getUUID(), subject.getUUID());
        send(viewer, new StateSyncPacket(view(viewer, subject, snapshot), viewer == subject ? 0 : 1));
    }
    public static void closeBody(ServerPlayer viewer) { VIEWERS.remove(viewer.getUUID()); }
    public static void refreshViewing(ServerPlayer viewer, ServerPlayer subject) {
        if (isViewing(viewer, subject))
            send(viewer, new StateSyncPacket(view(viewer, subject, RevivalManager.snapshot(subject)),
                    viewer == subject ? 0 : 3));
    }
    public static void sync(ServerPlayer player, BodySnapshot snapshot) {
        send(player, new StateSyncPacket(view(player, player, snapshot), 0));
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), MaimSyncPacket.from(snapshot));
        for (var entry : new ArrayList<>(VIEWERS.entrySet())) {
            ServerPlayer viewer = player.server.getPlayerList().getPlayer(entry.getKey());
            if (viewer == null) { VIEWERS.remove(entry.getKey()); continue; }
            if (!entry.getValue().equals(player.getUUID()) || viewer == player) continue;
            if (canInspect(viewer, player))
                send(viewer, new StateSyncPacket(view(viewer, player, snapshot), 3));
            else {
                closeBody(viewer);
                send(viewer, new UiControlPacket("close", "Too far away to treat", 0, 0));
            }
        }
    }
    public static void startTracking(ServerPlayer viewer, ServerPlayer subject) {
        send(viewer, MaimSyncPacket.from(RevivalManager.snapshot(subject)));
    }
    public static void stopTracking(ServerPlayer viewer, ServerPlayer subject) {
        send(viewer, MaimSyncPacket.empty(subject.getUUID()));
    }
    public static void sendRecap(ServerPlayer player, BodySnapshot snapshot, DamageLedger.Summary damage) {
        send(player, new StateSyncPacket(view(player, player, snapshot), 2, damage));
    }
    public static void treatmentStatus(ServerPlayer viewer, ServerPlayer subject, float progress, String message) {
        send(viewer, new UiControlPacket("treatment", message, progress, 0));
    }
    public static void send(ServerPlayer player, Object packet) {
        if (player.connection != null)
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
