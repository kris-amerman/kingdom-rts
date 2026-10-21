package com.krisamerman.kingdomrts.gametest;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.SettlementStorageBlockEntity;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Stock;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

// The stress test's world (see StressGameTests and docs/perf.md): a grid of settlements, each a
// small working town of UNITS_PER_SETTLEMENT units: lumberjacks working from their huts in a forest
// of small oaks, a soldier at the hall, and a builder building a storage shed, fetching its materials
// from a settlement storage by the hall. Everything is
// placed the same way every run, so runs can be compared. The settlements' chunks are kept loaded
// (forced) so they tick like chunks near a player.
final class StressScenario {
    static final int UNITS_PER_SETTLEMENT = 10;
    static final int LUMBERJACKS_PER_SETTLEMENT = 8;
    static final int TREE_HEIGHT = 4;
    // A settlement's starting claim is 3x3 chunks; settlements sit side by side, one claim apart.
    private static final int CLAIM_CHUNKS = 2 * SettlementOps.STARTING_CLAIM_RADIUS + 1;
    private static final int TREE_SPACING = 4;
    // No trees this close to the hall, where the huts are.
    private static final int CLEARING = 8;
    // Where the huts go around the hall, and where each lumberjack starts (next to its hut).
    private static final int[][] HUTS = {{-5, -5}, {0, -5}, {5, -5}, {-5, 0}, {5, 0}, {-5, 5}, {0, 5}, {5, 5}};
    // Where each town's shed goes (its template corner, from the hall), outside the clearing.
    private static final int[] SHED_OFFSET = {10, -2};
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");

    private final ServerLevel level;
    private final List<BlockPos> halls = new ArrayList<>();
    private final List<BlockPos> huts = new ArrayList<>();
    private final List<Unit> units = new ArrayList<>();
    private final List<Unit> lumberjacks = new ArrayList<>();
    private final List<ChunkPos> forced = new ArrayList<>();
    private final List<BlockPos> sheds = new ArrayList<>();

    private StressScenario(ServerLevel level) {
        this.level = level;
    }

    // Builds settlements for the given number of units (a multiple of UNITS_PER_SETTLEMENT), on a
    // floor at floorY, starting from the chunk at origin.
    static StressScenario build(ServerLevel level, Faction faction, ChunkPos origin, int floorY, int unitCount) {
        StressScenario scenario = new StressScenario(level);
        int settlements = unitCount / UNITS_PER_SETTLEMENT;
        int columns = (int) Math.ceil(Math.sqrt(settlements));
        for (int i = 0; i < settlements; i++) {
            ChunkPos corner = new ChunkPos(origin.x + (i % columns) * CLAIM_CHUNKS, origin.z + (i / columns) * CLAIM_CHUNKS);
            scenario.buildSettlement(faction, corner, floorY);
        }
        return scenario;
    }

    private void buildSettlement(Faction faction, ChunkPos corner, int floorY) {
        int minX = corner.getMinBlockX();
        int minZ = corner.getMinBlockZ();
        int size = CLAIM_CHUNKS * 16;
        for (int cx = 0; cx < CLAIM_CHUNKS; cx++) {
            for (int cz = 0; cz < CLAIM_CHUNKS; cz++) {
                ChunkPos chunk = new ChunkPos(corner.x + cx, corner.z + cz);
                level.setChunkForced(chunk.x, chunk.z, true);
                forced.add(chunk);
            }
        }
        // A grass floor with open air above it.
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                level.setBlock(pos.set(minX + x, floorY, minZ + z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 1; y <= TREE_HEIGHT + 3; y++) {
                    level.setBlock(pos.set(minX + x, floorY + y, minZ + z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        BlockPos hall = new BlockPos(minX + size / 2, floorY + 1, minZ + size / 2);
        level.setBlockAndUpdate(hall, KingdomRts.TOWN_HALL.get().defaultBlockState());
        SettlementOps.FoundResult founded = SettlementOps.found(level, hall, faction);
        Settlement settlement = founded.settlement();
        if (settlement == null) {
            throw new IllegalStateException("Couldn't found a stress settlement: " + founded.problem());
        }
        halls.add(hall);
        // The shed's ground is kept clear of trees, so it's built the same way every run.
        BlockPos shedOrigin = hall.offset(SHED_OFFSET[0], 0, SHED_OFFSET[1]);
        BoundingBox shedGround = new BoundingBox(shedOrigin.getX() - 3, floorY, shedOrigin.getZ() - 3, shedOrigin.getX() + 7, floorY + 8, shedOrigin.getZ() + 7);
        for (int x = TREE_SPACING / 2; x < size - 1; x += TREE_SPACING) {
            for (int z = TREE_SPACING / 2; z < size - 1; z += TREE_SPACING) {
                BlockPos base = new BlockPos(minX + x, floorY + 1, minZ + z);
                if ((Math.abs(base.getX() - hall.getX()) > CLEARING || Math.abs(base.getZ() - hall.getZ()) > CLEARING) && !shedGround.isInside(base)) {
                    tree(base);
                }
            }
        }
        for (int i = 0; i < LUMBERJACKS_PER_SETTLEMENT; i++) {
            BlockPos hut = hall.offset(HUTS[i][0], 0, HUTS[i][1]);
            level.setBlockAndUpdate(hut, KingdomRts.LUMBERJACK_HUT.get().defaultBlockState());
            if (WorkplaceOps.register(level, hut) == null) {
                throw new IllegalStateException("Couldn't register a stress hut at " + hut.toShortString());
            }
            huts.add(hut);
            Unit lumberjack = spawn(faction, settlement, hut.offset(Integer.signum(HUTS[i][0]), 0, 1), null);
            String problem = WorkplaceOps.hire(level, lumberjack, hut);
            if (problem != null) {
                throw new IllegalStateException("Couldn't hire a stress lumberjack: " + problem);
            }
            lumberjacks.add(lumberjack);
        }
        spawn(faction, settlement, hall.offset(2, 0, 0), Occupation.SOLDIER); // stands guard at the hall
        // A builder on a storage shed, fetching what it needs from a settlement storage by the hall
        // that holds everything the shed takes.
        BuildingOps.CommissionResult shed = BuildingOps.commission(level, faction, SHED, shedOrigin, Rotation.NONE);
        if (shed.building() == null) {
            throw new IllegalStateException("Couldn't commission a stress shed: " + shed.problem());
        }
        sheds.add(shed.building().origin());
        BlockPos storagePos = hall.offset(0, 0, 2);
        level.setBlockAndUpdate(storagePos, KingdomRts.SETTLEMENT_STORAGE.get().defaultBlockState());
        if (SettlementOps.registerStorage(level, storagePos) == null || !(level.getBlockEntity(storagePos) instanceof SettlementStorageBlockEntity storage)) {
            throw new IllegalStateException("Couldn't place a stress storage at " + storagePos.toShortString());
        }
        BuildingOps.remaining(level, shed.building()).remaining().materials().forEach((item, count) -> Stock.insert(storage, new ItemStack(item, count)));
        Unit builder = spawn(faction, settlement, hall.offset(-2, 0, 0), null);
        String problem = BuilderOps.hire(level, settlement, builder);
        problem = problem != null ? problem : BuilderOps.assign(level, settlement, shed.building(), builder.getUUID());
        if (problem != null) {
            throw new IllegalStateException("Couldn't set up a stress builder: " + problem);
        }
    }

    // How many of the towns' sheds are built.
    int shedsBuilt() {
        SettlementData data = SettlementData.get(level);
        int built = 0;
        for (BlockPos origin : sheds) {
            Settlement settlement = data.claimAt(level.dimension(), new ChunkPos(origin));
            Building building = settlement != null ? settlement.buildingAt(origin) : null;
            if (building != null && building.isBuilt()) {
                built++;
            }
        }
        return built;
    }

    // A small natural oak, the same as LumberjackGameTests': dirt under a trunk, leaves around its
    // top two logs and on top.
    private void tree(BlockPos base) {
        level.setBlock(base.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int y = 0; y < TREE_HEIGHT; y++) {
            level.setBlock(base.above(y), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int y = TREE_HEIGHT - 2; y < TREE_HEIGHT; y++) {
            BlockPos log = base.above(y);
            for (BlockPos leaf : new BlockPos[] {log.north(), log.south(), log.east(), log.west()}) {
                level.setBlock(leaf, leaves, Block.UPDATE_CLIENTS);
            }
        }
        level.setBlock(base.above(TREE_HEIGHT), leaves, Block.UPDATE_CLIENTS);
    }

    private Unit spawn(Faction faction, Settlement settlement, BlockPos pos, @Nullable ResourceLocation occupation) {
        Unit unit = KingdomRts.UNIT.get().create(level);
        if (unit == null) {
            throw new IllegalStateException("Couldn't create a unit");
        }
        unit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        unit.setup(faction.id(), pos, occupation);
        level.addFreshEntity(unit);
        SettlementOps.setUnitSettlement(level, unit, settlement);
        units.add(unit);
        return unit;
    }

    int unitCount() {
        return units.size();
    }

    int lumberjackCount() {
        return lumberjacks.size();
    }

    // Logs the lumberjacks have collected: carried, or already in their huts' stocks.
    int logsCollected() {
        int logs = 0;
        for (Unit lumberjack : lumberjacks) {
            logs += lumberjack.getInventory().countItem(Items.OAK_LOG);
        }
        for (BlockPos hut : huts) {
            if (level.getBlockEntity(hut) instanceof Container stock) {
                logs += stock.countItem(Items.OAK_LOG);
            }
        }
        return logs;
    }

    // Removes the units and settlements and stops forcing the chunks. The blocks are left: each
    // size is built in its own place.
    void remove() {
        for (Unit unit : units) {
            unit.remove(Entity.RemovalReason.DISCARDED);
        }
        for (BlockPos hall : halls) {
            level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState()); // dissolves its settlement
        }
        for (ChunkPos chunk : forced) {
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
}
