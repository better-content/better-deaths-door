package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Small teammate treatment view using the same controls as the inventory panel. */
public final class MendScreen extends Screen {
    private BodyView body;
    private Button mend;
    private int left, top, panelWidth, panelHeight, scroll;
    public MendScreen(BodyView body) {
        super(Component.literal("Mend " + body.name()));
        this.body = body;
    }
    public BodyView body() { return body; }
    public void update(BodyView next) { body = next; updateButton(); }
    @Override protected void init() {
        panelWidth = Math.min(420, width - 20); panelHeight = Math.min(250, height - 20);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        int listX = left + 155;
        mend = addRenderableWidget(Button.builder(Component.literal("Mend"),
                button -> MendUi.toggle(body)).bounds(listX, top + 22, panelWidth - 170, 20).build());
        updateButton();
    }
    private void updateButton() {
        if (mend == null) return;
        mend.setMessage(Component.literal(body.treatmentActive() ? "Cancel" : "Mend"));
        mend.active = body.treatmentActive() || body.regions().stream().anyMatch(region -> region.total() > 0);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        int listX = left + 155, listY = top + 64, listWidth = panelWidth - 170;
        int listHeight = panelHeight - 107;
        if (x >= listX && x <= listX + listWidth && y >= listY && y <= listY + listHeight) {
            scroll = Math.max(0, Math.min(MendUi.maxScroll(body, listHeight), scroll - (int) (amount * 24)));
            return true;
        }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        MendUi.refreshTheme();
        renderBackground(graphics);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, MendUi.PAPER);
        graphics.fill(left, top, left + 2, top + panelHeight, body.atDoor() ? MendUi.RED : MendUi.GREEN);
        graphics.drawString(font, body.name(), left + 12, top + 13, MendUi.INK, false);
        graphics.drawString(font, body.atDoor() ? "Death's Door · 0 HP" : "Active maims",
                left + 12, top + 29, body.atDoor() ? MendUi.RED : MendUi.MUTED, false);
        Player target = minecraft.level == null ? null : minecraft.level.players().stream()
                .filter(player -> player.getUUID().equals(body.playerId())).findFirst().orElse(null);
        if (target != null)
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    left + 76, top + Math.min(panelHeight - 35, 192), 55,
                    left + 76 - mouseX, top + 105 - mouseY, target);
        int listX = left + 155, listY = top + 64, listWidth = panelWidth - 170;
        int listHeight = panelHeight - 107;
        graphics.drawString(font, "MAIMS", listX, listY - 13, MendUi.MUTED, false);
        if (MendUi.maxScroll(body, listHeight) > 0)
            graphics.drawString(font, "Scroll ↕", listX + listWidth - font.width("Scroll ↕"),
                    listY - 13, MendUi.MUTED, false);
        scroll = Math.min(scroll, MendUi.maxScroll(body, listHeight));
        MendUi.renderList(graphics, font, body, listX, listY, listWidth, listHeight, scroll);
        MendUi.progress(graphics, font, body, listX, top + panelHeight - 29, listWidth);
        super.render(graphics, mouseX, mouseY, partial);
    }
    @Override public void tick() { MendUi.tick(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() {
        if (minecraft.getConnection() != null)
            RevivalNetwork.CHANNEL.sendToServer(new BodyActionPacket(body.playerId(), BodyActionPacket.CLOSE));
        MendUi.clear();
        super.removed();
    }
}
