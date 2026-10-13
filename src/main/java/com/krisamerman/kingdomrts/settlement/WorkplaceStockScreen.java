package com.krisamerman.kingdomrts.settlement;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.entity.player.Inventory;

// Client only. Vanilla's chest screen for a workplace's stock, with a Back button above it that
// returns to the hut panel.
public class WorkplaceStockScreen extends ContainerScreen {
    public WorkplaceStockScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("< Back"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkplaceStockMenu.BACK_BUTTON))
                .bounds(leftPos, topPos - 22, 60, 20)
                .build());
    }
}
