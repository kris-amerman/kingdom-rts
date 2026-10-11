package com.krisamerman.kingdomrts.settlement;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

// The block a settlement is founded around. Each placed town hall gets a TownHallBlockEntity,
// which holds its settlement id and capture control meter. A newly placed hall is unclaimed until
// a player claims it from the town hall menu, which founds the settlement. (Placing the hall as a
// block is a stand-in until the build tool exists.)
//
// A claimed hall can't be destroyed in survival: not mined, not blown up, not broken by the wither
// or the dragon. Only creative-mode operators can break it (for testing), which dissolves the
// settlement. An unclaimed hall breaks normally, so a bare hall block can't be used as an
// unbreakable wall. The protection lives on the block itself rather than in event handlers, which
// tend to leak (explosions, other mods).
public class TownHallBlock extends Block implements EntityBlock {
    public static final MapCodec<TownHallBlock> CODEC = simpleCodec(TownHallBlock::new);
    // Whether this hall's settlement exists. Kept in the block state (not just the block entity)
    // because block states are synced to every client, so the client knows not to show mining
    // progress on a claimed hall. TownHallBlockEntity keeps it in step with its settlement.
    public static final BooleanProperty CLAIMED = BooleanProperty.create("claimed");
    // Bedrock's explosion resistance: no explosion can destroy a claimed hall.
    private static final float CLAIMED_EXPLOSION_RESISTANCE = 3_600_000.0f;

    public TownHallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CLAIMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CLAIMED);
    }

    // Only creative-mode operators may break a claimed hall.
    private static boolean mayBreakClaimed(Player player) {
        return player.isCreative() && player.hasPermissions(2);
    }

    // Mining speed. 0 means mining makes no progress at all (and shows no cracks).
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (state.getValue(CLAIMED) && !mayBreakClaimed(player)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    // The final say when a player breaks a block (on both sides). Returning false keeps it in place,
    // which also covers creative-mode players without operator rights, who break blocks instantly.
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (state.getValue(CLAIMED) && !mayBreakClaimed(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("A claimed town hall can't be broken"), true);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return state.getValue(CLAIMED) ? CLAIMED_EXPLOSION_RESISTANCE : super.getExplosionResistance(state, level, pos, explosion);
    }

    // Entities that break blocks on their own: the wither, the ender dragon, ravagers.
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return !state.getValue(CLAIMED) && super.canEntityDestroy(state, level, pos, entity);
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
