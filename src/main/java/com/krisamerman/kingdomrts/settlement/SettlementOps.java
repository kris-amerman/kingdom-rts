package com.krisamerman.kingdomrts.settlement;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

// Settlement operations, as actions of a faction rather than of a player: the town hall's Claim
// button and the /kingdomrts settlement commands both go through here, and AI factions will too.
// These check the rules; SettlementData just stores the result.
public final class SettlementOps {
    // The starting claim: chunks within this distance of the town hall's chunk, so 1 = 3x3 chunks.
    // Tunable; how much space a settlement needs is still an open design question.
    public static final int STARTING_CLAIM_RADIUS = 1;

    private SettlementOps() {
    }

    // The outcome of founding: the new settlement, or why it couldn't be founded.
    public record FoundResult(@Nullable Settlement settlement, @Nullable String problem) {
        static FoundResult failed(String problem) {
            return new FoundResult(null, problem);
        }
    }

    // The chunks a settlement founded at this town hall would claim.
    public static Set<ChunkPos> startingClaim(BlockPos townHall) {
        ChunkPos center = new ChunkPos(townHall);
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (int dx = -STARTING_CLAIM_RADIUS; dx <= STARTING_CLAIM_RADIUS; dx++) {
            for (int dz = -STARTING_CLAIM_RADIUS; dz <= STARTING_CLAIM_RADIUS; dz++) {
                chunks.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        return chunks;
    }

    // Why a settlement can't be founded at this town hall right now, or null if it can: the block
    // must be an unclaimed town hall, and none of its starting claim may belong to another
    // settlement (including one of the same faction).
    @Nullable
    public static String foundingProblem(ServerLevel level, BlockPos townHallPos) {
        if (!(level.getBlockEntity(townHallPos) instanceof TownHallBlockEntity townHall)) {
            return "No town hall at " + townHallPos.toShortString();
        }
        if (townHall.getSettlementId() != null) {
            return "This town hall is already claimed";
        }
        SettlementData settlements = SettlementData.get(level);
        for (ChunkPos chunk : startingClaim(townHallPos)) {
            Settlement other = settlements.claimAt(level.dimension(), chunk);
            if (other != null) {
                return "Too close to " + other.name() + ": a settlement here would overlap its claim";
            }
        }
        return null;
    }

    // Founds a settlement for a faction at an unclaimed town hall: claims the starting chunks,
    // names it, and gives the hall full control.
    public static FoundResult found(ServerLevel level, BlockPos townHallPos, Faction faction) {
        String problem = foundingProblem(level, townHallPos);
        if (problem != null) {
            return FoundResult.failed(problem);
        }
        SettlementData settlements = SettlementData.get(level);
        Settlement settlement = settlements.create(SettlementNames.pick(settlements), faction.id(),
                level.dimension(), townHallPos, startingClaim(townHallPos));
        ((TownHallBlockEntity) level.getBlockEntity(townHallPos)).settlementFounded(settlement.id());
        return new FoundResult(settlement, null);
    }

    // Passes a settlement, with its claim, to a new owning faction (on capture). Its units that
    // aren't military pass to the new owner too: loaded ones now, unloaded ones when they next load.
    public static void transfer(ServerLevel level, Settlement settlement, UUID newOwner) {
        SettlementData.get(level).setOwner(settlement, newOwner);
        forEachLoadedUnit(level, settlement, unit -> catchUp(level, unit));
    }

    // ---- Units ----

    // Makes a unit belong to a settlement, or to none (null), keeping the unit and the settlement's
    // unit list in step. Faction and settlement are independent, so this doesn't change its faction.
    public static void setUnitSettlement(ServerLevel level, Unit unit, @Nullable Settlement settlement) {
        SettlementData settlements = SettlementData.get(level);
        if (settlement == null || !settlement.id().equals(unit.getSettlementId())) {
            WorkplaceOps.fire(level, unit); // its workplace belongs to the settlement it's leaving
        }
        Settlement old = settlements.byId(unit.getSettlementId());
        if (old != null) {
            settlements.removeUnit(old, unit.getUUID());
        }
        if (settlement != null) {
            settlements.addUnit(settlement, unit.getUUID(), Resident.of(unit));
            unit.setSettlement(settlement.id(), settlement.ownerChanges());
        } else {
            unit.setSettlement(null, 0);
        }
    }

    // A unit died or was discarded: its settlement (and workplace) forget it.
    public static void unitRemoved(ServerLevel level, Unit unit) {
        WorkplaceOps.unitRemoved(level, unit);
        SettlementData settlements = SettlementData.get(level);
        Settlement settlement = settlements.byId(unit.getSettlementId());
        if (settlement != null) {
            settlements.removeUnit(settlement, unit.getUUID());
        }
    }

    // Brings a unit up to date with its settlement: if the settlement changed owner since the unit
    // last checked, a non-military unit passes to the current owner; if the settlement is gone, the
    // unit no longer belongs to one. Runs on capture for loaded units and when a unit loads.
    // Also catches up with its workplace (see WorkplaceOps#catchUp).
    public static void catchUp(ServerLevel level, Unit unit) {
        if (unit.getSettlementId() != null) {
            Settlement settlement = SettlementData.get(level).byId(unit.getSettlementId());
            if (settlement == null) {
                unit.setSettlement(null, 0); // dissolved while the unit was away
            } else if (unit.getSeenOwnerChanges() != settlement.ownerChanges()) {
                unit.setFactionId(CaptureRules.factionAfterCapture(unit.getFactionId(), unit.isMilitary(), settlement.owner()));
                unit.setSettlement(settlement.id(), settlement.ownerChanges());
            }
        }
        WorkplaceOps.catchUp(level, unit);
        refreshResident(level, unit);
    }

    // Updates what the unit's settlement remembers about it (name and occupation), so the
    // town hall can list it while it's unloaded. Called when it's hired or fired and when it loads.
    public static void refreshResident(ServerLevel level, Unit unit) {
        SettlementData settlements = SettlementData.get(level);
        Settlement settlement = settlements.byId(unit.getSettlementId());
        if (settlement != null) {
            settlements.updateResident(settlement, unit.getUUID(), Resident.of(unit));
        }
    }

    // Runs action for each of the settlement's units that is currently loaded.
    private static void forEachLoadedUnit(ServerLevel anyLevel, Settlement settlement, Consumer<Unit> action) {
        ServerLevel level = anyLevel.getServer().getLevel(settlement.dimension());
        if (level == null) {
            return;
        }
        for (UUID id : List.copyOf(settlement.units())) {
            if (level.getEntity(id) instanceof Unit unit) {
                action.accept(unit);
            }
        }
    }

    // Renames a settlement. Returns why the name can't be used, or null on success.
    @Nullable
    public static String rename(ServerLevel level, Settlement settlement, String newName) {
        SettlementData settlements = SettlementData.get(level);
        String problem = settlements.nameProblem(newName);
        if (problem != null) {
            return problem;
        }
        settlements.rename(settlement, newName);
        return null;
    }

    // Why this faction can't put settlement storage here, or null if it can: storage may only go
    // inside a claim owned by that faction.
    @Nullable
    public static String storagePlacementProblem(ServerLevel level, BlockPos pos, @Nullable UUID faction) {
        return ownClaimProblem(level, pos, faction, "storage");
    }

    // Why this faction can't put a settlement's block (storage, a workplace) here, or null if it
    // can: it may only go inside a claim owned by that faction. noun is what to call it ("storage").
    @Nullable
    public static String ownClaimProblem(ServerLevel level, BlockPos pos, @Nullable UUID faction, String noun) {
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(pos));
        if (settlement == null) {
            return "A " + noun + " can only be placed inside your own settlement's claim";
        }
        if (faction == null || !faction.equals(settlement.owner())) {
            return "This land belongs to " + settlement.name() + "; a " + noun + " can only go in your own claim";
        }
        return null;
    }

    // Makes the storage block at pos belong to the settlement whose claim it stands in (recorded on
    // both the settlement and the block). Returns that settlement, or null if no settlement claims
    // the spot, in which case the block is just an ordinary chest.
    @Nullable
    public static Settlement registerStorage(ServerLevel level, BlockPos pos) {
        SettlementData settlements = SettlementData.get(level);
        Settlement settlement = settlements.claimAt(level.dimension(), new ChunkPos(pos));
        if (settlement != null) {
            settlements.addStorage(settlement, pos);
        }
        if (level.getBlockEntity(pos) instanceof SettlementStorageBlockEntity storage) {
            storage.setSettlement(settlement != null ? settlement.id() : null);
        }
        return settlement;
    }

    // Forgets a storage block (it was broken).
    public static void unregisterStorage(ServerLevel level, Settlement settlement, BlockPos pos) {
        SettlementData.get(level).removeStorage(settlement, pos);
    }

    // Removes a settlement and releases its claim (its town hall is gone). Its units no longer
    // belong to a settlement: loaded ones now, unloaded ones when they next load.
    public static void dissolve(ServerLevel level, Settlement settlement) {
        SettlementData.get(level).dissolve(settlement);
        forEachLoadedUnit(level, settlement, unit -> catchUp(level, unit)); // no settlement, no workplace
    }
}
