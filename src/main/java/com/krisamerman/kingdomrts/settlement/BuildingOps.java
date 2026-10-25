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
import com.krisamerman.kingdomrts.unit.Stock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
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
        Plan plan = plan(level, faction, typeId, origin, rotation);
        if (plan.problem() != null) {
            return refused(plan.problem());
        }
        Building building = new Building(typeId, origin, rotation, plan.footprint(), plan.siteSpot(), level.getGameTime(), Building.NOT_BUILT);
        SettlementData.get(level).addBuilding(plan.settlement(), building);
        // The site block goes down now: it's the site's panel and the stock its materials are pooled in.
        level.setBlockAndUpdate(plan.siteSpot(), KingdomRts.CONSTRUCTION_SITE.get().defaultBlockState());
        if (level.getBlockEntity(plan.siteSpot()) instanceof ConstructionSiteBlockEntity stock) {
            stock.setSettlement(plan.settlement().id());
        }
        return new CommissionResult(building, null);
    }

    // Why commissioning this building there would be refused, or null if it would go ahead. Changes
    // nothing: the nation interface asks this as the player moves a preview, to tint it.
    @Nullable
    public static String commissionProblem(ServerLevel level, Faction faction, ResourceLocation typeId, BlockPos origin, Rotation rotation) {
        return plan(level, faction, typeId, origin, rotation).problem();
    }

    // Where a commissioned building would go, worked out and checked without changing anything.
    // Either problem is set, or everything else is.
    private record Plan(@Nullable String problem, @Nullable Settlement settlement, @Nullable BoundingBox footprint, @Nullable BlockPos siteSpot) {
        static Plan refused(String problem) {
            return new Plan(problem, null, null, null);
        }
    }

    private static Plan plan(ServerLevel level, Faction faction, ResourceLocation typeId, BlockPos origin, Rotation rotation) {
        Optional<BuildingType> type = BuildingType.get(level, typeId);
        if (type.isEmpty()) {
            return Plan.refused("Unknown building: " + typeId);
        }
        Optional<Blueprint> blueprint = BuildingTemplates.blueprint(level, type.get());
        if (blueprint.isEmpty()) {
            return Plan.refused("The building " + typeId + " has no usable structure (" + type.get().structure() + ")");
        }
        List<Blueprint.Part> placed = BuildingRules.place(blueprint.get(), origin, rotation);
        Optional<BlockPos> siteSpot;
        if (type.get().core().isPresent()) {
            // A building with a core has its site block on the core's spot, and the core takes its
            // place when it's finished.
            ResourceLocation coreId = type.get().core().get();
            Block core = coreBlock(type.get());
            if (core == null) {
                return Plan.refused("The building " + typeId + "'s core, " + coreId + ", isn't a block");
            }
            siteSpot = BuildingRules.coreOf(placed, core).map(Blueprint.Part::pos);
            if (siteSpot.isEmpty()) {
                return Plan.refused("The building " + typeId + "'s template must have its core, " + coreId + ", exactly once");
            }
        } else {
            siteSpot = BuildingRules.siteSpot(placed);
            if (siteSpot.isEmpty()) {
                return Plan.refused("The building " + typeId + " has no clear spot inside for its construction site");
            }
        }
        BoundingBox footprint = BuildingRules.footprint(blueprint.get().size(), origin, rotation);
        if (level.isOutsideBuildHeight(footprint.minY()) || level.isOutsideBuildHeight(footprint.maxY())) {
            return Plan.refused("It doesn't fit between the world's floor and ceiling");
        }
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(origin));
        if (settlement == null) {
            return Plan.refused("That isn't in a settlement's claim");
        }
        if (!settlement.owner().equals(faction.id())) {
            Faction owner = FactionData.get(level).byId(settlement.owner());
            return Plan.refused("That's " + (owner != null ? owner.name() : "another faction") + "'s claim, not " + faction.name() + "'s");
        }
        String problem = siteProblem(settlement, footprint);
        if (problem != null) {
            return Plan.refused(problem);
        }
        return new Plan(null, settlement, footprint, siteSpot.get());
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

    // If nothing is left to do at a site, finishes it: the building is marked built. Its
    // construction site block stays while anything is left in its stock, for its builders to carry
    // to storage (see BuilderJob), and goes once the stock is empty (closeIfEmpty). Returns whether
    // it's built now.
    public static boolean completeIfDone(ServerLevel level, Settlement settlement, Building building) {
        Building current = settlement.buildingAt(building.origin());
        if (current == null || current.demolishing()) {
            return false;
        }
        if (!current.isBuilt()) {
            RemainingResult result = remaining(level, current);
            if (result.remaining() == null || !result.remaining().isDone()) {
                return false;
            }
            Block core = coreBlock(level, current);
            if (core != null && (current.site() == null || !level.isLoaded(current.site()))) {
                return false;
            }
            SettlementData.get(level).finishBuilding(settlement, current, level.getGameTime());
            if (core != null) {
                becomeCore(level, settlement, current);
                return true;
            }
        }
        closeIfEmpty(level, settlement, current);
        return true;
    }

    // A finished building with a core: its site closes at once, and its construction site block
    // becomes the core, as the template has it, with what was left in the site's stock moved into
    // the core's (a hut's starting stock, such as spare axes) rather than carried off to storage.
    // Then the core starts its work: a hut becomes the settlement's workplace, and starts hiring.
    private static void becomeCore(ServerLevel level, Settlement settlement, Building building) {
        BlockPos pos = building.site();
        List<Blueprint.Part> placed = placed(level, building);
        BlockState state = placed == null ? null
                : placed.stream().filter(part -> part.pos().equals(pos)).map(Blueprint.Part::state).findFirst().orElse(null);
        if (state == null) {
            return;
        }
        List<ItemStack> leftovers = new ArrayList<>();
        if (level.getBlockEntity(pos) instanceof ConstructionSiteBlockEntity site) {
            for (int i = 0; i < site.getContainerSize(); i++) {
                ItemStack stack = site.removeItemNoUpdate(i);
                if (!stack.isEmpty()) {
                    leftovers.add(stack);
                }
            }
        }
        // The site is closed first, so taking its block away doesn't cancel the building (see
        // ConstructionSiteBlock).
        SettlementData.get(level).closeSite(settlement, building);
        level.setBlockAndUpdate(pos, BuildingRules.placedState(state));
        if (state.getBlock() instanceof SettlementContainerBlock container) {
            container.register(level, pos);
        }
        for (ItemStack stack : leftovers) {
            ItemStack left = level.getBlockEntity(pos) instanceof Container stock ? Stock.insert(stock, stack) : stack;
            if (!left.isEmpty()) {
                Block.popResource(level, pos, left);
            }
        }
    }

    // A building's core block, or null if it has none (or its type is gone).
    @Nullable
    public static Block coreBlock(ServerLevel level, Building building) {
        return BuildingType.get(level, building.type()).map(BuildingOps::coreBlock).orElse(null);
    }

    @Nullable
    private static Block coreBlock(BuildingType type) {
        return type.core().filter(BuiltInRegistries.BLOCK::containsKey).map(BuiltInRegistries.BLOCK::get).orElse(null);
    }

    // Closes a built building's site once its stock is empty (or its block is gone): the settlement
    // stops recording it as a site, frees its builders, and the construction site block is taken
    // away (in that order, so taking it away doesn't cancel the building: see ConstructionSiteBlock).
    // Returns whether the site is closed now.
    public static boolean closeIfEmpty(ServerLevel level, Settlement settlement, Building building) {
        Building current = settlement.buildingAt(building.origin());
        if (current == null || current.site() == null) {
            return true;
        }
        if (!current.isBuilt() || current.demolishing() || !level.isLoaded(current.site())) {
            return false;
        }
        if (level.getBlockEntity(current.site()) instanceof ConstructionSiteBlockEntity stock && !stock.isEmpty()) {
            return false;
        }
        SettlementData.get(level).closeSite(settlement, current);
        removeSiteBlock(level, current);
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

    // Orders a building demolished, built or still being built: the faction's builders take it apart
    // (see BuilderJob), and the settlement forgets it once nothing of it is left (finishDemolition).
    // Only the faction that owns the building can. Returns why it can't be, or null.
    @Nullable
    public static String orderDemolition(ServerLevel level, Faction faction, Settlement settlement, Building building) {
        Building current = settlement.buildingAt(building.origin());
        if (current == null) {
            return "That building is no longer commissioned";
        }
        if (!faction.id().equals(settlement.owner())) {
            return "The " + typeName(level, current) + " belongs to another faction";
        }
        if (current.demolishing()) {
            return "The " + typeName(level, current) + " is already being demolished";
        }
        SettlementData.get(level).setDemolishing(settlement, current, true);
        return null;
    }

    // Stops a demolition: the building stays as it is (what's been taken down stays down), and is
    // built, or a site again, as it was. A site keeps its builders; a built building's go back to the
    // pool, as a built building takes none. Neighbours of what was taken down are told now (see
    // BuilderJob: blocks are taken down without telling them, so nothing pops off).
    // Only the faction that owns the building can. Returns why it can't be, or null.
    @Nullable
    public static String stopDemolition(ServerLevel level, Faction faction, Settlement settlement, Building building) {
        Building current = settlement.buildingAt(building.origin());
        if (current == null) {
            return "That building is no longer commissioned";
        }
        if (!faction.id().equals(settlement.owner())) {
            return "The " + typeName(level, current) + " belongs to another faction";
        }
        if (!current.demolishing()) {
            return "The " + typeName(level, current) + " isn't being demolished";
        }
        SettlementData.get(level).setDemolishing(settlement, current, false);
        updateTakenDown(level, current);
        return null;
    }

    // If nothing of a building being demolished is left (none of its blocks, and nothing in its
    // site's stock), the settlement forgets it and its site block goes. Returns whether it's gone.
    public static boolean finishDemolitionIfDone(ServerLevel level, Settlement settlement, Building building) {
        Building current = settlement.buildingAt(building.origin());
        if (current == null) {
            return true;
        }
        BoundingBox box = current.footprint();
        List<Blueprint.Part> placed = placed(level, current);
        if (!current.demolishing() || !level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            return false;
        }
        if (placed != null && placed.stream().anyMatch(part -> !part.pos().equals(current.site())
                && BuildingRules.isToTakeDown(part.state(), level.getBlockState(part.pos())))) {
            return false;
        }
        if (current.site() != null && level.getBlockEntity(current.site()) instanceof ConstructionSiteBlockEntity stock && !stock.isEmpty()) {
            return false;
        }
        forget(level, settlement, current);
        removeSiteBlock(level, current);
        updateTakenDown(level, current);
        return true;
    }

    // Tells the neighbours of every spot of the building that's now empty, once a demolition ends.
    private static void updateTakenDown(ServerLevel level, Building building) {
        List<Blueprint.Part> placed = placed(level, building);
        if (placed == null) {
            return;
        }
        for (Blueprint.Part part : placed) {
            if (!part.state().isAir() && level.isLoaded(part.pos()) && level.getBlockState(part.pos()).isAir()) {
                level.blockUpdated(part.pos(), Blocks.AIR);
            }
        }
    }

    private static String typeName(ServerLevel level, Building building) {
        return BuildingType.get(level, building.type()).map(BuildingType::name).orElse(building.type().toString());
    }

    // Removes a building at once, finished or not: the settlement forgets it, a construction site's
    // block is taken away (its stock drops), and every block of the building still in place is
    // removed, with no drops. Blocks that aren't part of it stay. Not a player or faction action
    // (demolition goes through builders: orderDemolition); for tests and admin use. Returns how many
    // blocks it removed, or -1 if the site isn't loaded (so a building is never half removed). With
    // its template gone, it's only forgotten.
    public static int removeAtOnce(ServerLevel level, Settlement settlement, Building building) {
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
