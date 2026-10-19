package com.krisamerman.kingdomrts.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
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
// site (which puts a construction site block down inside it, see ConstructionSiteBlock), seeing
// what's left to do there, cancelling it, and finishing it: once nothing is left to do, the
// building is marked built and its site block is taken away. The build tool and AI factions will both
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
        List<Blueprint.Part> placed = BuildingRules.place(blueprint.get(), origin, rotation);
        Optional<BlockPos> siteSpot = BuildingRules.siteSpot(placed);
        if (siteSpot.isEmpty()) {
            return refused("The building " + typeId + " has no clear spot inside for its construction site");
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
        Building building = new Building(typeId, origin, rotation, footprint, siteSpot.get(), level.getGameTime(), Building.NOT_BUILT);
        data.addBuilding(settlement, building);
        // The site block goes down now: it's the site's panel and the stock its materials are pooled in.
        level.setBlockAndUpdate(siteSpot.get(), KingdomRts.CONSTRUCTION_SITE.get().defaultBlockState());
        if (level.getBlockEntity(siteSpot.get()) instanceof ConstructionSiteBlockEntity stock) {
            stock.setSettlement(settlement.id());
        }
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
    // The construction site block isn't counted as in the way: it goes when the rest is done. Never
    // loads chunks: if the site isn't loaded, it says so.
    public static RemainingResult remaining(ServerLevel level, Building building) {
        List<Blueprint.Part> placed = placed(level, building);
        if (placed == null) {
            return new RemainingResult(null, "The building " + building.type() + " has no usable structure");
        }
        BoundingBox box = building.footprint();
        if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            return new RemainingResult(null, "The site isn't loaded");
        }
        List<Blueprint.Part> parts = building.site() == null ? placed
                : placed.stream().filter(part -> !part.pos().equals(building.site())).toList();
        return new RemainingResult(BuildingRules.remaining(parts, level::getBlockState), null);
    }

    // If nothing is left to do at a site, finishes it: the building is marked built and its
    // construction site block is taken away (whatever is left in its stock drops, until builders
    // take leftovers back to storage). Returns whether it's built now.
    public static boolean completeIfDone(ServerLevel level, Settlement settlement, Building building) {
        if (building.isBuilt()) {
            return true;
        }
        RemainingResult result = remaining(level, building);
        if (result.remaining() == null || !result.remaining().isDone()) {
            return false;
        }
        // Marked built first, so taking the site block away doesn't cancel it (see ConstructionSiteBlock).
        SettlementData.get(level).finishBuilding(settlement, building, level.getGameTime());
        removeSiteBlock(level, building);
        return true;
    }

    // The building's blueprint where it goes at its site, or null if its template is gone.
    @Nullable
    public static List<Blueprint.Part> placed(ServerLevel level, Building building) {
        return BuildingType.get(level, building.type())
                .flatMap(type -> BuildingTemplates.blueprint(level, type))
                .map(blueprint -> BuildingRules.place(blueprint, building.origin(), building.rotation()))
                .orElse(null);
    }

    // Cancels a commission: the settlement forgets the site, and its construction site block is
    // removed (its stock drops). Other blocks already placed stay.
    public static void cancel(ServerLevel level, Settlement settlement, Building building) {
        forget(level, settlement, building);
        removeSiteBlock(level, building);
    }

    private static void removeSiteBlock(ServerLevel level, Building building) {
        if (building.site() != null && level.getBlockState(building.site()).is(KingdomRts.CONSTRUCTION_SITE.get())) {
            level.destroyBlock(building.site(), false);
        }
    }

    // The settlement forgets a building, e.g. because its construction site block was broken.
    static void forget(ServerLevel level, Settlement settlement, Building building) {
        SettlementData.get(level).removeBuilding(settlement, building.origin());
    }

    // The building whose construction site block is at pos, if any.
    @Nullable
    public static Building siteAt(Settlement settlement, BlockPos pos) {
        for (Building building : settlement.buildings()) {
            if (pos.equals(building.site())) {
                return building;
            }
        }
        return null;
    }

    // Demolishes a building, finished or not: the settlement forgets it (so its ground is free again
    // and it's no longer listed), a construction site's block is taken away (its stock drops, so
    // materials gathered there aren't lost), and every block of the building still in place is
    // removed at once, with no drops. Blocks that aren't part of it (a chest someone put inside) stay.
    // A stand-in for the build tool's demolish, which will look like an explosion and give some of
    // the materials back. Returns how many blocks it removed, or -1 if the site isn't loaded (so a
    // building is never half removed). With its template gone, it's only forgotten.
    public static int demolish(ServerLevel level, Settlement settlement, Building building) {
        BoundingBox box = building.footprint();
        if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            return -1;
        }
        List<Blueprint.Part> placed = placed(level, building);
        forget(level, settlement, building);
        removeSiteBlock(level, building);
        if (placed == null) {
            return 0;
        }
        // Top down, without shape updates while clearing (so nothing pops off as drops halfway),
        // then neighbours are told.
        List<BlockPos> removed = new ArrayList<>();
        List<Blueprint.Part> topDown = new ArrayList<>(placed);
        topDown.sort(Comparator.comparingInt((Blueprint.Part part) -> part.pos().getY()).reversed());
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (Blueprint.Part part : topDown) {
            if (!part.state().isAir() && BuildingRules.matches(part.state(), level.getBlockState(part.pos()))) {
                level.setBlock(part.pos(), Blocks.AIR.defaultBlockState(), flags);
                removed.add(part.pos());
            }
        }
        for (BlockPos pos : removed) {
            level.blockUpdated(pos, Blocks.AIR);
        }
        return removed.size();
    }

    // A cheat for testing (an op command, see BuildingCommands): clears what's in the way and puts
    // every missing block in place at once, with no builder and no materials. Blocks are set without
    // shape updates while placing, so a door's lower half doesn't pop off before its upper half is
    // there, then neighbours are told about them; then the site is completed. Returns how many
    // blocks it set, or -1 if the site can't be finished (no template, or not loaded).
    public static int finish(ServerLevel level, Settlement settlement, Building building) {
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
        completeIfDone(level, settlement, building);
        return changed;
    }
}
