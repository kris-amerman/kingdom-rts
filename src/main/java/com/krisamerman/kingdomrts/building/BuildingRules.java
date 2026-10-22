package com.krisamerman.kingdomrts.building;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
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

    // Where a building's construction site block goes while it's built: a spot the building leaves
    // clear (air in the template), so the site block never stands where a block is wanted. Floor
    // spots are preferred (air with one of the building's blocks right under it, lowest first), and
    // among those the one nearest the middle. Empty if the building has no clear spot at all.
    public static Optional<BlockPos> siteSpot(List<Blueprint.Part> placed) {
        if (placed.isEmpty()) {
            return Optional.empty();
        }
        Set<BlockPos> solid = new HashSet<>();
        long sumX = 0;
        long sumZ = 0;
        for (Blueprint.Part part : placed) {
            if (!part.state().isAir()) {
                solid.add(part.pos());
            }
            sumX += part.pos().getX();
            sumZ += part.pos().getZ();
        }
        double middleX = (double) sumX / placed.size();
        double middleZ = (double) sumZ / placed.size();
        Comparator<Blueprint.Part> preference = Comparator
                .<Blueprint.Part>comparingInt(part -> solid.contains(part.pos().below()) ? 0 : 1)
                .thenComparingInt(part -> part.pos().getY())
                .thenComparingDouble(part -> Math.pow(part.pos().getX() - middleX, 2) + Math.pow(part.pos().getZ() - middleZ, 2))
                .thenComparingLong(part -> part.pos().asLong());
        return placed.stream().filter(part -> part.state().isAir()).min(preference).map(Blueprint.Part::pos);
    }

    // The blocks of an unfinished building to draw where they'll go, so a player planning nearby
    // sees it finished: the ones not in place yet, at spots that are free now (air, or something a
    // block simply replaces, like grass). Blocks already in place are real, and a spot taken by
    // something else would be drawn through it, so both are left out.
    public static List<Blueprint.Part> stillToShow(List<Blueprint.Part> placed, Function<BlockPos, BlockState> stateAt) {
        List<Blueprint.Part> shown = new ArrayList<>();
        for (Blueprint.Part part : placed) {
            if (part.state().isAir()) {
                continue;
            }
            BlockState actual = stateAt.apply(part.pos());
            if (!matches(part.state(), actual) && (actual.isAir() || actual.canBeReplaced())) {
                shown.add(part);
            }
        }
        return shown;
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
            if (isObstruction(part.state(), actual)) {
                obstructions.add(part.pos()); // in the way: it has to go first (see isObstruction)
            }
            if (part.state().isAir()) {
                continue;
            }
            missing.add(part);
            List<ItemStack> cost = costOf(part.state());
            if (cost.isEmpty()) {
                if (!isFreeHalf(part.state())) {
                    unplaceable.add(part);
                }
            } else {
                cost.forEach(stack -> materials.merge(stack.getItem(), stack.getCount(), Integer::sum));
            }
        }
        return new Remaining(missing, obstructions, materials, unplaceable);
    }

    // What placing this block costs, worked out from vanilla's blocks. Usually its own item. Two-block
    // things (doors, tall plants, beds) cost one item, on their lower half or foot; a double slab
    // costs two slabs; snow layers, candles and sea pickles cost one per layer or piece; a potted
    // plant costs a flower pot and the plant; grass, farmland and paths cost dirt (they can't be
    // picked up as items without silk touch or a tool); a water source costs a water bucket (the
    // empty bucket comes back, see SitePlacement). Blocks with no item (air, lava, flowing fluids,
    // fire, a piston's head) cost nothing and can't be placed by builders. Empty for the free half
    // of a two-block thing.
    public static List<ItemStack> costOf(BlockState state) {
        if (state.isAir() || isFreeHalf(state)) {
            return List.of();
        }
        Block block = state.getBlock();
        if (block instanceof LiquidBlock) {
            return state.getFluidState().isSourceOfType(Fluids.WATER) ? List.of(new ItemStack(Items.WATER_BUCKET)) : List.of();
        }
        if (block instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR) {
            return List.of(new ItemStack(Items.FLOWER_POT), new ItemStack(pot.getPotted().asItem()));
        }
        if (block == Blocks.GRASS_BLOCK || block == Blocks.FARMLAND || block == Blocks.DIRT_PATH) {
            return List.of(new ItemStack(Items.DIRT));
        }
        Item item = block.asItem();
        if (item == Items.AIR) {
            return List.of();
        }
        int count = 1;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
            count = 2;
        } else if (state.hasProperty(BlockStateProperties.LAYERS) && block == Blocks.SNOW) {
            count = state.getValue(BlockStateProperties.LAYERS);
        } else if (state.hasProperty(BlockStateProperties.CANDLES)) {
            count = state.getValue(BlockStateProperties.CANDLES);
        } else if (state.hasProperty(BlockStateProperties.PICKLES)) {
            count = state.getValue(BlockStateProperties.PICKLES);
        }
        return List.of(new ItemStack(item, count));
    }

    // The other half of a two-block thing, which goes down in the same step as this one: the upper
    // half above a door's or tall plant's lower half, or a bed's head beside its foot. Empty for
    // anything else (or if the blueprint lacks the other half).
    public static Optional<Blueprint.Part> partnerOf(Blueprint.Part part, Map<BlockPos, Blueprint.Part> byPos) {
        BlockState state = part.state();
        BlockPos other = null;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            other = part.pos().above();
        } else if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
            other = part.pos().relative(BedBlock.getConnectedDirection(state));
        }
        Blueprint.Part partner = other != null ? byPos.get(other) : null;
        return partner != null && partner.state().is(state.getBlock()) ? Optional.of(partner) : Optional.empty();
    }

    // The order builders take a building apart in, the reverse of BUILD_ORDER: the top layer first,
    // and within a layer what hangs on other blocks (torches, doors, ladders) before full blocks.
    public static final Comparator<Blueprint.Part> DEMOLISH_ORDER = Comparator
            .<Blueprint.Part>comparingInt(part -> -part.pos().getY())
            .thenComparingInt(part -> part.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? 1 : 0)
            .thenComparingInt(part -> part.pos().getX())
            .thenComparingInt(part -> part.pos().getZ());

    // Whether a builder takes this block down when demolishing: it's one of the building's own
    // blocks (still as the template has it) and it can be broken at all.
    public static boolean isToTakeDown(BlockState wanted, BlockState actual) {
        return !wanted.isAir() && matches(wanted, actual) && actual.getBlock().defaultDestroyTime() >= 0
                && !(actual.getBlock() instanceof LiquidBlock);
    }

    // The order builders place blocks in: the lowest layer first, and within a layer full blocks
    // before the rest (so walls go up before what hangs on them), then a fixed order by position.
    // Whether a block can actually go down yet is checked when placing (see SitePlacement).
    public static final Comparator<Blueprint.Part> BUILD_ORDER = Comparator
            .<Blueprint.Part>comparingInt(part -> part.pos().getY())
            .thenComparingInt(part -> part.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? 0 : 1)
            .thenComparingInt(part -> part.pos().getX())
            .thenComparingInt(part -> part.pos().getZ());

    // Whether the block in the world is in the way of what the blueprint wants there, so a builder
    // breaks it first: something where the blueprint wants air (grass and flowers included), or
    // something solid where it wants another block (grass, snow and the like are simply replaced
    // when the block goes down). Fluids aren't broken (a builder can't pick them up yet), nor are
    // unbreakable blocks such as bedrock.
    public static boolean isObstruction(BlockState wanted, BlockState actual) {
        if (actual.isAir() || matches(wanted, actual) || actual.getBlock() instanceof LiquidBlock || actual.getBlock().defaultDestroyTime() < 0) {
            return false;
        }
        return wanted.isAir() || !actual.canBeReplaced();
    }

    // The order builders clear a site in: top down (so nothing is left hanging or falls into a
    // cleared spot), then a fixed order by position.
    public static final Comparator<BlockPos> CLEAR_ORDER = Comparator.<BlockPos>comparingInt(pos -> -pos.getY())
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);

    // The state a builder actually places: the blueprint's, but dry (a waterlogged block in a
    // template doesn't bring its own water).
    public static BlockState placedState(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
    }

    // The half of a two-block thing that comes free with the other half.
    private static boolean isFreeHalf(BlockState state) {
        return (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
                || (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD);
    }
}
