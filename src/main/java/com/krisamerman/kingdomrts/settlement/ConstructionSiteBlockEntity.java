package com.krisamerman.kingdomrts.settlement;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

// A construction site's stock, where the materials for a building are pooled while it's built, with
// the same access and protection as Settlement Storage. What's being built is recorded on the
// settlement (see Building), not here.
public class ConstructionSiteBlockEntity extends SettlementStorageBlockEntity {
    public ConstructionSiteBlockEntity(BlockPos pos, BlockState state) {
        super(KingdomRts.CONSTRUCTION_SITE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("Site stock");
    }
}
