package com.krisamerman.kingdomrts.unit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;
import com.krisamerman.kingdomrts.settlement.WorkplaceBlockEntity;
import com.krisamerman.kingdomrts.settlement.Workplace;
import com.krisamerman.kingdomrts.settlement.WorkArea;
import com.krisamerman.kingdomrts.unit.job.Job;
import com.krisamerman.kingdomrts.unit.job.JobStep;
import com.krisamerman.kingdomrts.unit.job.WaitStep;
import com.krisamerman.kingdomrts.unit.job.WalkToStep;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

// The lumberjack's job, as explicit steps, working from its hut:
//   find a tree in the hut's work area -> walk next to it -> fell it (logs go into the inventory)
//   -> clear its canopy (the leaves the tree leaves behind, with their saplings, sticks and apples)
//   -> pick up anything still lying around -> repeat, until its inventory is nearly full or there
//   are no trees left -> walk back to the hut -> put everything except its axes in the hut's stock
//   (taking an axe from the stock if it has none).
// When something goes wrong it recovers instead of stalling: a tree it can't reach is skipped for a
// while, a tree that disappears on the way is simply replaced by the next one, and with no
// workplace, no trees, or nowhere to put what it carries (its inventory and the stock both full) it
// waits and checks again later.
public final class LumberjackJob implements Job {
    // Claim columns checked per tick while searching, so a search is spread over several ticks.
    private static final int COLUMNS_PER_TICK = 128;
    // Candidate trees checked (with a flood fill) per tick once the columns are scanned.
    private static final int TREES_CHECKED_PER_TICK = 4;
    private static final double REACH = 3.0;
    // How far around a felled tree it looks for drops to collect, and for how long at most.
    private static final int COLLECT_RADIUS = 8;
    private static final int COLLECT_TICKS = 20 * 5;
    // It goes back to the hut when fewer slots than this are empty (room for one more big tree).
    private static final int MIN_FREE_SLOTS = 2;
    // How close to the hut it must be to use its stock.
    private static final double HUT_REACH = 4.0;
    private static final int WALK_TIMEOUT_TICKS = 20 * 60;
    // Waits: no settlement / no room for logs, no tree found, and after a failed attempt.
    private static final int IDLE_TICKS = 100;
    private static final int NO_TREE_TICKS = 200;
    private static final int RETRY_TICKS = 20;
    // How long a tree it couldn't reach is skipped.
    private static final int SKIP_FAILED_TREE_TICKS = 20 * 60;

    private final LumberjackOccupation settings;
    // The tree being felled, once one is found.
    @Nullable
    private TreeRules.Tree tree;
    // Trees it couldn't reach (by base position), and the game time until which they're skipped.
    private final Map<BlockPos, Long> skipUntil = new HashMap<>();
    // The current walks, to tell a walk to a tree from a walk to the hut.
    @Nullable
    private JobStep treeWalk;
    @Nullable
    private JobStep hutWalk;

    public LumberjackJob(LumberjackOccupation settings) {
        this.settings = settings;
    }

    @Override
    public JobStep next(Unit unit, @Nullable JobStep previous, boolean succeeded) {
        if (previous == treeWalk && previous != null && !succeeded && tree != null && treeStillThere(unit)) {
            KingdomRts.LOGGER.debug("Lumberjack {} couldn't reach the tree at {}; skipping it for a while", unit.getUUID(), tree.base().toShortString());
            skipUntil.put(tree.base(), unit.level().getGameTime() + SKIP_FAILED_TREE_TICKS); // unreachable
        }
        if (settlementOf(unit) == null) {
            return new WaitStep(IDLE_TICKS, "no settlement");
        }
        Workplace workplace = workplaceOf(unit);
        if (workplace == null) {
            return new WaitStep(IDLE_TICKS, "no workplace");
        }
        if (previous != null && previous == hutWalk) {
            return succeeded ? new HutStep(workplace.pos()) : new WaitStep(RETRY_TICKS, "retrying the walk to the hut");
        }
        if (previous instanceof HutStep) {
            return succeeded ? new FindTreeStep() : new WaitStep(IDLE_TICKS, "no room in the hut's stock");
        }
        if (previous instanceof FindTreeStep) {
            if (!succeeded || tree == null) {
                return hasThingsToDeposit(unit) ? walkToHut(workplace) : new WaitStep(NO_TREE_TICKS, "no trees in the work area");
            }
            treeWalk = WalkToStep.near(tree.base(), WALK_TIMEOUT_TICKS, () -> treeStillThere(unit));
            return treeWalk;
        }
        if (previous != null && previous == treeWalk) {
            return succeeded ? new ChopStep() : new WaitStep(RETRY_TICKS, "retrying");
        }
        if (previous instanceof ChopStep) {
            return new ClearCanopyStep();
        }
        if (previous instanceof ClearCanopyStep) {
            return new CollectStep();
        }
        // Starting out, after a tree, or after waiting: go to the hut if it's nearly full (or has an
        // axe waiting there and none of its own); otherwise on to the next tree.
        return shouldDeposit(unit.getInventory()) || (!HutStep.hasAxe(unit) && hutHasAxe(unit, workplace))
                ? walkToHut(workplace) : new FindTreeStep();
    }

    private JobStep walkToHut(Workplace workplace) {
        hutWalk = WalkToStep.near(workplace.pos(), WALK_TIMEOUT_TICKS, () -> true);
        return hutWalk;
    }

    @Nullable
    private static Workplace workplaceOf(Unit unit) {
        return unit.level() instanceof ServerLevel level ? WorkplaceOps.workplaceOf(level, unit) : null;
    }

    // Whether the hut's stock has an axe (only checked if its chunk is loaded).
    private static boolean hutHasAxe(Unit unit, Workplace workplace) {
        return unit.level().isLoaded(workplace.pos()) && unit.level().getBlockEntity(workplace.pos()) instanceof WorkplaceBlockEntity stock
                && stock.hasAnyMatching(stack -> stack.is(ItemTags.AXES));
    }

    // Pure: whether it's time to take its load to the hut: fewer than MIN_FREE_SLOTS slots are empty,
    // or there's no room for another log. (Walking back after every tree would waste most of its day.)
    public static boolean shouldDeposit(SimpleContainer inventory) {
        int empty = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                empty++;
            }
        }
        return empty < MIN_FREE_SLOTS || !hasRoomForLogs(inventory);
    }

    // Pure: a leaf left behind by a felled tree, which vanilla would let decay: natural (not placed
    // by a player) and with no log within 6 leaves (distance 7).
    public static boolean isOrphanedLeaf(BlockState state) {
        return state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)
                && state.getValue(LeavesBlock.DISTANCE) == LeavesBlock.DECAY_DISTANCE;
    }

    // Anything besides axes: what it brings back to the hut.
    static boolean hasThingsToDeposit(Unit unit) {
        SimpleContainer inventory = unit.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && !stack.is(ItemTags.AXES)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public TreeRules.Tree currentTree() {
        return tree;
    }

    @Nullable
    private static Settlement settlementOf(Unit unit) {
        if (!(unit.level() instanceof ServerLevel level)) {
            return null;
        }
        Settlement settlement = SettlementData.get(level).byId(unit.getSettlementId());
        return settlement != null && settlement.dimension().equals(level.dimension()) ? settlement : null;
    }

    private boolean treeStillThere(Unit unit) {
        return tree != null && unit.level().getBlockState(tree.base()).is(BlockTags.LOGS);
    }

    static boolean hasRoomForLogs(SimpleContainer inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || (stack.is(ItemTags.LOGS) && stack.getCount() < stack.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    // How the tree rules see a block in the world. Unloaded blocks count as OTHER, so a search never
    // loads chunks.
    static TreeRules.Kind kindAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return TreeRules.Kind.OTHER;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.LOGS)) {
            return TreeRules.Kind.LOG;
        }
        if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
            return TreeRules.Kind.NATURAL_LEAVES;
        }
        return TreeRules.Kind.OTHER;
    }

    // Finds the nearest tree in the work area. First it scans the area's columns, a batch per tick:
    // the highest non-leaf block of each column (a heightmap lookup, no digging through the world)
    // is a log at the top of a trunk or branch. Then it checks those candidates, nearest first,
    // with the tree rules.
    private final class FindTreeStep implements JobStep {
        private final List<ChunkPos> chunks = new ArrayList<>();
        private final List<BlockPos> candidates = new ArrayList<>();
        private int column;
        private boolean scanned;
        // Where the unit was when the search started; the nearest tree to here wins.
        private BlockPos from = BlockPos.ZERO;
        // The work area: within radius of the hut (and inside the claim).
        private BlockPos hut = BlockPos.ZERO;
        private int radius;

        @Override
        public void start(Unit unit) {
            tree = null;
            from = unit.blockPosition();
            Settlement settlement = settlementOf(unit);
            Workplace workplace = workplaceOf(unit);
            if (settlement != null && workplace != null) {
                hut = workplace.pos();
                radius = workplace.workRadius();
                // Only claim chunks that reach into the work area.
                for (ChunkPos chunk : settlement.chunks()) {
                    if (WorkArea.touchesChunk(hut, radius, chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ())) {
                        chunks.add(chunk);
                    }
                }
            }
            long now = unit.level().getGameTime();
            skipUntil.values().removeIf(until -> until <= now);
        }

        @Override
        public Status tick(Unit unit) {
            ServerLevel level = (ServerLevel) unit.level();
            if (!scanned) {
                scanColumns(level);
                return Status.RUNNING;
            }
            for (int i = 0; i < TREES_CHECKED_PER_TICK && !candidates.isEmpty(); i++) {
                Optional<TreeRules.Tree> found = TreeRules.find(candidates.remove(0), pos -> kindAt(level, pos));
                if (found.isPresent() && !skipUntil.containsKey(found.get().base())) {
                    tree = found.get();
                    return Status.DONE;
                }
            }
            return candidates.isEmpty() ? Status.FAILED : Status.RUNNING;
        }

        private void scanColumns(ServerLevel level) {
            int total = chunks.size() * 256;
            int end = Math.min(total, column + COLUMNS_PER_TICK);
            for (; column < end; column++) {
                ChunkPos chunk = chunks.get(column / 256);
                if (!level.hasChunk(chunk.x, chunk.z)) {
                    column = (column / 256) * 256 + 255; // skip unloaded chunks entirely
                    continue;
                }
                int x = chunk.getMinBlockX() + (column % 256) / 16;
                int z = chunk.getMinBlockZ() + column % 16;
                if (!WorkArea.contains(hut, radius, x, z)) {
                    continue;
                }
                BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                if (level.getBlockState(top).is(BlockTags.LOGS)) {
                    candidates.add(top);
                }
            }
            if (column >= total) {
                scanned = true;
                candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(from)));
            }
        }

        @Override
        public int timeoutTicks() {
            return 20 * 30;
        }

        @Override
        public String describe() {
            return "find a tree";
        }
    }

    // Fells the tree from where it stands, highest log first, taking as long per log as a player
    // would with the same tool. While chopping it holds its best axe (from its inventory) in its
    // hand; each log costs the axe durability, and when one breaks it takes the next or carries on
    // by hand. It can reach every log of the tree from the ground, so it never climbs. Each log's
    // drops go into the inventory (anything that doesn't fit drops at its feet); it stops early when
    // there's no more room for logs. Fails if it gets pushed too far from the tree.
    private final class ChopStep implements JobStep {
        private static final int SWING_TICKS = 5;

        private int nextLog;
        private int progressTicks;
        @Nullable
        private BlockPos cracking;
        private int timeout;
        // The inventory slot the axe came from, so it goes back there when the step ends; -1 if none.
        private int axeSlot = -1;

        @Override
        public void start(Unit unit) {
            nextLog = 0;
            progressTicks = 0;
            equipBestAxe(unit);
            // Generous: every log by hand, plus slack.
            timeout = 100;
            if (tree != null) {
                for (BlockPos log : tree.logs()) {
                    BlockState state = unit.level().getBlockState(log);
                    timeout += Math.min(20 * 30, BreakTime.ticks(unit, ItemStack.EMPTY, state, unit.level(), log));
                }
            }
        }

        @Override
        public Status tick(Unit unit) {
            if (tree == null || unit.position().distanceTo(tree.base().getBottomCenter()) > REACH + 3) {
                return Status.FAILED;
            }
            ServerLevel level = (ServerLevel) unit.level();
            while (nextLog < tree.logs().size() && !level.getBlockState(tree.logs().get(nextLog)).is(BlockTags.LOGS)) {
                nextLog++; // already gone
                progressTicks = 0;
            }
            if (nextLog >= tree.logs().size()) {
                return Status.DONE;
            }
            if (!hasRoomForLogs(unit.getInventory())) {
                return Status.DONE;
            }
            BlockPos log = tree.logs().get(nextLog);
            BlockState state = level.getBlockState(log);
            ItemStack axe = unit.getMainHandItem();
            unit.getLookControl().setLookAt(log.getX() + 0.5, log.getY() + 0.5, log.getZ() + 0.5);
            if (progressTicks % SWING_TICKS == 0) {
                unit.swing(InteractionHand.MAIN_HAND);
            }
            int needed = BreakTime.ticks(unit, axe, state, level, log);
            progressTicks++;
            if (progressTicks < needed) {
                showCrack(level, unit, log, progressTicks * 10 / needed); // vanilla's 10 crack stages
                return Status.RUNNING;
            }
            List<ItemStack> drops = Block.getDrops(state, level, log, level.getBlockEntity(log), unit, axe);
            clearCrack(level, unit);
            level.destroyBlock(log, false, unit); // breaking sound and particles, no item entities
            if (!axe.isEmpty()) {
                axe.hurtAndBreak(1, unit, EquipmentSlot.MAINHAND);
                if (unit.getMainHandItem().isEmpty()) {
                    equipBestAxe(unit); // it broke: take the next one, if any
                }
            }
            for (ItemStack drop : drops) {
                ItemStack left = unit.getInventory().addItem(drop);
                if (!left.isEmpty()) {
                    unit.spawnAtLocation(left);
                }
            }
            nextLog++;
            progressTicks = 0;
            return Status.RUNNING;
        }

        @Override
        public void stop(Unit unit) {
            clearCrack((ServerLevel) unit.level(), unit);
            putAxeAway(unit);
        }

        // Moves the inventory's fastest axe (against logs) into the main hand.
        private void equipBestAxe(Unit unit) {
            SimpleContainer inventory = unit.getInventory();
            BlockState log = Blocks.OAK_LOG.defaultBlockState();
            int best = -1;
            float bestSpeed = 1.0f;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(ItemTags.AXES) && stack.getDestroySpeed(log) > bestSpeed) {
                    best = i;
                    bestSpeed = stack.getDestroySpeed(log);
                }
            }
            if (best >= 0 && unit.getMainHandItem().isEmpty()) {
                unit.setItemSlot(EquipmentSlot.MAINHAND, inventory.removeItemNoUpdate(best));
                unit.setGuaranteedDrop(EquipmentSlot.MAINHAND); // it's the unit's own axe: always drop it on death
                inventory.setChanged();
                axeSlot = best;
            }
        }

        // Back into the inventory slot it came from (or any free slot); if there's no room it stays
        // in hand.
        private void putAxeAway(Unit unit) {
            ItemStack held = unit.getMainHandItem();
            if (held.isEmpty()) {
                return;
            }
            SimpleContainer inventory = unit.getInventory();
            if (axeSlot >= 0 && inventory.getItem(axeSlot).isEmpty()) {
                inventory.setItem(axeSlot, held);
                unit.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            } else if (inventory.canAddItem(held)) {
                inventory.addItem(held);
                unit.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
            axeSlot = -1;
        }

        private void showCrack(ServerLevel level, Unit unit, BlockPos log, int stage) {
            cracking = log;
            level.destroyBlockProgress(unit.getId(), log, stage);
        }

        private void clearCrack(ServerLevel level, Unit unit) {
            if (cracking != null) {
                level.destroyBlockProgress(unit.getId(), cracking, -1);
                cracking = null;
            }
        }

        @Override
        public int timeoutTicks() {
            return timeout;
        }

        @Override
        public String describe() {
            return "fell the tree at " + (tree != null ? tree.base().toShortString() : "?");
        }
    }

    // At the hut: puts everything it carries except axes into the hut's stock, as much as fits, and
    // takes the stock's best axe if it has none. Fails if it still has no room for logs afterwards
    // (the stock is full), so the job waits instead of felling trees it can't carry.
    private static final class HutStep implements JobStep {
        private final BlockPos hut;

        HutStep(BlockPos hut) {
            this.hut = hut;
        }

        @Override
        public Status tick(Unit unit) {
            if (unit.position().distanceTo(hut.getCenter()) > HUT_REACH
                    || !(unit.level().getBlockEntity(hut) instanceof WorkplaceBlockEntity stock)) {
                return Status.FAILED;
            }
            SimpleContainer inventory = unit.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && !stack.is(ItemTags.AXES)) {
                    inventory.setItem(i, Stock.insert(stock, stack));
                }
            }
            if (!hasAxe(unit)) {
                takeBestAxe(stock, inventory);
            }
            return hasRoomForLogs(inventory) ? Status.DONE : Status.FAILED;
        }

        static boolean hasAxe(Unit unit) {
            return unit.getMainHandItem().is(ItemTags.AXES) || unit.getInventory().hasAnyMatching(stack -> stack.is(ItemTags.AXES));
        }

        private void takeBestAxe(WorkplaceBlockEntity stock, SimpleContainer inventory) {
            BlockState log = Blocks.OAK_LOG.defaultBlockState();
            int best = -1;
            for (int i = 0; i < stock.getContainerSize(); i++) {
                ItemStack stack = stock.getItem(i);
                if (stack.is(ItemTags.AXES) && (best < 0 || stack.getDestroySpeed(log) > stock.getItem(best).getDestroySpeed(log))) {
                    best = i;
                }
            }
            if (best >= 0 && inventory.canAddItem(stock.getItem(best))) {
                inventory.addItem(stock.removeItem(best, 1));
            }
        }

        @Override
        public int timeoutTicks() {
            return 1;
        }

        @Override
        public String describe() {
            return "use the hut's stock";
        }
    }

    // Collects the drops lying around the felled tree (logs, saplings, sticks, apples: whatever it
    // wants and has room for), walking over to each so vanilla's pickup takes it. Done when there's
    // nothing left to collect nearby, or after COLLECT_TICKS. (Leaves decay slowly after the logs are
    // gone; what they drop later is picked up as it passes, or on a later visit.)
    private final class CollectStep implements JobStep {
        private static final int RETARGET_TICKS = 10;
        private BlockPos center = BlockPos.ZERO;
        private int ticks;

        @Override
        public void start(Unit unit) {
            center = tree != null ? tree.base() : unit.blockPosition();
            ticks = 0;
        }

        @Override
        public Status tick(Unit unit) {
            if (ticks++ % RETARGET_TICKS != 0) {
                return Status.RUNNING;
            }
            AABB area = new AABB(center).inflate(COLLECT_RADIUS, 4, COLLECT_RADIUS);
            List<ItemEntity> drops = unit.level().getEntitiesOfClass(ItemEntity.class, area,
                    item -> item.isAlive() && !item.getItem().isEmpty() && unit.wantsToPickUp(item.getItem()));
            if (drops.isEmpty()) {
                return Status.DONE;
            }
            ItemEntity nearest = drops.stream().min(Comparator.comparingDouble(unit::distanceToSqr)).get();
            unit.getNavigation().moveTo(nearest, 1.0);
            return Status.RUNNING;
        }

        @Override
        public void stop(Unit unit) {
            unit.getNavigation().stop();
        }

        @Override
        public int timeoutTicks() {
            return COLLECT_TICKS;
        }

        @Override
        public String describe() {
            return "collect the drops";
        }
    }

    // Clears the felled tree's canopy, the way vanilla's leaf decay would, only sooner. Vanilla keeps
    // each natural leaf's distance to the nearest log, and a leaf with no log within 6 (distance 7)
    // decays on a random tick, which can take minutes. So: wait a few ticks for vanilla to update the
    // distances after the last log went, then decay every such leaf around the tree, with the drops
    // its loot table gives (saplings, sticks, apples) going into the inventory.
    //
    // Leaves that are still within 6 of another tree's log keep a smaller distance and stay, so a
    // dense forest whose canopies touch is handled by vanilla's own rule, with no search through the
    // forest. The work is bounded: only the felled tree's bounds plus vanilla's 6-block leaf range are
    // scanned, a batch per tick, and a few leaves are removed per tick so the canopy falls away
    // visibly rather than vanishing.
    private final class ClearCanopyStep implements JobStep {
        private static final int LEAF_SETTLE_TICKS = 10;
        private static final int SCANNED_PER_TICK = 512;
        private static final int CLEARED_PER_TICK = 4;

        private final ArrayDeque<BlockPos> toClear = new ArrayDeque<>();
        @Nullable
        private BoundingBox box;
        private int settle;
        private long scanned;
        private long volume;

        @Override
        public void start(Unit unit) {
            box = tree != null ? TreeRules.canopyBox(tree.logs(), LeavesBlock.DECAY_DISTANCE - 1) : null;
            volume = box != null ? (long) box.getXSpan() * box.getYSpan() * box.getZSpan() : 0;
            settle = LEAF_SETTLE_TICKS;
            scanned = 0;
            toClear.clear();
        }

        @Override
        public Status tick(Unit unit) {
            if (box == null) {
                return Status.DONE;
            }
            if (settle > 0) {
                settle--;
                return Status.RUNNING;
            }
            ServerLevel level = (ServerLevel) unit.level();
            for (int i = 0; i < SCANNED_PER_TICK && scanned < volume; i++, scanned++) {
                BlockPos pos = posAt(scanned);
                if (level.isLoaded(pos) && isOrphanedLeaf(level.getBlockState(pos))) {
                    toClear.add(pos);
                }
            }
            for (int i = 0; i < CLEARED_PER_TICK && !toClear.isEmpty(); i++) {
                clear(level, unit, toClear.poll());
            }
            return scanned >= volume && toClear.isEmpty() ? Status.DONE : Status.RUNNING;
        }

        // The index-th block of the box, so the scan can resume where it stopped last tick.
        private BlockPos posAt(long index) {
            int x = (int) (index % box.getXSpan());
            int z = (int) ((index / box.getXSpan()) % box.getZSpan());
            int y = (int) (index / ((long) box.getXSpan() * box.getZSpan()));
            return new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z);
        }

        private void clear(ServerLevel level, Unit unit, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (!isOrphanedLeaf(state)) {
                return; // changed since it was found
            }
            List<ItemStack> drops = Block.getDrops(state, level, pos, null); // what decaying would drop
            level.destroyBlock(pos, false); // particles and sound, no item entities
            for (ItemStack drop : drops) {
                ItemStack left = unit.getInventory().addItem(drop);
                if (!left.isEmpty()) {
                    unit.spawnAtLocation(left);
                }
            }
        }

        @Override
        public int timeoutTicks() {
            return 20 * 30;
        }

        @Override
        public String describe() {
            return "clear the canopy";
        }
    }
}
