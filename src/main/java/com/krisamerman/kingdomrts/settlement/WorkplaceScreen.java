package com.krisamerman.kingdomrts.settlement;

import java.util.List;

import com.krisamerman.kingdomrts.client.DisplayNames;
import com.krisamerman.kingdomrts.client.GuiDraw;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

// Client only. Draws the hut panel in the vanilla style, as two pages of the same fixed size (so it
// fits any screen, however many settlers there are), like MineColonies' hut windows:
//   - Overview: the workplace's details (settlement, job, workers, work area with -/+), then buttons
//     for automatic hiring, the workers page and the stock.
//   - Workers: who works here (each with a Fire button) and who's looking for work (each with a
//     Hire button, hidden when the hut is full), a page of rows at a time, with Back and paging.
// Which page shows comes from the server (WorkplaceInfo.view), so refreshes keep the player where
// they were.
public class WorkplaceScreen extends AbstractContainerScreen<WorkplaceMenu> {
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int LABEL_COLOR = 0xFF707070;
    private static final int DIVIDER_COLOR = 0xFF9A9A9A;
    private static final int WIDTH = 176;
    private static final int HEIGHT = 160;
    private static final int LEFT = 8;
    private static final int RIGHT = WIDTH - 8;
    private static final int TEXT_HEIGHT = 8;
    private static final int TOP = 20;
    private static final int ROW = 12;
    private static final int BUTTON_HEIGHT = 16;
    private static final int LIST_ROW = BUTTON_HEIGHT + 2;
    private static final int PERSON_BUTTON_WIDTH = 36;
    private static final int SMALL_BUTTON = 16;
    private static final int WIDE_BUTTON_HEIGHT = 20;

    // Overview layout.
    private static final int WORK_AREA_Y = TOP + 3 * ROW;
    private static final int OVERVIEW_DIVIDER_Y = WORK_AREA_Y + SMALL_BUTTON + 6;
    private static final int AUTO_HIRE_Y = OVERVIEW_DIVIDER_Y + 6;
    private static final int WORKERS_BUTTON_Y = AUTO_HIRE_Y + WIDE_BUTTON_HEIGHT + 4;
    private static final int STOCK_Y = WORKERS_BUTTON_Y + WIDE_BUTTON_HEIGHT + 4;
    // Workers layout.
    private static final int LIST_DIVIDER_Y = TOP + WorkplaceMenu.ROWS_PER_PAGE * LIST_ROW + 4;
    private static final int BOTTOM_BAR_Y = LIST_DIVIDER_Y + 4;

    public WorkplaceScreen(WorkplaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    private WorkplaceMenu.WorkplaceInfo info() {
        return menu.getInfo();
    }

    private boolean onWorkersPage() {
        return info().view() == WorkplaceMenu.View.WORKERS;
    }

    @Override
    protected void init() {
        super.init(); // centers the panel
        if (onWorkersPage()) {
            initWorkers();
        } else {
            initOverview();
        }
    }

    private void initOverview() {
        WorkplaceMenu.WorkplaceInfo info = info();
        addRenderableWidget(Button.builder(Component.literal("-"), button -> click(WorkplaceMenu.SMALLER_AREA_BUTTON))
                .bounds(leftPos + RIGHT - 2 * SMALL_BUTTON - 2, topPos + WORK_AREA_Y, SMALL_BUTTON, SMALL_BUTTON)
                .tooltip(Tooltip.create(Component.literal("Smaller work area")))
                .build()).active = info.workRadius() > WorkArea.MIN_RADIUS;
        addRenderableWidget(Button.builder(Component.literal("+"), button -> click(WorkplaceMenu.LARGER_AREA_BUTTON))
                .bounds(leftPos + RIGHT - SMALL_BUTTON, topPos + WORK_AREA_Y, SMALL_BUTTON, SMALL_BUTTON)
                .tooltip(Tooltip.create(Component.literal("Larger work area (up to " + info.maxWorkRadius() + " blocks)")))
                .build()).active = info.workRadius() < info.maxWorkRadius();
        wideButton("Automatic hiring: " + (info.autoHire() ? "On" : "Off"), AUTO_HIRE_Y, WorkplaceMenu.AUTO_HIRE_BUTTON,
                "When on, unemployed settlers take this hut's openings by themselves. Firing someone here turns it off.");
        wideButton("Workers (" + info.workers().size() + "/" + info.capacity() + ")...", WORKERS_BUTTON_Y,
                WorkplaceMenu.SHOW_WORKERS_BUTTON, "See who works here, and hire or fire");
        wideButton("Stock", STOCK_Y, WorkplaceMenu.STOCK_BUTTON, null);
    }

    private void initWorkers() {
        WorkplaceMenu.WorkplaceInfo info = info();
        List<WorkplaceMenu.Row> rows = info.pageRows();
        for (int i = 0; i < rows.size(); i++) {
            WorkplaceMenu.Row row = rows.get(i);
            int y = TOP + i * LIST_ROW;
            if (row.kind() == WorkplaceMenu.Row.Kind.WORKER) {
                personButton("Fire", y, WorkplaceMenu.FIRE_BUTTON + row.index());
            } else if (row.kind() == WorkplaceMenu.Row.Kind.CANDIDATE && !info.isFull()) {
                personButton("Hire", y, WorkplaceMenu.HIRE_BUTTON + row.index());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("< Back"), button -> click(WorkplaceMenu.SHOW_OVERVIEW_BUTTON))
                .bounds(leftPos + LEFT, topPos + BOTTOM_BAR_Y, 50, BUTTON_HEIGHT).build());
        if (info.pages() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> click(WorkplaceMenu.PREVIOUS_PAGE_BUTTON))
                    .bounds(leftPos + RIGHT - 2 * 20 - 2, topPos + BOTTOM_BAR_Y, 20, BUTTON_HEIGHT).build()).active = info.page() > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), button -> click(WorkplaceMenu.NEXT_PAGE_BUTTON))
                    .bounds(leftPos + RIGHT - 20, topPos + BOTTOM_BAR_Y, 20, BUTTON_HEIGHT).build()).active = info.page() < info.pages() - 1;
        }
    }

    private void wideButton(String label, int y, int id, String tooltip) {
        Button.Builder builder = Button.builder(Component.literal(label), button -> click(id))
                .bounds(leftPos + LEFT, topPos + y, WIDTH - 2 * LEFT, WIDE_BUTTON_HEIGHT);
        if (tooltip != null) {
            builder.tooltip(Tooltip.create(Component.literal(tooltip)));
        }
        addRenderableWidget(builder.build());
    }

    private void personButton(String label, int rowY, int id) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> click(id))
                .bounds(leftPos + RIGHT - PERSON_BUTTON_WIDTH, topPos + rowY, PERSON_BUTTON_WIDTH, BUTTON_HEIGHT).build());
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
        int dividerY = topPos + (onWorkersPage() ? LIST_DIVIDER_Y : OVERVIEW_DIVIDER_Y);
        graphics.fill(leftPos + LEFT, dividerY, leftPos + RIGHT, dividerY + 1, DIVIDER_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, LEFT, 6, TEXT_COLOR, false);
        if (onWorkersPage()) {
            renderWorkers(graphics);
        } else {
            renderOverview(graphics);
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        WorkplaceMenu.WorkplaceInfo info = info();
        row(graphics, TOP, "Settlement", info.settlementName());
        row(graphics, TOP + ROW, "Job", info.occupation().isEmpty() ? "None" : DisplayNames.occupation(info.occupation()));
        row(graphics, TOP + 2 * ROW, "Workers", info.workers().size() + "/" + info.capacity());
        int areaTextY = centred(WORK_AREA_Y, SMALL_BUTTON);
        graphics.drawString(font, "Work area", LEFT, areaTextY, LABEL_COLOR, false);
        String area = info.workRadius() + " blocks";
        graphics.drawString(font, area, RIGHT - 2 * SMALL_BUTTON - 6 - font.width(area), areaTextY, TEXT_COLOR, false);
    }

    private void renderWorkers(GuiGraphics graphics) {
        WorkplaceMenu.WorkplaceInfo info = info();
        String subtitle = "Workers";
        graphics.drawString(font, subtitle, RIGHT - font.width(subtitle), 6, LABEL_COLOR, false);
        List<WorkplaceMenu.Row> rows = info.pageRows();
        for (int i = 0; i < rows.size(); i++) {
            WorkplaceMenu.Row row = rows.get(i);
            int textY = centred(TOP + i * LIST_ROW, BUTTON_HEIGHT);
            switch (row.kind()) {
                case WORKERS_TITLE -> sectionTitle(graphics, textY, "Working here", info.workers().size() + "/" + info.capacity());
                case WORKER -> person(graphics, textY, info.workers().get(row.index()).name(), true);
                case NO_WORKERS -> graphics.drawString(font, "Nobody yet", LEFT, textY, LABEL_COLOR, false);
                case CANDIDATES_TITLE -> sectionTitle(graphics, textY, "Looking for work",
                        info.isFull() && !info.candidates().isEmpty() ? "hut is full" : "");
                case CANDIDATE -> person(graphics, textY, info.candidates().get(row.index()).name(), !info.isFull());
                case NO_CANDIDATES -> graphics.drawString(font, "No unemployed settlers nearby", LEFT, textY, LABEL_COLOR, false);
            }
        }
        if (info.pages() > 1) {
            String page = (info.page() + 1) + "/" + info.pages();
            int pageX = LEFT + 50 + (RIGHT - 2 * 20 - 2 - LEFT - 50 - font.width(page)) / 2;
            graphics.drawString(font, page, pageX, centred(BOTTOM_BAR_Y, BUTTON_HEIGHT), LABEL_COLOR, false);
        }
    }

    private void row(GuiGraphics graphics, int y, String label, String value) {
        graphics.drawString(font, label, LEFT, y, LABEL_COLOR, false);
        graphics.drawString(font, value, RIGHT - font.width(value), y, TEXT_COLOR, false);
    }

    private void sectionTitle(GuiGraphics graphics, int y, String title, String note) {
        graphics.drawString(font, title, LEFT, y, TEXT_COLOR, false);
        graphics.drawString(font, note, RIGHT - font.width(note), y, LABEL_COLOR, false);
    }

    // A name, cut short with "..." if it would run into its button.
    private void person(GuiGraphics graphics, int textY, String name, boolean hasButton) {
        int room = (hasButton ? RIGHT - PERSON_BUTTON_WIDTH - 4 : RIGHT) - LEFT - 6;
        String shown = font.width(name) <= room ? name : font.plainSubstrByWidth(name, room - font.width("...")) + "...";
        graphics.drawString(font, shown, LEFT + 6, textY, TEXT_COLOR, false); // indented under its section title
    }

    // The y at which text sits centred against something of the given height starting at rowY.
    private static int centred(int rowY, int height) {
        return rowY + (height - TEXT_HEIGHT) / 2 + 1;
    }
}
