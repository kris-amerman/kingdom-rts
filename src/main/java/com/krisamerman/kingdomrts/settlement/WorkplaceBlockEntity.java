package com.krisamerman.kingdomrts.settlement;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

// A workplace's stock: one chest's worth of items, with the same access and protection as
// Settlement Storage (only the owning faction, no hoppers or pipes). Who works here is recorded on
// the settlement (see Workplace), not here, so it's known even while this chunk is unloaded.
public class WorkplaceBlockEntity extends SettlementStorageBlockEntity {
    public WorkplaceBlockEntity(BlockPos pos, BlockState state) {
        super(KingdomRts.WORKPLACE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }
}
