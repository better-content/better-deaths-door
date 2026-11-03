package com.bettercontent.betterdeathsdoor.heartreview;

import com.bettercontent.betterdeathsdoor.client.ClientRevivalState;
import com.bettercontent.betterdeathsdoor.state.MaimType;
import com.bettercontent.betterdeathsdoor.state.Region;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Nonshipping native-renderer diagnostic, NOT native menu or human-pointer acceptance.
 * Angles are explicit public render-helper arguments; no pointer, clicks, scrolling or resizing.
 */
@Mod.EventBusSubscriber(modid = HeartReviewSupport.MOD_ID, value = Dist.CLIENT)
public final class MaimSurfaceReview {
    private static int ticks, step = -1, wait;
    private static final String[] TYPES = {"OPENED", "CRACKED", "BURNT", "LOW_OXYGEN"};
    private MaimSurfaceReview() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!"1".equals(System.getenv("MAIM_REVIEW_MATRIX")) || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks < 100) return;
        if (step == -1) { advance(mc); return; }
        if (++wait < 40) return;
        int count = 0;
        for (Region region : Region.values()) for (MaimType type : MaimType.values())
            count += ClientRevivalState.count(mc.player.getUUID(), region, type);
        if (count != 6) throw new IllegalStateException("Expected six synchronized marks, got " + count);
        String label = step < 8 ? "surface-" + TYPES[step / 2].toLowerCase()
                + (step % 2 == 0 ? "-clothing" : "-base") : switch (step) {
                    case 8 -> "native-inventory-base";
                    case 9 -> "native-inventory-clothing";
                    case 10 -> "native-inventory-armor";
                    case 11 -> "native-world-front";
                    default -> "native-world-back";
                };
        Screenshot.grab(mc.gameDirectory, label + ".png", mc.getMainRenderTarget(), message ->
                System.out.println("MAIM_SURFACE_CAPTURE " + label + " " + message.getString()));
        if (step == 12) { System.out.println("MAIM_SURFACE_REVIEW_COMPLETE"); mc.stop(); }
        else advance(mc);
    }

    private static void advance(Minecraft mc) {
        step++; wait = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) mc.options.toggleModelPart(part,
                step < 8 ? step % 2 == 0 : step != 8);
        String name = mc.player.getGameProfile().getName();
        String type = step < 8 ? TYPES[step / 2] : "OPENED";
        int stage = step;
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            var source = server.createCommandSourceStack();
            server.getCommands().performPrefixedCommand(source, "betterdeathsdoor debug scenario " + name + " healthy");
            for (Region region : Region.values()) server.getCommands().performPrefixedCommand(source,
                    "betterdeathsdoor debug maim " + name + " " + region.name() + " " + type);
            for (String slot : new String[] {"head", "chest", "legs", "feet"}) {
                String item = stage != 10 ? "air" : "diamond_" + switch (slot) {
                    case "head" -> "helmet";
                    case "chest" -> "chestplate";
                    case "legs" -> "leggings";
                    default -> "boots";
                };
                server.getCommands().performPrefixedCommand(source,
                        "item replace entity " + name + " armor." + slot + " with minecraft:" + item);
            }
            if (stage >= 8 && stage <= 10) server.getCommands().performPrefixedCommand(source,
                    "betterdeathsdoor debug gui " + name + " inventory");
        });
        if (step < 8) mc.setScreen(new MatrixScreen(type + (step % 2 == 0 ? " clothing" : " base")));
        else if (step > 10) {
            mc.setScreen(null);
            mc.options.setCameraType(step == 11 ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        }
    }

    private static final class MatrixScreen extends Screen {
        private final String label;
        MatrixScreen(String label) { super(Component.literal("Maim surface diagnostic")); this.label = label; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            g.fill(0, 0, width, height, 0xff303030);
            g.drawString(font, "Native avatar diagnostic: " + label + " (not menu acceptance)", 8, 8, 0xffffffff);
            for (int row = 0; row < 3; row++) for (int col = 0; col < 5; col++) {
                float yaw = (col - 2) * .75f, pitch = (row - 1) * .8f;
                int x = width * (col * 2 + 1) / 10, y = (height - 24) * (row + 1) / 3 + 12;
                InventoryScreen.renderEntityInInventoryFollowsAngle(g, x, y - 10, 50, yaw, pitch, minecraft.player);
                g.drawString(font, yaw + "/" + pitch, x - 28, y - 6, 0xffeeeeee);
            }
        }
    }
}
