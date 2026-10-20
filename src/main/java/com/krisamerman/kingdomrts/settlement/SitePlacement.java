package com.krisamerman.kingdomrts.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingRules;
import com.krisamerman.kingdomrts.unit.Stock;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

// Placing one block of a building, the way a builder does: from the site's stock, only where vanilla
// says the block can stay, and two-block things (doors, tall plants, beds) both halves at once. The
// rules come from vanilla's own blocks (see BuildingRules#costOf and #partnerOf); our own, not any
// other mod's.
public final class SitePlacement {
    // Attempts to place a block that was already there (another builder got to it first). Should
    // stay 0 while builders reserve their blocks (see SiteReservations); for diagnostics and tests.
    private static final AtomicInteger WASTED = new AtomicInteger();

    public enum Result {
        PLACED,
        // The block (and its other half, if any) is already in place.
        ALREADY_DONE,
        // The stock doesn't hold what it costs.
        NO_MATERIALS,
        // It can't stay there yet: nothing to hang on or stand on (a torch before its wall, sand
        // over air), or someone is standing in the spot.
        CANT_STAY_YET,
        // Something solid is in the spot (clearing it is S-030), or the block can't be placed by a
        // builder at all.
        BLOCKED
    }

    private SitePlacement() {
    }

    // Whether the part could go down now: its spot (and its other half's) is free to build in, the
    // block can stay there, a falling block has something under it, and nobody is in the way.
    public static Result check(ServerLevel level, Blueprint.Part part, @Nullable Blueprint.Part partner) {
        if (BuildingRules.matches(part.state(), level.getBlockState(part.pos()))
                && (partner == null || BuildingRules.matches(partner.state(), level.getBlockState(partner.pos())))) {
            return Result.ALREADY_DONE;
        }
        if (!level.getBlockState(part.pos()).canBeReplaced() || (partner != null && !level.getBlockState(partner.pos()).canBeReplaced())) {
            return Result.BLOCKED;
        }
        if (BuildingRules.costOf(part.state()).isEmpty()) {
            return Result.BLOCKED; // no item places it (lava, fire...)
        }
        BlockState state = BuildingRules.placedState(part.state());
        if (!state.canSurvive(level, part.pos())) {
            return Result.CANT_STAY_YET;
        }
        if (state.getBlock() instanceof FallingBlock && FallingBlock.isFree(level.getBlockState(part.pos().below()))) {
            return Result.CANT_STAY_YET;
        }
        if (!level.isUnobstructed(state, part.pos(), CollisionContext.empty())
                || (partner != null && !level.isUnobstructed(BuildingRules.placedState(partner.state()), partner.pos(), CollisionContext.empty()))) {
            return Result.CANT_STAY_YET; // someone is standing there
        }
        return Result.PLACED;
    }

    // Places the part (and its other half) if it can go down now and the stock holds what it costs,
    // taking that from the stock. A water source gives its empty bucket back to the stock.
    public static Result place(ServerLevel level, Blueprint.Part part, @Nullable Blueprint.Part partner, Container stock, @Nullable Entity builder) {
        Result check = check(level, part, partner);
        if (check != Result.PLACED) {
            if (check == Result.ALREADY_DONE) {
                WASTED.incrementAndGet();
            }
            return check;
        }
        List<ItemStack> cost = new ArrayList<>(BuildingRules.costOf(part.state()));
        if (!Stock.take(stock, cost)) {
            return Result.NO_MATERIALS;
        }
        BlockState state = BuildingRules.placedState(part.state());
        if (partner == null) {
            level.setBlock(part.pos(), state, Block.UPDATE_ALL);
        } else {
            // Both halves without shape updates, so neither pops off before the other is there,
            // then their neighbours are told.
            BlockState other = BuildingRules.placedState(partner.state());
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
            level.setBlock(part.pos(), state, flags);
            level.setBlock(partner.pos(), other, flags);
            level.blockUpdated(part.pos(), state.getBlock());
            level.blockUpdated(partner.pos(), other.getBlock());
        }
        if (cost.stream().anyMatch(stack -> stack.is(Items.WATER_BUCKET))) {
            Stock.insert(stock, new ItemStack(Items.BUCKET));
        }
        SoundType sound = state.getSoundType(level, part.pos(), builder);
        level.playSound(null, part.pos(), sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
        return Result.PLACED;
    }

    public static int wastedAttempts() {
        return WASTED.get();
    }
}
