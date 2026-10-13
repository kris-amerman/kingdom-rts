package com.krisamerman.kingdomrts.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

// Workplace operations, as actions of a faction (like SettlementOps): recording a workplace on its
// settlement, hiring and firing, and keeping units in step when a workplace or settlement goes away.
// A unit gets a job (a non-military occupation) only by being hired here.
public final class WorkplaceOps {
    private WorkplaceOps() {
    }

    // Records the workplace block at pos on the settlement whose claim it stands in (and marks its
    // stock as that settlement's). Returns the workplace, or null if no settlement claims the spot
    // (then the block is just a chest) or there's no WorkplaceType entry for the block.
    @Nullable
    public static Workplace register(ServerLevel level, BlockPos pos) {
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(pos));
        if (level.getBlockEntity(pos) instanceof WorkplaceBlockEntity stock) {
            stock.setSettlement(settlement != null ? settlement.id() : null);
        }
        if (settlement == null) {
            return null;
        }
        ResourceLocation type = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        Optional<WorkplaceType> entry = WorkplaceType.get(level, type);
        if (entry.isEmpty()) {
            KingdomRts.LOGGER.warn("No workplace entry '{}'; the block at {} won't employ anyone", type, pos.toShortString());
            return null;
        }
        return SettlementData.get(level).addWorkplace(settlement, pos, type, entry.get().workRadius());
    }

    // The workplace block was removed: its workers lose their jobs (loaded ones now, unloaded ones
    // when they next load, see catchUp) and the settlement forgets it.
    public static void unregister(ServerLevel level, Settlement settlement, BlockPos pos) {
        Workplace workplace = settlement.workplaceAt(pos);
        if (workplace == null) {
            return;
        }
        SettlementData.get(level).removeWorkplace(settlement, pos);
        for (UUID id : List.copyOf(workplace.workers().keySet())) {
            if (level.getEntity(id) instanceof Unit unit) {
                unemploy(unit);
            }
        }
    }

    // The workplace at pos, if any.
    @Nullable
    public static Workplace workplaceAt(ServerLevel level, BlockPos pos) {
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(pos));
        return settlement != null ? settlement.workplaceAt(pos) : null;
    }

    // Hires a unit at the workplace at pos, giving it the workplace's occupation. Returns why it
    // can't be hired, or null on success.
    @Nullable
    public static String hire(ServerLevel level, Unit unit, BlockPos pos) {
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(pos));
        Workplace workplace = settlement != null ? settlement.workplaceAt(pos) : null;
        if (workplace == null) {
            return "There's no workplace at " + pos.toShortString();
        }
        Optional<WorkplaceType> type = WorkplaceType.get(level, workplace.type());
        if (type.isEmpty()) {
            return "Unknown workplace: " + workplace.type();
        }
        String unitName = unit.getName().getString();
        String problem = WorkplaceRules.hireProblem(unitName, unit.getSettlementId(), settlement.id(), settlement.name(),
                unit.getOccupationId() != null, workplace.workers().size(), type.get().workers(), name(workplace));
        if (problem != null) {
            return problem;
        }
        SettlementData.get(level).addWorker(workplace, unit.getUUID(), unitName);
        unit.setWorkplace(pos);
        unit.setOccupation(type.get().occupation());
        return null;
    }

    // Fires a unit from its workplace: it loses its job. Returns false if it had no workplace.
    public static boolean fire(ServerLevel level, Unit unit) {
        if (unit.getWorkplace() == null) {
            return false;
        }
        Workplace workplace = workplaceOf(level, unit);
        if (workplace != null) {
            SettlementData.get(level).removeWorker(workplace, unit.getUUID());
        }
        unemploy(unit);
        return true;
    }

    // Fires a worker by id, whether or not it's loaded: an unloaded worker loses its job when it
    // next loads (see catchUp), since the workplace no longer lists it.
    public static void fire(ServerLevel level, Workplace workplace, UUID worker) {
        if (level.getEntity(worker) instanceof Unit unit && fire(level, unit)) {
            return;
        }
        SettlementData.get(level).removeWorker(workplace, worker);
    }

    // A unit died or was discarded: its workplace forgets it.
    public static void unitRemoved(ServerLevel level, Unit unit) {
        Workplace workplace = workplaceOf(level, unit);
        if (workplace != null) {
            SettlementData.get(level).removeWorker(workplace, unit.getUUID());
        }
    }

    // Brings a unit up to date: if its workplace no longer lists it (the workplace was removed, or
    // its settlement dissolved, while it was unloaded), it loses its job.
    public static void catchUp(ServerLevel level, Unit unit) {
        if (unit.getWorkplace() == null) {
            return;
        }
        Workplace workplace = workplaceOf(level, unit);
        if (workplace == null || !workplace.workers().containsKey(unit.getUUID())) {
            unemploy(unit);
        }
    }

    // Sets how far from the workplace its workers work, kept within WorkArea.MIN_RADIUS and the
    // workplace type's maximum. Returns the radius it ended up with.
    public static int setWorkRadius(ServerLevel level, Workplace workplace, int radius) {
        int max = WorkplaceType.get(level, workplace.type()).map(WorkplaceType::maxWorkRadius).orElse(WorkplaceType.DEFAULT_MAX_WORK_RADIUS);
        int clamped = WorkArea.clampRadius(radius, max);
        SettlementData.get(level).setWorkRadius(workplace, clamped);
        return clamped;
    }

    // The workplace a unit is employed at, if any.
    @Nullable
    public static Workplace workplaceOf(ServerLevel level, Unit unit) {
        Settlement settlement = SettlementData.get(level).byId(unit.getSettlementId());
        return settlement != null && unit.getWorkplace() != null ? settlement.workplaceAt(unit.getWorkplace()) : null;
    }

    // Turns a workplace's automatic hiring on or off.
    public static void setAutoHire(ServerLevel level, Workplace workplace, boolean autoHire) {
        SettlementData.get(level).setAutoHire(workplace, autoHire);
    }

    // An unemployed settler looks for work: it's hired at the nearest workplace of its settlement
    // that has room and hires automatically, if there is one. Cheap: it only looks at its own
    // settlement's few workplaces. Returns whether it found a job.
    public static boolean seekJob(ServerLevel level, Unit unit) {
        if (unit.getOccupationId() != null) {
            return false; // employed, or a soldier
        }
        Settlement settlement = SettlementData.get(level).byId(unit.getSettlementId());
        if (settlement == null || !settlement.dimension().equals(level.dimension())) {
            return false;
        }
        List<WorkplaceRules.Opening> openings = new ArrayList<>();
        for (Workplace workplace : settlement.workplaces()) {
            WorkplaceType.get(level, workplace.type()).ifPresent(type -> openings.add(
                    new WorkplaceRules.Opening(workplace.pos(), workplace.workers().size(), type.workers(), workplace.autoHire())));
        }
        BlockPos chosen = WorkplaceRules.chooseWorkplace(openings, unit.blockPosition());
        return chosen != null && hire(level, unit, chosen) == null;
    }

    // How a workplace is named in messages: "lumberjack hut" for kingdomrts:lumberjack_hut.
    public static String name(Workplace workplace) {
        return workplace.type().getPath().replace('_', ' ');
    }

    private static void unemploy(Unit unit) {
        unit.setWorkplace(null);
        unit.setOccupation(null);
    }
}
