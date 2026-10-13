package com.bettercontent.betterdeathsdoor.client;

/** Single measured death-layout authority, including the contextual hint and content scroll range. */
public record DeathRecapLayout(int x, int width, int contentTop, int contentBottom,
                              int summaryHeight, int cardsTop, int cardHeight, int hintTop,
                              int contentHeight, int actionY, int detailsY) {
    public static DeathRecapLayout fit(int screenWidth, int screenHeight, int causeLines,
                                      int cards, int hintHeight, boolean details) {
        int width = Math.max(1, Math.min(560, screenWidth - 24));
        int x = Math.max(0, (screenWidth - width) / 2);
        int top = Math.max(66, 44 + Math.max(0, causeLines) * 10 + 12);
        int bottom = Math.max(top, screenHeight - 70);
        int summary = 44;
        int cardHeight = width < 340 ? 68 : 58;
        // Reserve every selected card immediately; delayed drawing cannot shift the layout.
        int count = DeathRevealSchedule.cardStages(cards);
        int bodyRows = width < 340 ? 6 : 3;
        int hint = details ? summary + 90 + 18 + bodyRows * 56 + 20 + 150 + 12
                : summary + count * (cardHeight + 8);
        int contentHeight = hint + Math.max(0, hintHeight) + (hintHeight > 0 ? 8 : 0);
        return new DeathRecapLayout(x, width, top, bottom, summary, summary,
                cardHeight, hint, contentHeight, Math.max(0, screenHeight - 32), Math.max(0, screenHeight - 58));
    }
    public int viewportHeight() { return Math.max(0, contentBottom - contentTop); }
    public int maxScroll() { return Math.max(0, contentHeight - viewportHeight()); }
    public int clampScroll(int requested) { return Math.max(0, Math.min(maxScroll(), requested)); }
    public boolean contentContains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= contentTop && mouseY < contentBottom;
    }
    public boolean hintIntersectsViewport(int scroll, int height) {
        int y = contentTop + hintTop - clampScroll(scroll);
        return height > 0 && y < contentBottom && y + height > contentTop;
    }
}
