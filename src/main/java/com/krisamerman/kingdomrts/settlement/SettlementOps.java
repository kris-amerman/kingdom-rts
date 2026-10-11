package com.krisamerman.kingdomrts.settlement;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.faction.Faction;

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

    // Passes a settlement, with its claim, to a new owning faction (on capture).
    public static void transfer(ServerLevel level, Settlement settlement, UUID newOwner) {
        SettlementData.get(level).setOwner(settlement, newOwner);
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

    // Removes a settlement and releases its claim (its town hall is gone).
    public static void dissolve(ServerLevel level, Settlement settlement) {
        SettlementData.get(level).dissolve(settlement);
    }
}
