package com.krisamerman.kingdomrts.settlement;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;

// A workplace's stock, opened from its panel: vanilla's single-chest menu with its own menu type, so
// its screen (WorkplaceStockScreen) can add a Back button that returns to the panel.
public class WorkplaceStockMenu extends ChestMenu {
    public static final int BACK_BUTTON = 0;

    // Server only: the workplace whose stock this is. Null on the client.
    @Nullable
    private final WorkplaceBlockEntity stock;

    WorkplaceStockMenu(int containerId, Inventory inventory, WorkplaceBlockEntity stock) {
        super(KingdomRts.WORKPLACE_STOCK_MENU.get(), containerId, inventory, stock, 3);
        this.stock = stock;
    }

    // Client side: an empty chest the server fills in, like vanilla's.
    public WorkplaceStockMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(KingdomRts.WORKPLACE_STOCK_MENU.get(), containerId, inventory, new SimpleContainer(27), 3);
        this.stock = null;
    }

    public static void open(ServerPlayer player, WorkplaceBlockEntity stock) {
        player.openMenu(new SwitchingMenuProvider((id, inventory, p) -> new WorkplaceStockMenu(id, inventory, stock),
                stock.getDisplayName()), buf -> { });
    }

    // Back to the panel.
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BACK_BUTTON && stock != null && player instanceof ServerPlayer serverPlayer) {
            WorkplaceMenu.open(serverPlayer, stock, 0);
            return true;
        }
        return false;
    }

    // As for the panel: closes if the hut is gone, the player walks away, or loses access.
    @Override
    public boolean stillValid(Player player) {
        return stock == null || (super.stillValid(player) && stock.canAccess(player));
    }
}
