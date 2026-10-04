package com.krisamerman.kingdomrts.settlement;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// The block that founds a settlement. Each placed town hall gets a TownHallBlockEntity,
// which holds its capture control meter.
public class TownHallBlock extends Block implements EntityBlock {
    public static final MapCodec<TownHallBlock> CODEC = simpleCodec(TownHallBlock::new);

    public TownHallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TownHallBlockEntity(pos, state);
    }

    // The placing player's faction owns the new town hall. PLACEHOLDER until real faction data
    // exists: a player's faction id is just their UUID.
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof TownHallBlockEntity townHall) {
            townHall.setFaction(player.getUUID().toString());
        }
    }

    // Capture logic only runs on the server; the client gets no ticker.
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != KingdomRts.TOWN_HALL_BLOCK_ENTITY.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<TownHallBlockEntity>) TownHallBlockEntity::serverTick;
    }
}
