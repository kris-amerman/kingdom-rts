package com.krisamerman.kingdomrts.building;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

// A building being previewed before it's commissioned: which building, how it's turned, and how far
// it's been nudged from where the player is looking. Pure placement maths, shared by the client's
// ghost and (in S-034) the commission request, so what's shown is exactly what gets built.
//
// The building sits on the anchor (the spot the player is looking at): its pivot block, the middle
// of its bottom layer (BuildingRules.pivot), goes there. Turning a building keeps its pivot in place,
// so rotating spins it on the spot instead of swinging it around a corner.
public final class BuildingPreview {
    // Moves relative to where the player faces, so "left" is always the player's left.
    public enum Nudge { FORWARD, BACK, LEFT, RIGHT, UP, DOWN }

    private final ResourceLocation type;
    private final String name;
    private final Blueprint blueprint;
    private Rotation rotation = Rotation.NONE;
    private BlockPos offset = BlockPos.ZERO;
    // The blueprint turned by the current rotation, with its corner at 0,0,0: worked out once per
    // turn, so drawing the ghost each frame only moves it.
    private List<Blueprint.Part> turned;

    public BuildingPreview(ResourceLocation type, String name, Blueprint blueprint) {
        this.type = type;
        this.name = name;
        this.blueprint = blueprint;
        this.turned = BuildingRules.place(blueprint, BlockPos.ZERO, rotation);
    }

    public ResourceLocation type() {
        return type;
    }

    public String name() {
        return name;
    }

    public Blueprint blueprint() {
        return blueprint;
    }

    public Rotation rotation() {
        return rotation;
    }

    // How far the player has nudged the building from the anchor.
    public BlockPos offset() {
        return offset;
    }

    // A quarter turn clockwise, seen from above.
    public void rotate() {
        rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
        turned = BuildingRules.place(blueprint, BlockPos.ZERO, rotation);
    }

    // Moves the building one block, relative to the way the player faces (only north, south, east
    // or west count: see Entity.getDirection).
    public void nudge(Nudge nudge, Direction facing) {
        offset = offset.relative(direction(nudge, facing));
    }

    // The world direction a nudge moves in for a player facing that way.
    public static Direction direction(Nudge nudge, Direction facing) {
        return switch (nudge) {
            case FORWARD -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getCounterClockWise();
            case RIGHT -> facing.getClockWise();
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }

    // The template corner (before turning) that puts the building on the anchor, as
    // BuildingOps.commission takes it.
    public BlockPos origin(BlockPos anchor) {
        return anchor.offset(offset).subtract(BuildingRules.pivot(blueprint.size()));
    }

    // The box the building would take up.
    public BoundingBox footprint(BlockPos anchor) {
        return BuildingRules.footprint(blueprint.size(), origin(anchor), rotation);
    }

    // The building's blocks, turned, with its template corner at 0,0,0: add origin(anchor) to put
    // them in the world. Matches BuildingRules.place(blueprint, origin(anchor), rotation()).
    public List<Blueprint.Part> turnedParts() {
        return turned;
    }
}
