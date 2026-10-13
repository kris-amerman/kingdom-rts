package com.krisamerman.kingdomrts.settlement;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

// A block with an inventory that belongs to the settlement whose claim it stands in: Settlement
// Storage, and workplaces with their stock. What they share:
// - Placement: only inside a claim owned by the placer's faction.
// - Opening and breaking: only the owning faction's members (and creative-mode operators).
// - Explosions, the wither and pistons can't destroy or move it; hoppers and pipes can't reach it
//   (see SettlementStorageBlockEntity).
// - On capture, access follows the settlement's new owner.
// Like the town hall, the protection lives on the block itself rather than in event handlers.
// Subclasses say how it's recorded on the settlement (register/unregister) and what it's called.
public abstract class SettlementContainerBlock extends Block implements EntityBlock {
    private static final float PROTECTED_EXPLOSION_RESISTANCE = 3_600_000.0f; // bedrock's

    protected SettlementContainerBlock(Properties properties) {
        super(properties);
    }

    // Records the block on the settlement whose claim it stands in, after it's placed.
    protected abstract void register(ServerLevel level, BlockPos pos);

    // Forgets the block (it was broken). settlement is the one it belonged to.
    protected abstract void unregister(ServerLevel level, Settlement settlement, BlockPos pos);

    // What players are told it is when they're refused, e.g. "storage".
    protected abstract String noun();

    // What right-clicking opens for someone allowed in: the inventory, unless a subclass has a panel.
    protected void open(ServerPlayer player, SettlementStorageBlockEntity storage) {
        player.openMenu(storage);
    }

    // Returning null cancels the placement. The client can't see claims, so only the server
    // decides; a refused block briefly appears on the placer's screen and then disappears.
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            Player player = context.getPlayer();
            Faction faction = player != null ? FactionData.get(serverLevel).factionOf(player.getUUID()) : null;
            String problem = SettlementOps.ownClaimProblem(serverLevel, context.getClickedPos(), faction != null ? faction.id() : null, noun());
            if (problem != null) {
                if (player != null) {
                    player.displayClientMessage(Component.literal(problem), true);
                }
                return null;
            }
        }
        return super.getStateForPlacement(context);
    }

    // After a successful placement: record it on the settlement.
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            register(serverLevel, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof SettlementStorageBlockEntity storage) {
            if (storage.canAccess(player)) {
                open(serverPlayer, storage);
            } else {
                player.displayClientMessage(Component.literal("This " + noun() + " belongs to " + storage.getSettlement().name()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Owned storage that isn't yours: mining makes no progress on the server. (The client
    // can't tell who owns it, so cracks may show briefly before the server refuses.)
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SettlementStorageBlockEntity storage && !storage.canAccess(player)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    // The server's final say when a player breaks it.
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SettlementStorageBlockEntity storage && !storage.canAccess(player)) {
            player.displayClientMessage(Component.literal("This " + noun() + " belongs to " + storage.getSettlement().name()), true);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return isOwned(level, pos) ? PROTECTED_EXPLOSION_RESISTANCE : super.getExplosionResistance(state, level, pos, explosion);
    }

    // Entities that break blocks on their own: the wither, the ender dragon, ravagers.
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return !isOwned(level, pos) && super.canEntityDestroy(state, level, pos, entity);
    }

    // Broken (by its owner, or a creative operator): drop the contents and forget it.
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof SettlementStorageBlockEntity storage) {
            Settlement settlement = storage.getSettlement();
            if (settlement != null) {
                unregister(serverLevel, settlement, pos);
            }
        }
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston); // removes the block entity
    }

    private static boolean isOwned(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SettlementStorageBlockEntity storage && storage.getSettlement() != null;
    }
}
