package com.krisamerman.kingdomrts.settlement;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingRules;
import com.krisamerman.kingdomrts.building.BuildingTemplates;
import com.krisamerman.kingdomrts.building.BuildingType;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

// Building operations, as actions of a faction (like WorkplaceOps): commissioning a building at a
// site, seeing what's left to do there, and cancelling it. The build tool and AI factions will both
// go through these. Builders (S-011) will do the actual building; finish() is a cheat for testing.
public final class BuildingOps {
    private BuildingOps() {
    }

    public record CommissionResult(@Nullable Building building, @Nullable String problem) {
    }

    public record RemainingResult(@Nullable BuildingRules.Remaining remaining, @Nullable String problem) {
    }

    // Commissions a building of the given type for a faction, with its template's corner at origin,
    // turned by rotation. Refused unless it fits inside one settlement's claim owned by the faction,
    // stays within build height, and doesn't overlap another building, the town hall, a workplace
    // or a storage block.
    public static CommissionResult commission(ServerLevel level, Faction faction, ResourceLocation typeId, BlockPos origin, Rotation rotation) {
        Optional<BuildingType> type = BuildingType.get(level, typeId);
        if (type.isEmpty()) {
            return refused("Unknown building: " + typeId);
        }
        Optional<Blueprint> blueprint = BuildingTemplates.blueprint(level, type.get());
        if (blueprint.isEmpty()) {
            return refused("The building " + typeId + " has no usable structure (" + type.get().structure() + ")");
        }
        BoundingBox footprint = BuildingRules.footprint(blueprint.get().size(), origin, rotation);
        if (level.isOutsideBuildHeight(footprint.minY()) || level.isOutsideBuildHeight(footprint.maxY())) {
            return refused("It doesn't fit between the world's floor and ceiling");
        }
        SettlementData data = SettlementData.get(level);
        Settlement settlement = data.claimAt(level.dimension(), new ChunkPos(origin));
        if (settlement == null) {
            return refused("That isn't in a settlement's claim");
        }
        if (!settlement.owner().equals(faction.id())) {
            Faction owner = FactionData.get(level).byId(settlement.owner());
            return refused("That's " + (owner != null ? owner.name() : "another faction") + "'s claim, not " + faction.name() + "'s");
        }
        String problem = siteProblem(settlement, footprint);
        if (problem != null) {
            return refused(problem);
        }
        Building building = new Building(typeId, origin, rotation, footprint, level.getGameTime());
        data.addBuilding(settlement, building);
        return new CommissionResult(building, null);
    }

    // Why a footprint can't be built on in this settlement, or null if it can.
    @Nullable
    private static String siteProblem(Settlement settlement, BoundingBox footprint) {
        for (int x = footprint.minX() >> 4; x <= footprint.maxX() >> 4; x++) {
            for (int z = footprint.minZ() >> 4; z <= footprint.maxZ() >> 4; z++) {
                if (!settlement.chunks().contains(new ChunkPos(x, z))) {
                    return "It doesn't fit inside " + settlement.name() + "'s claim";
                }
            }
        }
        for (Building other : settlement.buildings()) {
            if (other.footprint().intersects(footprint)) {
                return "It overlaps the " + other.type().getPath().replace('_', ' ') + " at " + other.origin().toShortString();
            }
        }
        if (footprint.isInside(settlement.townHall())) {
            return "It would cover the town hall";
        }
        for (Workplace workplace : settlement.workplaces()) {
            if (footprint.isInside(workplace.pos())) {
                return "It would cover the " + WorkplaceOps.name(workplace) + " at " + workplace.pos().toShortString();
            }
        }
        for (BlockPos storage : settlement.storage()) {
            if (footprint.isInside(storage)) {
                return "It would cover the storage at " + storage.toShortString();
            }
        }
        return null;
    }

    private static CommissionResult refused(String problem) {
        return new CommissionResult(null, problem);
    }

    // What's left to do at a building: blocks missing, blocks in the way, and the materials needed.
    // Never loads chunks: if the site isn't loaded, it says so.
    public static RemainingResult remaining(ServerLevel level, Building building) {
        List<Blueprint.Part> placed = placed(level, building);
        if (placed == null) {
            return new RemainingResult(null, "The building " + building.type() + " has no usable structure");
        }
        BoundingBox box = building.footprint();
        if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            return new RemainingResult(null, "The site isn't loaded");
        }
        return new RemainingResult(BuildingRules.remaining(placed, level::getBlockState), null);
    }

    // The building's blueprint where it goes at its site, or null if its template is gone.
    @Nullable
    public static List<Blueprint.Part> placed(ServerLevel level, Building building) {
        return BuildingType.get(level, building.type())
                .flatMap(type -> BuildingTemplates.blueprint(level, type))
                .map(blueprint -> BuildingRules.place(blueprint, building.origin(), building.rotation()))
                .orElse(null);
    }

    // Cancels a commission: the settlement forgets the site. Blocks already placed stay.
    public static void cancel(ServerLevel level, Settlement settlement, Building building) {
        SettlementData.get(level).removeBuilding(settlement, building.origin());
    }

    // A cheat for testing (an op command, see BuildingCommands): clears what's in the way and puts
    // every missing block in place at once, with no builder and no materials. Blocks are set without
    // shape updates while placing, so a door's lower half doesn't pop off before its upper half is
    // there, then neighbours are told about them. Returns how many blocks it set, or -1 if the
    // site can't be finished (no template, or not loaded).
    public static int finish(ServerLevel level, Building building) {
        RemainingResult result = remaining(level, building);
        if (result.remaining() == null) {
            return -1;
        }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        int changed = 0;
        for (BlockPos pos : result.remaining().obstructions()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
            changed++;
        }
        for (Blueprint.Part part : result.remaining().missing()) {
            level.setBlock(part.pos(), part.state(), flags);
            changed++;
        }
        for (Blueprint.Part part : result.remaining().missing()) {
            level.blockUpdated(part.pos(), part.state().getBlock());
        }
        return changed;
    }
}
