package com.krisamerman.kingdomrts.settlement;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;

// Opens a menu in place of the one already open (refreshing a panel, or going from a panel to its
// stock and back). Normally the server first tells the client to close the old screen, and closing a
// screen makes Minecraft move the mouse to the middle of the window; this skips that step, so the
// cursor stays where it was. (NeoForge's shouldTriggerClientSideContainerClosingOnOpen; vanilla's
// SimpleMenuProvider can't be extended, so this is its twin.)
public record SwitchingMenuProvider(MenuConstructor menuConstructor, Component title) implements MenuProvider {
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return menuConstructor.createMenu(containerId, inventory, player);
    }

    @Override
    public Component getDisplayName() {
        return title;
    }

    @Override
    public boolean shouldTriggerClientSideContainerClosingOnOpen() {
        return false;
    }
}
