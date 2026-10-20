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

// Client only. Draws the town hall menu in the vanilla style, the same size as the hut and site
// panels, as two pages:
//   - Overview: settlement, owner, the live control meter and claim; a Claim button for an
//     unclaimed hall, and a Population button for the owning faction's members.
//   - Population: everyone in the settlement, a page at a time: each name with its job title, with
//     Back and paging.
//   - Builders: the settlement's builders (what each is working on, and Fire), then the settlers
//     looking for work (Hire), a page of rows at a time, with Back and paging.
public class TownHallScreen extends AbstractContainerScreen<TownHallMenu> {
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int LABEL_COLOR = 0xFF707070;
    private static final int DIVIDER_COLOR = 0xFF9A9A9A;
    private static final int WIDTH = 176;
    private static final int HEIGHT = 160;
    private static final int LEFT = 8;
    private static final int RIGHT = WIDTH - 8;
    private static final int TOP = 20;
    private static final int ROW = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SMALL_BUTTON_HEIGHT = 16;
    // Population layout: one line per person.
    private static final int PERSON_ROW = 12;
    // Builders layout: rows with a small button at the end.
    private static final int LIST_ROW = SMALL_BUTTON_HEIGHT + 2;
    private static final int ROW_BUTTON_WIDTH = 36;
    private static final int HALF_BUTTON_WIDTH = (WIDTH - 2 * LEFT - 4) / 2;
    private static final int LIST_DIVIDER_Y = TOP + TownHallMenu.PEOPLE_PER_PAGE * PERSON_ROW + 2;
    private static final int BOTTOM_BAR_Y = LIST_DIVIDER_Y + 4;
    private static final int BOTTOM_BUTTON_Y = HEIGHT - 8 - BUTTON_HEIGHT;

    public TownHallScreen(TownHallMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    private TownHallMenu.HallInfo info() {
        return menu.getInfo();
    }

    private boolean onPopulationPage() {
        return info().view() == TownHallMenu.View.POPULATION;
    }

    private boolean onBuildersPage() {
        return info().view() == TownHallMenu.View.BUILDERS;
    }

    private boolean onListPage() {
        return onPopulationPage() || onBuildersPage();
    }

    @Override
    protected void init() {
        super.init(); // centers the panel: sets leftPos/topPos from imageWidth/imageHeight
        TownHallMenu.HallInfo info = info();
        if (onBuildersPage()) {
            List<TownHallMenu.Row> rows = info.pageRows();
            for (int i = 0; i < rows.size(); i++) {
                TownHallMenu.Row row = rows.get(i);
                int y = TOP + i * LIST_ROW;
                if (row.kind() == TownHallMenu.Row.Kind.BUILDER) {
                    rowButton("Fire", y, TownHallMenu.FIRE_BUILDER_BUTTON + row.index(), "No longer a builder (unemployed again)");
                } else if (row.kind() == TownHallMenu.Row.Kind.UNEMPLOYED) {
                    rowButton("Hire", y, TownHallMenu.HIRE_BUILDER_BUTTON + row.index(), "Hire as one of the settlement's builders");
                }
            }
        }
        if (onListPage()) {
            addRenderableWidget(Button.builder(Component.literal("< Back"), button -> click(TownHallMenu.SHOW_OVERVIEW_BUTTON))
                    .bounds(leftPos + LEFT, topPos + BOTTOM_BAR_Y, 50, SMALL_BUTTON_HEIGHT).build());
            if (info.pages() > 1) {
                addRenderableWidget(Button.builder(Component.literal("<"), button -> click(TownHallMenu.PREVIOUS_PAGE_BUTTON))
                        .bounds(leftPos + RIGHT - 42, topPos + BOTTOM_BAR_Y, 20, SMALL_BUTTON_HEIGHT).build()).active = info.page() > 0;
                addRenderableWidget(Button.builder(Component.literal(">"), button -> click(TownHallMenu.NEXT_PAGE_BUTTON))
                        .bounds(leftPos + RIGHT - 20, topPos + BOTTOM_BAR_Y, 20, SMALL_BUTTON_HEIGHT).build()).active = info.page() < info.pages() - 1;
            }
            return;
        }
        if (info.isUnclaimed()) {
            addRenderableWidget(Button.builder(Component.translatable("button.kingdomrts.claim"), button -> click(TownHallMenu.CLAIM_BUTTON))
                    .bounds(leftPos + LEFT, topPos + BOTTOM_BUTTON_Y, WIDTH - 2 * LEFT, BUTTON_HEIGHT).build());
        } else if (info.member()) {
            addRenderableWidget(Button.builder(Component.literal("Population (" + info.population() + ")"),
                            button -> click(TownHallMenu.SHOW_POPULATION_BUTTON))
                    .bounds(leftPos + LEFT, topPos + BOTTOM_BUTTON_Y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("Everyone who belongs to this settlement")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Builders (" + info.builderCount() + ")"),
                            button -> click(TownHallMenu.SHOW_BUILDERS_BUTTON))
                    .bounds(leftPos + RIGHT - HALF_BUTTON_WIDTH, topPos + BOTTOM_BUTTON_Y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("Hire and fire the settlement's builders")))
                    .build());
        }
    }

    private void rowButton(String label, int rowY, int id, String tooltip) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> click(id))
                .bounds(leftPos + RIGHT - ROW_BUTTON_WIDTH, topPos + rowY, ROW_BUTTON_WIDTH, SMALL_BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build());
    }

    // Tells the server a button was pressed (it runs TownHallMenu#clickMenuButton).
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
        if (onListPage()) {
            graphics.fill(leftPos + LEFT, topPos + LIST_DIVIDER_Y, leftPos + RIGHT, topPos + LIST_DIVIDER_Y + 1, DIVIDER_COLOR);
        }
    }

    // Text inside the panel; coordinates here are relative to the panel's top-left corner.
    // (Overridden so the default "Inventory" label isn't drawn: this menu has no inventory.)
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, LEFT, 6, TEXT_COLOR, false);
        TownHallMenu.HallInfo info = info();
        if (onListPage()) {
            String subtitle = onBuildersPage() ? "Builders" : "Population";
            graphics.drawString(font, subtitle, RIGHT - font.width(subtitle), 6, LABEL_COLOR, false);
            if (onBuildersPage()) {
                renderBuilders(graphics, info);
            } else {
                renderPopulation(graphics, info);
            }
            renderPageNumber(graphics, info);
            return;
        }
        if (info.isUnclaimed()) {
            graphics.drawString(font, "Unclaimed town hall", LEFT, TOP, TEXT_COLOR, false);
            return;
        }
        row(graphics, TOP, "Settlement", info.settlementName());
        row(graphics, TOP + ROW, "Owner", info.ownerName());
        row(graphics, TOP + 2 * ROW, "Control", menu.getControl() + "/" + TownHallBlockEntity.MAX_CONTROL);
        row(graphics, TOP + 3 * ROW, "Claim", info.claimedChunks() + " chunks");
        if (info.member()) {
            row(graphics, TOP + 4 * ROW, "Population", String.valueOf(info.population()));
        }
    }

    private void renderPopulation(GuiGraphics graphics, TownHallMenu.HallInfo info) {
        List<TownHallMenu.Person> people = info.pagePeople();
        if (people.isEmpty()) {
            graphics.drawString(font, "Nobody belongs to " + info.settlementName() + " yet", LEFT, TOP + 4, LABEL_COLOR, false);
        }
        for (int i = 0; i < people.size(); i++) {
            TownHallMenu.Person person = people.get(i);
            int y = TOP + i * PERSON_ROW;
            String job = jobTitle(person);
            graphics.drawString(font, fit(person.name(), RIGHT - LEFT - font.width(job) - 6), LEFT, y, TEXT_COLOR, false);
            graphics.drawString(font, job, RIGHT - font.width(job), y, LABEL_COLOR, false);
        }
    }

    private void renderBuilders(GuiGraphics graphics, TownHallMenu.HallInfo info) {
        List<TownHallMenu.Row> rows = info.pageRows();
        for (int i = 0; i < rows.size(); i++) {
            TownHallMenu.Row row = rows.get(i);
            int y = TOP + i * LIST_ROW + (SMALL_BUTTON_HEIGHT - 8) / 2 + 1;
            switch (row.kind()) {
                case BUILDERS_TITLE -> graphics.drawString(font, "Builders", LEFT, y, TEXT_COLOR, false);
                case BUILDER -> entry(graphics, y, info.builders().get(row.index()));
                case NO_BUILDERS -> graphics.drawString(font, "None yet", LEFT + 6, y, LABEL_COLOR, false);
                case UNEMPLOYED_TITLE -> graphics.drawString(font, "Looking for work", LEFT, y, TEXT_COLOR, false);
                case UNEMPLOYED -> entry(graphics, y, info.unemployed().get(row.index()));
                case NO_UNEMPLOYED -> graphics.drawString(font, "No unemployed settlers nearby", LEFT + 6, y, LABEL_COLOR, false);
            }
        }
    }

    // A name (indented under its section title), its note, and room for the row's button.
    private void entry(GuiGraphics graphics, int y, PanelEntry entry) {
        int right = RIGHT - ROW_BUTTON_WIDTH - 4;
        int noteX = right - font.width(entry.note());
        graphics.drawString(font, fit(entry.name(), noteX - 6 - LEFT - 6), LEFT + 6, y, TEXT_COLOR, false);
        graphics.drawString(font, entry.note(), noteX, y, LABEL_COLOR, false);
    }

    private void renderPageNumber(GuiGraphics graphics, TownHallMenu.HallInfo info) {
        if (info.pages() > 1) {
            String page = (info.page() + 1) + "/" + info.pages();
            int pageX = LEFT + 50 + (RIGHT - 42 - LEFT - 50 - font.width(page)) / 2;
            graphics.drawString(font, page, pageX, BOTTOM_BAR_Y + (SMALL_BUTTON_HEIGHT - 8) / 2 + 1, LABEL_COLOR, false);
        }
    }

    // "Lumberjack", "Soldier" or "Unemployed".
    private static String jobTitle(TownHallMenu.Person person) {
        return person.occupation().isEmpty() ? "Unemployed" : DisplayNames.occupation(person.occupation());
    }

    // Text cut short with "..." if it doesn't fit.
    private String fit(String text, int room) {
        return font.width(text) <= room ? text : font.plainSubstrByWidth(text, room - font.width("...")) + "...";
    }

    private void row(GuiGraphics graphics, int y, String label, String value) {
        graphics.drawString(font, label, LEFT, y, LABEL_COLOR, false);
        graphics.drawString(font, value, RIGHT - font.width(value), y, TEXT_COLOR, false);
    }
}
