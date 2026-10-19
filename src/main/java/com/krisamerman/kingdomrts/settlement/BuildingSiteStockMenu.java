package com.krisamerman.kingdomrts.settlement;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;

// A construction site's stock, opened from its panel: vanilla's single-chest menu with its own menu
// type, shown with WorkplaceStockScreen's Back button, which returns to the site panel.
public class BuildingSiteStockMenu extends ChestMenu {
    public static final int BACK_BUTTON = WorkplaceStockMenu.BACK_BUTTON; // the screen sends this one

    // Server only: the construction site block whose stock this is. Null on the client.
    @Nullable
    private final ConstructionSiteBlockEntity core;

    BuildingSiteStockMenu(int containerId, Inventory inventory, ConstructionSiteBlockEntity core) {
        super(KingdomRts.BUILDING_SITE_STOCK_MENU.get(), containerId, inventory, core, 3);
        this.core = core;
    }

    // Client side: an empty chest the server fills in, like vanilla's.
    public BuildingSiteStockMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(KingdomRts.BUILDING_SITE_STOCK_MENU.get(), containerId, inventory, new SimpleContainer(27), 3);
        this.core = null;
    }

    public static void open(ServerPlayer player, ConstructionSiteBlockEntity core) {
        player.openMenu(new SwitchingMenuProvider((id, inventory, p) -> new BuildingSiteStockMenu(id, inventory, core),
                core.getDisplayName()), buf -> { });
    }

    // Back to the site panel.
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BACK_BUTTON && core != null && player instanceof ServerPlayer serverPlayer) {
            BuildingSiteMenu.open(serverPlayer, core, BuildingSiteMenu.View.OVERVIEW, 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return core == null || (super.stillValid(player) && core.canAccess(player));
    }
}
