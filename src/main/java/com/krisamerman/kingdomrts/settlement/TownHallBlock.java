package com.krisamerman.kingdomrts.settlement;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// The block a settlement is founded around. Each placed town hall gets a TownHallBlockEntity,
// which holds its settlement id and capture control meter. A newly placed hall is unclaimed until
// a player claims it from the town hall menu, which founds the settlement. (Placing the hall as a
// block is a stand-in until the build tool exists.)
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

    // Right-click with an empty hand opens the town hall menu (see TownHallMenu).
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof TownHallBlockEntity townHall) {
            TownHallMenu.open(serverPlayer, townHall);
        }
        // On the client, SUCCESS plays the hand-swing; the server opens the menu.
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Runs when this block is really replaced (broken, /setblock, an explosion), not when its chunk
    // unloads. A settlement can't outlive its town hall, so it's dissolved and its claim released.
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof TownHallBlockEntity townHall) {
            Settlement settlement = townHall.getSettlement();
            if (settlement != null) {
                SettlementOps.dissolve(serverLevel, settlement);
                serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal(settlement.name() + " was dissolved: its town hall is gone"), false);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston); // removes the block entity
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
