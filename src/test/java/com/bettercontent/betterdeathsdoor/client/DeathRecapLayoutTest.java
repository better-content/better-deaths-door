package com.bettercontent.betterdeathsdoor.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DeathRecapLayoutTest {
    @Test void narrowAndWideLayoutsReserveStagesWithoutTimingDependentGeometry() {
        for (int[] size : new int[][]{{320,240},{640,360},{960,540}}) for (int cards = 0; cards <= 3; cards++) {
            var layout = DeathRecapLayout.fit(size[0], size[1], 2, cards, 40, false);
            assertTrue(layout.x() >= 0);
            assertTrue(layout.x() + layout.width() <= size[0]);
            assertTrue(layout.contentBottom() < layout.detailsY());
            assertTrue(layout.detailsY() + 18 < layout.actionY());
            assertTrue(layout.actionY() + 20 <= size[1]);
            assertEquals(layout.summaryHeight() + Math.max(1,cards) * (layout.cardHeight()+8), layout.hintTop());
            assertEquals(0, layout.clampScroll(-1));
            assertEquals(layout.maxScroll(), layout.clampScroll(Integer.MAX_VALUE));
        }
    }
    @Test void detailsRemainScrollableAndHintsUseSameViewport() {
        var layout = DeathRecapLayout.fit(320,240,3,3,70,true);
        assertTrue(layout.maxScroll() > 0);
        assertFalse(layout.hintIntersectsViewport(0,70));
        assertTrue(layout.hintIntersectsViewport(layout.maxScroll(),70));
        assertFalse(layout.contentContains(10,230));
        assertTrue(layout.contentContains(layout.x()+10,layout.contentTop()+10));
        assertFalse(layout.hintIntersectsViewport(0,0));
    }
    @Test void summaryNeedsLessSpaceThanFullDamageBodyAndMetricDetails() {
        assertTrue(DeathRecapLayout.fit(640,360,1,3,40,true).contentHeight()
                > DeathRecapLayout.fit(640,360,1,3,40,false).contentHeight());
    }
    @Test void rendererNeverMutatesNativeButtonEnableTimingOrServerRecordState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bettercontent/betterdeathsdoor/client/DeathRecapOverlay.java"));
        assertTrue(source.contains("detailsButton.active = detailsButton.visible"));
        assertFalse(source.contains("button.active = true"));
        assertFalse(source.contains("sendToServer"));
        assertFalse(source.contains("setHealth("));
        assertTrue(source.contains("if (frozen == null) frozen = ClientLifeRecords.latest()"));
        assertTrue(source.contains("SESSION.summary(reducedMotion())"));
        assertTrue(source.contains("SESSION.card(i, reducedMotion())"));
        assertTrue(source.contains("l.contentContains(event.getMouseX(), event.getMouseY())"));
        assertTrue(source.contains("g.enableScissor(l.x(), l.contentTop()"));
        assertFalse(source.contains("g.disableScissor(); g.pose().pushPose()"));
        assertTrue(source.contains("finally { if (clipped) g.disableScissor()"));
        assertTrue(source.contains("g.disableScissor(); clipped = false"));
        assertTrue(source.contains("boolean hover = button.active"));
    }
    @Test void authoredTenMetricSpriteHasExpectedPngDimensions() throws Exception {
        byte[] png=Files.readAllBytes(Path.of("src/main/resources/assets/better_deaths_door/textures/gui/life_record_icons.png"));
        var buffer=java.nio.ByteBuffer.wrap(png);
        assertEquals(160,buffer.getInt(16)); assertEquals(16,buffer.getInt(20));
    }
}
