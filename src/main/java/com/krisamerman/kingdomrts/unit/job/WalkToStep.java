package com.krisamerman.kingdomrts.unit.job;

import java.util.List;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

// Walks to a spot. There are two kinds of target:
//   - to(spot): a place to stand;
//   - near(block): a block the unit wants to work on (a tree trunk, say). It walks to a standable
//     spot next to it rather than at the block itself, which could lead it up onto a tree.
//
// Getting stuck is noticed and handled, cheapest first:
//   1. re-plan the route once a second (never every tick: pathfinding is the expensive part);
//   2. no progress for 3 seconds, or standing on a tree: step to a random nearby spot (twice);
//   3. still stuck: hop to a safe spot a few blocks away (see Footing#escapeSpot);
//   4. still stuck: fail, and the job decides what to do instead.
// It also fails if the target stops being worth walking to (stillWanted turns false).
public final class WalkToStep implements JobStep {
    private static final int REPATH_TICKS = 20;
    private static final int STUCK_TICKS = 60;
    private static final int RANDOM_STEP_TICKS = 40;
    private static final int RANDOM_STEPS = 2;
    private static final int STAND_SPOTS_CHECKED = 8;
    private static final double SPEED = 1.0;
    private static final double SPOT_REACH = 1.2;

    @Nullable
    private BlockPos target;
    // For near(block): the block, from which a stand spot is chosen at the start.
    @Nullable
    private final BlockPos workBlock;
    private final double reach;
    private final int timeoutTicks;
    private final BooleanSupplier stillWanted;
    private int ticks;
    private double closest;
    // The closest it ever got (for the debug log; closest is reset by each recovery attempt).
    private double closestEver;
    private int ticksSinceCloser;
    private int stuckCount;
    private int randomStepTicks;

    private WalkToStep(@Nullable BlockPos target, @Nullable BlockPos workBlock, double reach, int timeoutTicks, BooleanSupplier stillWanted) {
        this.target = target;
        this.workBlock = workBlock;
        this.reach = reach;
        this.timeoutTicks = timeoutTicks;
        this.stillWanted = stillWanted;
    }

    // Walk to within reach of a spot.
    public static WalkToStep to(BlockPos spot, double reach, int timeoutTicks, BooleanSupplier stillWanted) {
        return new WalkToStep(spot.immutable(), null, reach, timeoutTicks, stillWanted);
    }

    // Walk to a standable spot within 2 blocks of a block.
    public static WalkToStep near(BlockPos block, int timeoutTicks, BooleanSupplier stillWanted) {
        return new WalkToStep(null, block.immutable(), SPOT_REACH, timeoutTicks, stillWanted);
    }

    @Override
    public void start(Unit unit) {
        ticks = 0;
        closest = Double.MAX_VALUE;
        closestEver = Double.MAX_VALUE;
        ticksSinceCloser = 0;
        stuckCount = 0;
        randomStepTicks = 0;
        if (workBlock != null) {
            target = chooseStandSpot(unit, workBlock);
        }
        if (KingdomRts.LOGGER.isDebugEnabled() && target != null) {
            Path path = unit.getNavigation().createPath(target, 0);
            KingdomRts.LOGGER.debug("Unit {} walking from {} to {} ({} blocks{}); first path {}", unit.getUUID(), unit.blockPosition().toShortString(),
                    target.toShortString(), String.format("%.1f", Math.sqrt(unit.blockPosition().distSqr(target))),
                    workBlock != null ? ", to work on " + workBlock.toShortString() : "", describe(path));
        }
    }

    // For the debug log: whether a path reaches its target, and where it ends.
    private static String describe(@Nullable Path path) {
        if (path == null) {
            return "none";
        }
        BlockPos end = path.getEndNode() != null ? path.getEndNode().asBlockPos() : null;
        return (path.canReach() ? "reaches" : "doesn't reach") + " (" + path.getNodeCount() + " nodes, ends at "
                + (end != null ? end.toShortString() : "?") + ")";
    }

    // Logs why a walk gave up, for reports of stuck units.
    private void logFailure(Unit unit, String why) {
        KingdomRts.LOGGER.debug("Unit {} gave up walking to {}{}: {}; at {}, closest {} blocks", unit.getUUID(),
                target != null ? target.toShortString() : "?", workBlock != null ? " (near " + workBlock.toShortString() + ")" : "", why,
                unit.blockPosition().toShortString(), closestEver == Double.MAX_VALUE ? "?" : String.format("%.1f", closestEver));
    }

    // The nearest stand spot next to the block that the unit has a full path to (checking only a
    // few), or failing that the nearest stand spot at all, in case getting unstuck opens a way.
    @Nullable
    private static BlockPos chooseStandSpot(Unit unit, BlockPos block) {
        List<BlockPos> spots = Footing.standSpotsNear(unit.level(), block, 2);
        for (int i = 0; i < Math.min(STAND_SPOTS_CHECKED, spots.size()); i++) {
            Path path = unit.getNavigation().createPath(spots.get(i), 0);
            if (path != null && path.canReach()) {
                return spots.get(i);
            }
        }
        return spots.isEmpty() ? null : spots.get(0);
    }

    @Override
    public Status tick(Unit unit) {
        if (target == null) {
            logFailure(unit, "no spot to stand near it");
            return Status.FAILED;
        }
        if (!stillWanted.getAsBoolean()) {
            return Status.FAILED; // no longer wanted (e.g. the tree is gone): nothing went wrong
        }
        double distance = unit.position().distanceTo(Vec3.atBottomCenterOf(target));
        closestEver = Math.min(closestEver, distance);
        if (distance <= reach && unit.onGround()) {
            return Status.DONE;
        }
        if (randomStepTicks > 0) { // stepping aside to get unstuck; let it play out
            randomStepTicks--;
            return Status.RUNNING;
        }
        if (distance < closest - 0.5) {
            closest = distance;
            ticksSinceCloser = 0;
        } else {
            ticksSinceCloser++;
        }
        if (ticksSinceCloser >= STUCK_TICKS || Footing.isOnTree(unit)) {
            if (!getUnstuck(unit)) {
                logFailure(unit, "stuck, with no way out left to try (last path " + describe(unit.getNavigation().getPath()) + ")");
                return Status.FAILED;
            }
            return Status.RUNNING;
        }
        if (ticks++ % REPATH_TICKS == 0) {
            // Accuracy 0: to the spot itself. (Vanilla's moveTo(x, y, z) is happy to stop a block
            // short, which would leave the unit just outside reach.)
            unit.getNavigation().moveTo(unit.getNavigation().createPath(target, 0), SPEED);
        }
        return Status.RUNNING;
    }

    // One rung of the recovery ladder. Returns false when there's nothing left to try.
    private boolean getUnstuck(Unit unit) {
        stuckCount++;
        ticksSinceCloser = 0;
        closest = Double.MAX_VALUE;
        ticks = 0; // re-plan right after
        if (stuckCount <= RANDOM_STEPS) {
            Vec3 aside = DefaultRandomPos.getPos(unit, 6, 3);
            if (aside != null) {
                unit.getNavigation().moveTo(aside.x, aside.y, aside.z, SPEED);
                randomStepTicks = RANDOM_STEP_TICKS;
            }
            return true;
        }
        if (stuckCount == RANDOM_STEPS + 1) {
            BlockPos spot = Footing.escapeSpot(unit, target);
            if (spot != null) {
                KingdomRts.LOGGER.debug("Unit {} stuck at {}; moving it to {}", unit.getUUID(), unit.blockPosition().toShortString(), spot.toShortString());
                unit.getNavigation().stop();
                unit.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                return true;
            }
        }
        return false;
    }

    @Override
    public void stop(Unit unit) {
        unit.getNavigation().stop();
    }

    @Override
    public int timeoutTicks() {
        return timeoutTicks;
    }

    @Override
    public String describe() {
        return "walk to " + (target != null ? target.toShortString() : workBlock != null ? "near " + workBlock.toShortString() : "?");
    }
}
