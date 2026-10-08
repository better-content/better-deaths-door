package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.RevivalMod;
import com.bettercontent.betterdeathsdoor.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RevivalMod.MOD_ID, value = Dist.CLIENT)
public final class ClientRevivalInput {
    private static InventoryScreen inventoryScreen;
    private static Button mend;
    private static int scroll;
    private ClientRevivalInput() {}
    private record Panel(int x, int y, int width, int height) {}
    private static Panel panel(InventoryScreen screen) {
        int inventoryLeft = (screen.width - 176) / 2;
        int width = Math.min(124, Math.max(40, inventoryLeft - 4));
        return new Panel(inventoryLeft - width - 2, (screen.height - 166) / 2,
                width, 166);
    }
    private static java.util.function.Predicate<Screen> journalPanelHost = screen -> false;
    /** Optional client presentation cooperation; no storage or treatment ownership changes. */
    public static void setJournalPanelHost(java.util.function.Predicate<Screen> host) {
        journalPanelHost = java.util.Objects.requireNonNull(host);
    }
    public static void openOwnBody() {
        var player = Minecraft.getInstance().player;
        if (player != null) RevivalNetwork.CHANNEL.sendToServer(BodyActionPacket.open(player.getUUID()));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        inventoryScreen = null; mend = null; scroll = 0; ClientRevivalState.clear();
    }
    @SubscribeEvent public static void inventory(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (journalPanelHost.test(screen)) return;
        inventoryScreen = screen; scroll = 0; MendUi.clear();
        Panel panel = panel(screen);
        mend = Button.builder(Component.literal("Mend"), button -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            ClientRevivalState.get(player.getUUID()).ifPresent(MendUi::toggle);
        }).bounds(panel.x + 2, panel.y, panel.width - 4, 20).build();
        event.addListener(mend);
        openOwnBody();
    }
    @SubscribeEvent public static void inventoryRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || screen != inventoryScreen) return;
        Panel panel = panel(screen);
        var graphics = event.getGuiGraphics();
        var player = Minecraft.getInstance().player;
        BodyView body = player == null ? null : ClientRevivalState.get(player.getUUID()).orElse(null);
        int listY = panel.y + 39, listHeight = panel.height - 69;
        graphics.fill(panel.x, panel.y + 24, panel.x + panel.width, panel.y + panel.height, MendUi.PAPER);
        graphics.drawString(Minecraft.getInstance().font, "MAIMS", panel.x + 3, panel.y + 27, MendUi.MUTED, false);
        if (body != null) {
            scroll = Math.min(scroll, MendUi.maxScroll(body, listHeight));
            if (MendUi.maxScroll(body, listHeight) > 0)
                graphics.drawString(Minecraft.getInstance().font, "↕",
                        panel.x + panel.width - 10, panel.y + 27, MendUi.MUTED, false);
            MendUi.renderList(graphics, Minecraft.getInstance().font, body,
                    panel.x + 2, listY, panel.width - 4, listHeight, scroll);
            MendUi.progress(graphics, Minecraft.getInstance().font, body,
                    panel.x + 3, panel.y + panel.height - 23, panel.width - 6);
            mend.active = body.treatmentActive() || !MendUi.entries(body).isEmpty();
            mend.setMessage(Component.literal(body.treatmentActive() ? "Cancel" : "Mend"));
        } else {
            graphics.drawString(Minecraft.getInstance().font, "Loading...", panel.x + 3, listY + 8,
                    MendUi.MUTED, false);
            mend.active = false;
        }
    }
    @SubscribeEvent public static void inventoryScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || screen != inventoryScreen) return;
        Panel panel = panel(screen);
        int listY = panel.y + 39, listHeight = panel.height - 69;
        if (event.getMouseX() < panel.x || event.getMouseX() >= panel.x + panel.width
                || event.getMouseY() < listY || event.getMouseY() >= listY + listHeight) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ClientRevivalState.get(player.getUUID()).ifPresent(body ->
                scroll = Math.max(0, Math.min(MendUi.maxScroll(body, listHeight),
                        scroll - (int) (event.getScrollDelta() * 24))));
        event.setCanceled(true);
    }
    @SubscribeEvent public static void inventoryClose(ScreenEvent.Closing event) {
        if (event.getScreen() != inventoryScreen) return;
        var player = Minecraft.getInstance().player;
        if (player != null && Minecraft.getInstance().getConnection() != null)
            RevivalNetwork.CHANNEL.sendToServer(new BodyActionPacket(player.getUUID(), BodyActionPacket.CLOSE));
        inventoryScreen = null; mend = null; scroll = 0; MendUi.clear();
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (event.isUseItem() && mc.screen == null && mc.player != null
                && mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof Player other && mc.player.distanceToSqr(other) <= 9) {
            event.setCanceled(true); event.setSwingHand(false);
            RevivalNetwork.CHANNEL.sendToServer(BodyActionPacket.open(other.getUUID()));
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientRevivalState.tick(Minecraft.getInstance());
            MendUi.tick(); RevivalHud.tick();
        }
    }
}
