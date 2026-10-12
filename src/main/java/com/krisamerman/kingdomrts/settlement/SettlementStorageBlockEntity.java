package com.krisamerman.kingdomrts.settlement;

import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// A Settlement Storage block's inventory (one chest's worth) and the settlement it belongs to.
//
// Access follows the settlement's current owner, so capturing the settlement hands its storage to
// the new owner without changing anything here. A storage block with no settlement (placed by
// /setblock, or its settlement dissolved) is just an ordinary chest anyone can use.
//
// No automation: as a WorldlyContainer that offers no slots on any side, hoppers can't put items
// in or take them out, and it registers no item-handler capability, so pipe mods can't see it.
public class SettlementStorageBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private static final int SIZE = 27; // a single chest
    private static final int[] NO_SLOTS = new int[0];
    private static final String SETTLEMENT_TAG = "settlement";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    @Nullable
    private UUID settlementId;

    public SettlementStorageBlockEntity(BlockPos pos, BlockState state) {
        super(KingdomRts.SETTLEMENT_STORAGE_BLOCK_ENTITY.get(), pos, state);
    }

    // For blocks built on this one (a workplace's stock).
    protected SettlementStorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void setSettlement(@Nullable UUID settlementId) {
        this.settlementId = settlementId;
        setChanged();
    }

    // Server only: the settlement this storage belongs to, or null if it belongs to none.
    @Nullable
    public Settlement getSettlement() {
        return level instanceof ServerLevel serverLevel ? SettlementData.get(serverLevel).byId(settlementId) : null;
    }

    // Server only. Members of the owning faction may open and break it; so may creative-mode
    // operators (for testing). Storage that belongs to no settlement is open to everyone.
    public boolean canAccess(Player player) {
        Settlement settlement = getSettlement();
        if (settlement == null || (player.isCreative() && player.hasPermissions(2))) {
            return true;
        }
        Faction faction = FactionData.get((ServerLevel) level).factionOf(player.getUUID());
        return faction != null && faction.id().equals(settlement.owner());
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.kingdomrts.settlement_storage");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this); // vanilla's single-chest screen
    }

    // ---- No automation: no slots are reachable from any side ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return false;
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (settlementId != null) {
            tag.putUUID(SETTLEMENT_TAG, settlementId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        settlementId = tag.hasUUID(SETTLEMENT_TAG) ? tag.getUUID(SETTLEMENT_TAG) : null;
    }
}
