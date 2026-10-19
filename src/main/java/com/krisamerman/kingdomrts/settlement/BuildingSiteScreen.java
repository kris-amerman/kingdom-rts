package com.krisamerman.kingdomrts.settlement;

import java.util.List;

import com.krisamerman.kingdomrts.client.GuiDraw;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Client only. Draws the construction site panel in the vanilla style, the same size as the hut
// panel, as two pages:
//   - Overview: the settlement, progress (with a bar), what's in the way, the materials most
//     needed (with how many are on site), and buttons for the full materials list and the stock.
//   - Materials: every material still needed, a page at a time, with Back and paging.
public class BuildingSiteScreen extends AbstractContainerScreen<BuildingSiteMenu> {
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int LABEL_COLOR = 0xFF707070;
    private static final int DIVIDER_COLOR = 0xFF9A9A9A;
    private static final int BAR_BACKGROUND = 0xFF8B8B8B;
    private static final int BAR_FILL = 0xFF5DA03C;
    private static final int WIDTH = 176;
    private static final int HEIGHT = 160;
    private static final int LEFT = 8;
    private static final int RIGHT = WIDTH - 8;
    private static final int TOP = 20;
    private static final int ROW = 12;
    private static final int TEXT_HEIGHT = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SMALL_BUTTON_HEIGHT = 16;
    private static final int HALF_BUTTON_WIDTH = (WIDTH - 2 * LEFT - 4) / 2;
    private static final int LIST_ROW = 18;

    // Overview layout.
    private static final int PROGRESS_Y = TOP + ROW;
    private static final int BAR_Y = PROGRESS_Y + ROW;
    private static final int IN_THE_WAY_Y = BAR_Y + 8;
    private static final int OVERVIEW_DIVIDER_Y = IN_THE_WAY_Y + ROW;
    private static final int NEEDED_Y = OVERVIEW_DIVIDER_Y + 5;
    private static final int BUTTONS_Y = HEIGHT - 8 - BUTTON_HEIGHT;
    // Materials layout.
    private static final int LIST_DIVIDER_Y = TOP + BuildingSiteMenu.MATERIALS_PER_PAGE * LIST_ROW + 4;
    private static final int BOTTOM_BAR_Y = LIST_DIVIDER_Y + 4;

    public BuildingSiteScreen(BuildingSiteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    private BuildingSiteMenu.SiteInfo info() {
        return menu.getInfo();
    }

    private boolean onMaterialsPage() {
        return info().view() == BuildingSiteMenu.View.MATERIALS;
    }

    @Override
    protected void init() {
        super.init();
        BuildingSiteMenu.SiteInfo info = info();
        if (onMaterialsPage()) {
            addRenderableWidget(Button.builder(Component.literal("< Back"), button -> click(BuildingSiteMenu.SHOW_OVERVIEW_BUTTON))
                    .bounds(leftPos + LEFT, topPos + BOTTOM_BAR_Y, 50, SMALL_BUTTON_HEIGHT).build());
            if (info.pages() > 1) {
                addRenderableWidget(Button.builder(Component.literal("<"), button -> click(BuildingSiteMenu.PREVIOUS_PAGE_BUTTON))
                        .bounds(leftPos + RIGHT - 42, topPos + BOTTOM_BAR_Y, 20, SMALL_BUTTON_HEIGHT).build()).active = info.page() > 0;
                addRenderableWidget(Button.builder(Component.literal(">"), button -> click(BuildingSiteMenu.NEXT_PAGE_BUTTON))
                        .bounds(leftPos + RIGHT - 20, topPos + BOTTOM_BAR_Y, 20, SMALL_BUTTON_HEIGHT).build()).active = info.page() < info.pages() - 1;
            }
            return;
        }
        addRenderableWidget(Button.builder(Component.literal("Materials..."), button -> click(BuildingSiteMenu.SHOW_MATERIALS_BUTTON))
                .bounds(leftPos + LEFT, topPos + BUTTONS_Y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = !info.materials().isEmpty();
        addRenderableWidget(Button.builder(Component.literal("Stock"), button -> click(BuildingSiteMenu.STOCK_BUTTON))
                .bounds(leftPos + RIGHT - HALF_BUTTON_WIDTH, topPos + BUTTONS_Y, HALF_BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    // Tells the server a button was pressed (it runs BuildingSiteMenu#clickMenuButton).
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
        int dividerY = topPos + (onMaterialsPage() ? LIST_DIVIDER_Y : OVERVIEW_DIVIDER_Y);
        graphics.fill(leftPos + LEFT, dividerY, leftPos + RIGHT, dividerY + 1, DIVIDER_COLOR);
        BuildingSiteMenu.SiteInfo info = info();
        if (!onMaterialsPage() && info.loaded() && info.total() > 0) {
            int barWidth = RIGHT - LEFT;
            GuiDraw.sunken(graphics, leftPos + LEFT, topPos + BAR_Y, barWidth, 4, BAR_BACKGROUND);
            int filled = barWidth * info.placed() / info.total();
            graphics.fill(leftPos + LEFT, topPos + BAR_Y, leftPos + LEFT + filled, topPos + BAR_Y + 4, BAR_FILL);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        BuildingSiteMenu.SiteInfo info = info();
        graphics.drawString(font, info.buildingName(), LEFT, 6, TEXT_COLOR, false);
        String subtitle = onMaterialsPage() ? "Materials" : "Construction site";
        graphics.drawString(font, subtitle, RIGHT - font.width(subtitle), 6, LABEL_COLOR, false);
        if (onMaterialsPage()) {
            renderMaterialsPage(graphics, info);
        } else {
            renderOverview(graphics, info);
        }
    }

    private void renderOverview(GuiGraphics graphics, BuildingSiteMenu.SiteInfo info) {
        row(graphics, TOP, "Settlement", info.settlementName());
        if (!info.loaded()) {
            graphics.drawString(font, "The site isn't loaded", LEFT, PROGRESS_Y, LABEL_COLOR, false);
            return;
        }
        row(graphics, PROGRESS_Y, "Progress", info.isFinished() ? "Finished" : info.placed() + "/" + info.total() + " blocks");
        row(graphics, IN_THE_WAY_Y, "In the way", info.inTheWay() == 0 ? "Nothing" : info.inTheWay() + " blocks");
        List<BuildingSiteMenu.Material> materials = info.materials();
        int more = materials.size() - BuildingSiteMenu.OVERVIEW_MATERIALS;
        graphics.drawString(font, "Still needed", LEFT, NEEDED_Y, TEXT_COLOR, false);
        String note = materials.isEmpty() ? "nothing" : more > 0 ? "+" + more + " more" : "";
        graphics.drawString(font, note, RIGHT - font.width(note), NEEDED_Y, LABEL_COLOR, false);
        for (int i = 0; i < Math.min(materials.size(), BuildingSiteMenu.OVERVIEW_MATERIALS); i++) {
            material(graphics, NEEDED_Y + 11 + i * 11, materials.get(i), false);
        }
    }

    private void renderMaterialsPage(GuiGraphics graphics, BuildingSiteMenu.SiteInfo info) {
        List<BuildingSiteMenu.Material> materials = info.pageMaterials();
        if (materials.isEmpty()) {
            graphics.drawString(font, "Nothing more is needed", LEFT, TOP + 4, LABEL_COLOR, false);
        }
        for (int i = 0; i < materials.size(); i++) {
            material(graphics, TOP + i * LIST_ROW, materials.get(i), true);
        }
        if (info.pages() > 1) {
            String page = (info.page() + 1) + "/" + info.pages();
            int pageX = LEFT + 50 + (RIGHT - 42 - LEFT - 50 - font.width(page)) / 2;
            graphics.drawString(font, page, pageX, BOTTOM_BAR_Y + (SMALL_BUTTON_HEIGHT - TEXT_HEIGHT) / 2 + 1, LABEL_COLOR, false);
        }
    }

    // A material: "12 Oak Planks" on the left, "4 on site" on the right (with its icon on the
    // materials page, where rows are taller).
    private void material(GuiGraphics graphics, int y, BuildingSiteMenu.Material material, boolean withIcon) {
        int textY = withIcon ? y + (16 - TEXT_HEIGHT) / 2 + 1 : y;
        int x = LEFT;
        if (withIcon) {
            graphics.renderItem(new ItemStack(material.item()), LEFT, y);
            x += 20;
        }
        String onSite = material.onSite() >= material.needed() ? "all on site" : material.onSite() + " on site";
        int room = RIGHT - font.width(onSite) - 6 - x;
        String name = material.needed() + " " + material.item().getDescription().getString();
        String shown = font.width(name) <= room ? name : font.plainSubstrByWidth(name, room - font.width("...")) + "...";
        graphics.drawString(font, shown, x, textY, TEXT_COLOR, false);
        graphics.drawString(font, onSite, RIGHT - font.width(onSite), textY, LABEL_COLOR, false);
    }

    private void row(GuiGraphics graphics, int y, String label, String value) {
        graphics.drawString(font, label, LEFT, y, LABEL_COLOR, false);
        graphics.drawString(font, value, RIGHT - font.width(value), y, TEXT_COLOR, false);
    }
}
