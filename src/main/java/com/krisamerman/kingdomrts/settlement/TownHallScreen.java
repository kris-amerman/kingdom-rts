package com.krisamerman.kingdomrts.settlement;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

// Client only. Draws the town hall menu: a plain panel (no texture yet) with the owner, the live
// control meter, and a Claim button that's only shown for a neutral hall.
public class TownHallScreen extends AbstractContainerScreen<TownHallMenu> {
    private static final int PANEL_COLOR = 0xFFC6C6C6;   // vanilla GUI grey (ARGB)
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFF404040;

    private Button claimButton;

    public TownHallScreen(TownHallMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 84;
    }

    @Override
    protected void init() {
        super.init(); // centers the panel: sets leftPos/topPos from imageWidth/imageHeight
        claimButton = addRenderableWidget(Button.builder(Component.translatable("button.kingdomrts.claim"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TownHallMenu.CLAIM_BUTTON))
                .bounds(leftPos + 8, topPos + 56, imageWidth - 16, 20)
                .build());
        claimButton.visible = menu.isNeutral();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, BORDER_COLOR);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR);
    }

    // Text inside the panel; coordinates here are relative to the panel's top-left corner.
    // (Overridden so the default "Inventory" label isn't drawn: this menu has no inventory.)
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, TEXT_COLOR, false);
        String owner = menu.isNeutral() ? "none (neutral)" : menu.getOwnerName();
        graphics.drawString(font, "Owner: " + owner, 8, 22, TEXT_COLOR, false);
        graphics.drawString(font, "Control: " + menu.getControl() + "/" + TownHallBlockEntity.MAX_CONTROL, 8, 34, TEXT_COLOR, false);
    }
}
