package com.krisamerman.kingdomrts.settlement;

import com.krisamerman.kingdomrts.client.DisplayNames;
import com.krisamerman.kingdomrts.client.GuiDraw;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

// Client only. Draws the hut panel in the vanilla style: the workplace's details as label/value
// rows, then who works here (each with a Fire button), then the settlement's unemployed settlers
// (each with a Hire button, a page at a time), the automatic-hiring toggle, and a button for the
// stock.
public class WorkplaceScreen extends AbstractContainerScreen<WorkplaceMenu> {
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int LABEL_COLOR = 0xFF707070;
    private static final int WIDTH = 176;
    private static final int LEFT = 8;
    private static final int RIGHT = WIDTH - 8;
    private static final int ROW_HEIGHT = 10;
    private static final int PERSON_HEIGHT = 18;
    private static final int BUTTON_WIDTH = 36;
    private static final int BUTTON_HEIGHT = 16;

    // Layout, worked out once from what the panel lists (relative to the panel's top-left).
    private final int workersTitleY;
    private final int workersY;
    private final int candidatesTitleY;
    private final int candidatesY;
    private final int pagingY;
    private final int autoHireY;
    private final int stockY;

    public WorkplaceScreen(WorkplaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        WorkplaceMenu.WorkplaceInfo info = menu.getInfo();
        imageWidth = WIDTH;
        workersTitleY = 20 + 3 * ROW_HEIGHT + 6;
        workersY = workersTitleY + 12;
        candidatesTitleY = workersY + Math.max(1, info.workers().size()) * PERSON_HEIGHT + 6;
        candidatesY = candidatesTitleY + 12;
        pagingY = candidatesY + Math.max(1, info.candidates().size()) * PERSON_HEIGHT;
        autoHireY = pagingY + (info.pages() > 1 ? PERSON_HEIGHT + 2 : 2);
        stockY = autoHireY + 22;
        imageHeight = stockY + 20 + 8;
    }

    @Override
    protected void init() {
        super.init(); // centers the panel
        WorkplaceMenu.WorkplaceInfo info = menu.getInfo();
        for (int i = 0; i < info.workers().size(); i++) {
            addPersonButton("Fire", workersY + i * PERSON_HEIGHT, WorkplaceMenu.FIRE_BUTTON + i, true);
        }
        for (int i = 0; i < info.candidates().size(); i++) {
            addPersonButton("Hire", candidatesY + i * PERSON_HEIGHT, WorkplaceMenu.HIRE_BUTTON + i, !info.isFull());
        }
        if (info.pages() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> click(WorkplaceMenu.PREVIOUS_PAGE_BUTTON))
                    .bounds(leftPos + RIGHT - 2 * 20 - 2, topPos + pagingY, 20, BUTTON_HEIGHT).build()).active = info.page() > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), button -> click(WorkplaceMenu.NEXT_PAGE_BUTTON))
                    .bounds(leftPos + RIGHT - 20, topPos + pagingY, 20, BUTTON_HEIGHT).build()).active = info.page() < info.pages() - 1;
        }
        addRenderableWidget(Button.builder(Component.literal("Automatic hiring: " + (info.autoHire() ? "On" : "Off")),
                        button -> click(WorkplaceMenu.AUTO_HIRE_BUTTON))
                .bounds(leftPos + LEFT, topPos + autoHireY, WIDTH - 2 * LEFT, 20)
                .tooltip(Tooltip.create(Component.literal("When on, unemployed settlers take this hut's openings by themselves. "
                        + "Firing someone here turns it off.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Stock"), button -> click(WorkplaceMenu.STOCK_BUTTON))
                .bounds(leftPos + LEFT, topPos + stockY, WIDTH - 2 * LEFT, 20).build());
    }

    private void addPersonButton(String label, int y, int id, boolean active) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> click(id))
                .bounds(leftPos + RIGHT - BUTTON_WIDTH, topPos + y - 4, BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = active;
    }

    // Tells the server a button was pressed (it runs WorkplaceMenu#clickMenuButton).
    private void click(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiDraw.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        WorkplaceMenu.WorkplaceInfo info = menu.getInfo();
        graphics.drawString(font, title, LEFT, 6, TEXT_COLOR, false);
        row(graphics, 20, "Settlement", info.settlementName());
        row(graphics, 20 + ROW_HEIGHT, "Job", info.occupation().isEmpty() ? "None" : DisplayNames.occupation(info.occupation()));
        row(graphics, 20 + 2 * ROW_HEIGHT, "Workers", info.workers().size() + "/" + info.capacity());

        graphics.drawString(font, "Working here", LEFT, workersTitleY, TEXT_COLOR, false);
        if (info.workers().isEmpty()) {
            graphics.drawString(font, "Nobody yet", LEFT, workersY, LABEL_COLOR, false);
        }
        for (int i = 0; i < info.workers().size(); i++) {
            person(graphics, workersY + i * PERSON_HEIGHT, info.workers().get(i).name());
        }

        graphics.drawString(font, "Looking for work", LEFT, candidatesTitleY, TEXT_COLOR, false);
        if (info.candidates().isEmpty()) {
            graphics.drawString(font, "No unemployed settlers nearby", LEFT, candidatesY, LABEL_COLOR, false);
        }
        for (int i = 0; i < info.candidates().size(); i++) {
            person(graphics, candidatesY + i * PERSON_HEIGHT, info.candidates().get(i).name());
        }
        if (info.pages() > 1) {
            graphics.drawString(font, "Page " + (info.page() + 1) + "/" + info.pages(), LEFT, pagingY + 4, LABEL_COLOR, false);
        }
    }

    private void row(GuiGraphics graphics, int y, String label, String value) {
        graphics.drawString(font, label, LEFT, y, LABEL_COLOR, false);
        graphics.drawString(font, value, RIGHT - font.width(value), y, TEXT_COLOR, false);
    }

    // A name, cut short with "..." if it would run into its button.
    private void person(GuiGraphics graphics, int y, String name) {
        int room = RIGHT - BUTTON_WIDTH - 4 - LEFT;
        String shown = font.width(name) <= room ? name : font.plainSubstrByWidth(name, room - font.width("...")) + "...";
        graphics.drawString(font, shown, LEFT, y, TEXT_COLOR, false);
    }
}
