package com.krisamerman.kingdomrts.building;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

// The rules for buildings and their sites, as pure functions (they only see the world through a
// block lookup), so they can be unit tested:
//   - where a blueprint's blocks go for a site (its origin and rotation);
//   - whether a block in the world matches the one the blueprint wants;
//   - what's left to do at a site: blocks missing, blocks in the way, and the materials needed.
public final class BuildingRules {
    // The properties that make a block "the same" as the blueprint's: which way it faces or lies,
    // and which part of a multi-block thing it is. Others (waterlogged, open, powered, a leaf's
    // distance, a stair's corner shape, a chest's pairing) change in normal use, so a building
    // doesn't count as unfinished, or later damaged, because someone opened a door.
    private static final List<Property<?>> ORIENTATION = List.of(
            BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.FACING_HOPPER,
            BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS, BlockStateProperties.HALF,
            BlockStateProperties.DOUBLE_BLOCK_HALF, BlockStateProperties.BED_PART, BlockStateProperties.DOOR_HINGE,
            BlockStateProperties.ATTACH_FACE, BlockStateProperties.ROTATION_16, BlockStateProperties.SLAB_TYPE,
            BlockStateProperties.RAIL_SHAPE, BlockStateProperties.RAIL_SHAPE_STRAIGHT);

    // What's left to do at a site.
    //   missing: blocks of the blueprint not there yet (or not matching), with the state wanted;
    //   obstructions: positions where something is in the way (the blueprint wants air there, or
    //     another block stands where one of its blocks goes);
    //   materials: the items the missing blocks take, in blueprint order;
    //   unplaceable: missing blocks that have no item (fluids, fire...), so a builder can't place them.
    public record Remaining(List<Blueprint.Part> missing, List<BlockPos> obstructions, Map<Item, Integer> materials,
            List<Blueprint.Part> unplaceable) {
        public boolean isDone() {
            return missing.isEmpty() && obstructions.isEmpty();
        }
    }

    private BuildingRules() {
    }

    // The point the building turns around: the middle of its footprint, so a rotated building
    // stays over (about) the same ground.
    public static BlockPos pivot(Vec3i size) {
        return new BlockPos((size.getX() - 1) / 2, 0, (size.getZ() - 1) / 2);
    }

    // The blueprint's blocks where they go at a site: origin is the template's corner before
    // rotation, and each block is turned with the building.
    public static List<Blueprint.Part> place(Blueprint blueprint, BlockPos origin, Rotation rotation) {
        BlockPos pivot = pivot(blueprint.size());
        List<Blueprint.Part> placed = new ArrayList<>(blueprint.parts().size());
        for (Blueprint.Part part : blueprint.parts()) {
            BlockPos pos = StructureTemplate.transform(part.pos(), Mirror.NONE, rotation, pivot).offset(origin);
            placed.add(new Blueprint.Part(pos, part.state().rotate(rotation)));
        }
        return placed;
    }

    // The box a building takes up at a site.
    public static BoundingBox footprint(Vec3i size, BlockPos origin, Rotation rotation) {
        BlockPos pivot = pivot(size);
        BlockPos a = StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, rotation, pivot);
        BlockPos b = StructureTemplate.transform(new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1), Mirror.NONE, rotation, pivot);
        return BoundingBox.fromCorners(a, b).moved(origin.getX(), origin.getY(), origin.getZ());
    }

    // Whether the block in the world counts as the one the blueprint wants: the same block, facing
    // the same way (see ORIENTATION). Any kind of air counts as air.
    public static boolean matches(BlockState wanted, BlockState actual) {
        if (wanted.isAir()) {
            return actual.isAir();
        }
        if (wanted.getBlock() != actual.getBlock()) {
            return false;
        }
        for (Property<?> property : ORIENTATION) {
            if (wanted.hasProperty(property) && !wanted.getValue(property).equals(actual.getValue(property))) {
                return false;
            }
        }
        return true;
    }

    // What's left to do at a site, given its placed blueprint and what's in the world.
    public static Remaining remaining(List<Blueprint.Part> placed, Function<BlockPos, BlockState> stateAt) {
        List<Blueprint.Part> missing = new ArrayList<>();
        List<BlockPos> obstructions = new ArrayList<>();
        List<Blueprint.Part> unplaceable = new ArrayList<>();
        Map<Item, Integer> materials = new LinkedHashMap<>();
        for (Blueprint.Part part : placed) {
            BlockState actual = stateAt.apply(part.pos());
            if (matches(part.state(), actual)) {
                continue;
            }
            if (!actual.isAir() && !actual.canBeReplaced()) {
                obstructions.add(part.pos()); // something solid is in the way: it has to go first
            }
            if (part.state().isAir()) {
                if (actual.canBeReplaced() && !actual.isAir()) {
                    obstructions.add(part.pos()); // grass, snow and the like, where the blueprint wants air
                }
                continue;
            }
            missing.add(part);
            ItemStack cost = itemFor(part.state());
            if (cost.isEmpty()) {
                if (!isFreeHalf(part.state())) {
                    unplaceable.add(part);
                }
            } else {
                materials.merge(cost.getItem(), cost.getCount(), Integer::sum);
            }
        }
        return new Remaining(missing, obstructions, materials, unplaceable);
    }

    // What placing this block costs. Two-block things (doors, beds, tall flowers) cost one item, on
    // their lower half or foot; a double slab costs two slabs. Blocks with no item (air, fluids,
    // fire, a piston's head) cost nothing.
    public static ItemStack itemFor(BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || isFreeHalf(state)) {
            return ItemStack.EMPTY;
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        int count = state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE ? 2 : 1;
        return new ItemStack(item, count);
    }

    // The half of a two-block thing that comes free with the other half.
    private static boolean isFreeHalf(BlockState state) {
        return (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
                || (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD);
    }
}
