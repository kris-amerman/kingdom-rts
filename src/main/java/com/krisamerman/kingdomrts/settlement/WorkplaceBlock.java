package com.krisamerman.kingdomrts.settlement;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// A workplace block, such as the lumberjack hut: placed directly for now, standing in for a building
// the build tool will place later. It belongs to the settlement whose claim it's in, which records
// it as a workplace (what kind comes from the WorkplaceType entry named after this block). Right-
// clicking opens its panel (WorkplaceMenu), which has a button for the stock. Placement and
// protection rules: see SettlementContainerBlock.
public class WorkplaceBlock extends SettlementContainerBlock {
    public static final MapCodec<WorkplaceBlock> CODEC = simpleCodec(WorkplaceBlock::new);

    public WorkplaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorkplaceBlockEntity(pos, state);
    }

    @Override
    protected void register(ServerLevel level, BlockPos pos) {
        WorkplaceOps.register(level, pos);
    }

    @Override
    protected void unregister(ServerLevel level, Settlement settlement, BlockPos pos) {
        WorkplaceOps.unregister(level, settlement, pos);
    }

    @Override
    protected String noun() {
        return "workplace";
    }

    @Override
    protected void open(ServerPlayer player, SettlementStorageBlockEntity storage) {
        if (storage instanceof WorkplaceBlockEntity stock) {
            WorkplaceMenu.open(player, stock, WorkplaceMenu.View.OVERVIEW, 0);
        }
    }
}
