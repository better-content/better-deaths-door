package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.DamageLedger;
import com.bettercontent.betterdeathsdoor.network.*;
import com.bettercontent.betterdeathsdoor.state.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.sounds.SoundEvents;
import java.util.*;

public final class ClientRevivalState {
    private static final Map<UUID, BodyView> STATES = new HashMap<>();
    private static final Map<UUID, int[]> VISUALS = new HashMap<>();
    private static BodyView recap;
    private static DamageLedger.Summary recapDamage = DamageLedger.Summary.empty();
    private static boolean recapWasDead;
    private static int captureDelay = -1;
    private static String captureLabel;
    private ClientRevivalState() {}
    public static Optional<BodyView> get(UUID id) { return Optional.ofNullable(STATES.get(id)); }
    public static int count(UUID id, Region region, MaimType type) {
        int[] counts = VISUALS.get(id);
        return counts == null ? 0 : counts[region.ordinal() * MaimType.values().length + type.ordinal()];
    }
    public static BodyView recap() { return recap; }
    public static DamageLedger.Summary recapDamage() { return recapDamage; }
    public static void acceptMaims(MaimSyncPacket packet) {
        if (Arrays.stream(packet.counts()).allMatch(count -> count == 0)) VISUALS.remove(packet.playerId());
        else VISUALS.put(packet.playerId(), packet.counts().clone());
    }
    public static void accept(StateSyncPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        BodyView body = packet.view();
        if (packet.mode() == 2) {
            recap = body; recapDamage = packet.damage(); recapWasDead = false; return;
        }
        BodyView previous = STATES.put(body.playerId(), body);
        int[] counts = new int[Region.values().length * MaimType.values().length];
        for (Region region : Region.values())
            for (MaimType type : MaimType.values())
                counts[region.ordinal() * MaimType.values().length + type.ordinal()] = body.region(region).count(type);
        acceptMaims(new MaimSyncPacket(body.playerId(), counts));
        if (previous != null && body.regions().stream().mapToInt(BodyView.RegionView::total).sum()
                > previous.regions().stream().mapToInt(BodyView.RegionView::total).sum()
                && InjuryClientConfig.SOUND.get() && mc.player != null)
            mc.player.playSound(SoundEvents.ANVIL_LAND, .12f, .7f);
        if (packet.mode() == 1) {
            if (mc.screen instanceof MendScreen screen && screen.body().playerId().equals(body.playerId()))
                screen.update(body);
            else mc.setScreen(new MendScreen(body));
        }
        if (packet.mode() == 3 && mc.screen instanceof MendScreen screen
                && screen.body().playerId().equals(body.playerId())) screen.update(body);
    }
    public static void control(UiControlPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        switch (packet.operation()) {
            case "own-body" -> {
                if (mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
            }
            case "presentation" -> {
                InjuryClientConfig.REDUCED_MOTION.set(packet.progress() > 0);
                InjuryClientConfig.SOUND.set(Boolean.parseBoolean(packet.text()));
            }
            case "inventory" -> {
                if (mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
            }
            case "capture" -> {
                captureLabel = packet.text().replaceAll("[^a-zA-Z0-9_-]", "_");
                captureDelay = Math.max(2, Math.min(200, packet.delay()));
            }
            case "treatment" -> MendUi.treatment(packet.progress(), packet.text());
            case "failed-proc" -> {
                if (InjuryClientConfig.SOUND.get() && mc.player != null)
                    mc.player.playSound(SoundEvents.ANVIL_LAND, .3f, .55f);
            }
            case "close" -> {
                if (!(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)) mc.setScreen(null);
                if (mc.player != null && !packet.text().isBlank())
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(packet.text()), true);
            }
            case "death-recap" -> {
                if (mc.player != null && !mc.player.isAlive()
                        && !(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen))
                    mc.setScreen(new net.minecraft.client.gui.screens.DeathScreen(
                            null, mc.level.getLevelData().isHardcore()));
            }
            default -> {}
        }
    }
    public static void tick(Minecraft mc) {
        if (mc.player == null) { STATES.clear(); VISUALS.clear(); return; }
        if (recap != null && !mc.player.isAlive()) recapWasDead = true;
        if (recapWasDead && mc.player.isAlive()) {
            recap = null; recapDamage = DamageLedger.Summary.empty(); recapWasDead = false;
        }
        if (captureDelay >= 0 && --captureDelay == 0) {
            Screenshot.grab(mc.gameDirectory, captureLabel + ".png", mc.getMainRenderTarget(),
                    message -> System.out.println("INJURY_GUI_CAPTURE " + message.getString()));
            captureDelay = -1;
        }
    }
    public static void clear() {
        STATES.clear(); VISUALS.clear(); recap = null;
        recapDamage = DamageLedger.Summary.empty(); recapWasDead = false;
        captureDelay = -1; captureLabel = null; MendUi.clear(); RevivalHud.reset();
    }
}
