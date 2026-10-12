package com.krisamerman.kingdomrts.unit;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

// The lumberjack's job, as explicit steps:
//   find a tree in the settlement's claim -> walk next to it -> fell it (logs go into the inventory)
//   -> repeat.
// When something goes wrong it recovers instead of stalling: a tree it can't reach is skipped for a
// while, a tree that disappears on the way is simply replaced by the next one, and with no
// settlement, no trees or no room for logs it waits and checks again later.
// (Taking the logs to settlement storage comes next, in S-014.)
public final class LumberjackJob implements Job {
    // Claim columns checked per tick while searching, so a search is spread over several ticks.
    private static final int COLUMNS_PER_TICK = 128;
    // Candidate trees checked (with a flood fill) per tick once the columns are scanned.
    private static final int TREES_CHECKED_PER_TICK = 4;
    private static final double REACH = 3.0;
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

    public LumberjackJob(LumberjackOccupation settings) {
        this.settings = settings;
    }

    @Override
    public JobStep next(Unit unit, @Nullable JobStep previous, boolean succeeded) {
        if (previous instanceof WalkToStep && !succeeded && tree != null && treeStillThere(unit)) {
            KingdomRts.LOGGER.debug("Lumberjack {} couldn't reach the tree at {}; skipping it for a while", unit.getUUID(), tree.base().toShortString());
            skipUntil.put(tree.base(), unit.level().getGameTime() + SKIP_FAILED_TREE_TICKS); // unreachable
        }
        if (settlementOf(unit) == null) {
            return new WaitStep(IDLE_TICKS, "no settlement");
        }
        if (!hasRoomForLogs(unit.getInventory())) {
            return new WaitStep(IDLE_TICKS, "no room for logs");
        }
        if (previous instanceof FindTreeStep) {
            if (!succeeded || tree == null) {
                return new WaitStep(NO_TREE_TICKS, "no trees in the claim");
            }
            return WalkToStep.near(tree.base(), WALK_TIMEOUT_TICKS, () -> treeStillThere(unit));
        }
        if (previous instanceof WalkToStep && succeeded) {
            return new ChopStep();
        }
        if (previous instanceof WalkToStep || (previous instanceof ChopStep && !succeeded)) {
            return new WaitStep(RETRY_TICKS, "retrying");
        }
        return new FindTreeStep();
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

    // Finds the nearest tree in the claim. First it scans the claim's columns, a batch per tick:
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

        @Override
        public void start(Unit unit) {
            tree = null;
            from = unit.blockPosition();
            Settlement settlement = settlementOf(unit);
            if (settlement != null) {
                chunks.addAll(settlement.chunks());
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
}
