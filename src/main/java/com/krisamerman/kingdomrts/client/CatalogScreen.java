package com.krisamerman.kingdomrts.client;

import java.util.List;

import com.krisamerman.kingdomrts.nation.CatalogEntry;
import com.krisamerman.kingdomrts.nation.NationPayloads;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// Client only. The nation management interface's Build catalog: one button per building, a page at
// a time. Picking one asks the server for its blueprint and closes the screen, and the building's
// ghost follows the player's view until they cancel it (BuildPreviewClient).
public class CatalogScreen extends Screen {
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int LABEL_COLOR = 0xFF707070;
    private static final int WIDTH = 176;
    private static final int HEIGHT = 160;
    private static final int LEFT = 8;
    private static final int RIGHT = WIDTH - 8;
    private static final int TOP = 20;
    private static final int ENTRY_HEIGHT = 20;
    private static final int ENTRY_GAP = 2;
    private static final int SMALL_BUTTON_HEIGHT = 16;
    private static final int BOTTOM_BAR_Y = HEIGHT - 8 - SMALL_BUTTON_HEIGHT;
    private static final int PER_PAGE = (BOTTOM_BAR_Y - 4 - TOP + ENTRY_GAP) / (ENTRY_HEIGHT + ENTRY_GAP);

    private final List<CatalogEntry> entries;
    private int page;
    private int leftPos;
    private int topPos;

    public CatalogScreen(List<CatalogEntry> entries) {
        super(Component.literal("Build"));
        this.entries = entries;
    }

    private int pages() {
        return Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void init() {
        leftPos = (width - WIDTH) / 2;
        topPos = (height - HEIGHT) / 2;
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(entries.size(), from + PER_PAGE); i++) {
            CatalogEntry entry = entries.get(i);
            addRenderableWidget(Button.builder(Component.literal(entry.name()), button -> pick(entry))
                    .bounds(leftPos + LEFT, topPos + TOP + (i - from) * (ENTRY_HEIGHT + ENTRY_GAP), RIGHT - LEFT - 52, ENTRY_HEIGHT).build());
        }
        // Back to the first screen: asks the server again, so it's up to date.
        addRenderableWidget(Button.builder(Component.literal("< Back"), button -> PacketDistributor.sendToServer(new NationPayloads.OpenNation()))
                .bounds(leftPos + LEFT, topPos + BOTTOM_BAR_Y, 50, SMALL_BUTTON_HEIGHT).build());
        if (pages() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> turnPage(-1))
                    .bounds(leftPos + RIGHT - 42, topPos + BOTTOM_BAR_Y, 20, SMALL_BUTTON_HEIGHT).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), button -> turnPage(1))
                    .bounds(leftPos + RIGHT - 20, topPos + BOTTOM_BAR_Y, 20, SMALL_BUTTON_HEIGHT).build()).active = page < pages() - 1;
        }
    }

    private void turnPage(int by) {
        page = Math.max(0, Math.min(pages() - 1, page + by));
        rebuildWidgets();
    }

    private void pick(CatalogEntry entry) {
        PacketDistributor.sendToServer(new NationPayloads.RequestBlueprint(entry.id()));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiDraw.panel(graphics, leftPos, topPos, WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftPos + LEFT, topPos + 6, TEXT_COLOR, false);
        if (entries.isEmpty()) {
            graphics.drawString(font, "Nothing to build yet", leftPos + LEFT, topPos + TOP + 4, LABEL_COLOR, false);
        }
        // Each building's size (width x depth x height), beside its button.
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(entries.size(), from + PER_PAGE); i++) {
            CatalogEntry entry = entries.get(i);
            String size = entry.size().getX() + "x" + entry.size().getZ() + "x" + entry.size().getY();
            int y = topPos + TOP + (i - from) * (ENTRY_HEIGHT + ENTRY_GAP) + (ENTRY_HEIGHT - 8) / 2;
            graphics.drawString(font, size, leftPos + RIGHT - font.width(size), y, LABEL_COLOR, false);
        }
        if (pages() > 1) {
            String pageText = (page + 1) + "/" + pages();
            int pageX = leftPos + LEFT + 50 + (RIGHT - 42 - LEFT - 50 - font.width(pageText)) / 2;
            graphics.drawString(font, pageText, pageX, topPos + BOTTOM_BAR_Y + (SMALL_BUTTON_HEIGHT - 8) / 2 + 1, LABEL_COLOR, false);
        }
    }
}
