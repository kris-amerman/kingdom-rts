package com.krisamerman.kingdomrts.client;

import net.minecraft.client.gui.GuiGraphics;

// Client only. Draws the parts of vanilla's container screens (the raised grey panel, item slots,
// the black box behind the player model) with plain fills, so our screens match vanilla's look at
// any size without shipping texture files.
public final class GuiDraw {
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    private GuiDraw() {
    }

    // The raised panel every container screen sits on: a black outline (corner pixels left out, so
    // the corners look rounded), a white highlight along the top and left, and a dark shadow along
    // the bottom and right.
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x + 1, y, right - 1, y + 1, BLACK);
        graphics.fill(x + 1, bottom - 1, right - 1, bottom, BLACK);
        graphics.fill(x, y + 1, x + 1, bottom - 1, BLACK);
        graphics.fill(right - 1, y + 1, right, bottom - 1, BLACK);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, PANEL);
        graphics.fill(x + 1, y + 1, right - 2, y + 3, WHITE);
        graphics.fill(x + 1, y + 1, x + 3, bottom - 2, WHITE);
        graphics.fill(x + 2, bottom - 3, right - 1, bottom - 1, PANEL_SHADOW);
        graphics.fill(right - 3, y + 2, right - 1, bottom - 1, PANEL_SHADOW);
    }

    // An item slot, for an item drawn at (x, y): the 18x18 sunken square around the 16x16 item.
    public static void slot(GuiGraphics graphics, int x, int y) {
        sunken(graphics, x - 1, y - 1, 18, 18, SLOT);
    }

    // A sunken box with the given fill, e.g. the black box behind an entity preview.
    public static void sunken(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        graphics.fill(x, y, x + width - 1, y + 1, SLOT_SHADOW);
        graphics.fill(x, y, x + 1, y + height - 1, SLOT_SHADOW);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, WHITE);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, WHITE);
    }

    public static void entityBox(GuiGraphics graphics, int x, int y, int width, int height) {
        sunken(graphics, x, y, width, height, BLACK);
    }
}
