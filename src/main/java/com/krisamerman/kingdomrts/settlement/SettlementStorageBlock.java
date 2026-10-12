package com.krisamerman.kingdomrts.settlement;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Settlement Storage: a chest that belongs to the settlement whose claim it stands in, recorded in
// the settlement's storage list. Placement and protection rules: see SettlementContainerBlock.
public class SettlementStorageBlock extends SettlementContainerBlock {
    public static final MapCodec<SettlementStorageBlock> CODEC = simpleCodec(SettlementStorageBlock::new);

    public SettlementStorageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SettlementStorageBlockEntity(pos, state);
    }

    @Override
    protected void register(ServerLevel level, BlockPos pos) {
        SettlementOps.registerStorage(level, pos);
    }

    @Override
    protected void unregister(ServerLevel level, Settlement settlement, BlockPos pos) {
        SettlementOps.unregisterStorage(level, settlement, pos);
    }

    @Override
    protected String noun() {
        return "storage";
    }
}
