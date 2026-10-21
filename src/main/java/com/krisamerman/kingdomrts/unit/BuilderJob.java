package com.krisamerman.kingdomrts.unit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingRules;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.ConstructionSiteBlockEntity;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.SettlementStorageBlockEntity;
import com.krisamerman.kingdomrts.settlement.SitePlacement;
import com.krisamerman.kingdomrts.settlement.SiteReservations;
import com.krisamerman.kingdomrts.unit.job.Footing;
import com.krisamerman.kingdomrts.unit.job.Job;
import com.krisamerman.kingdomrts.unit.job.JobStep;
import com.krisamerman.kingdomrts.unit.job.WaitStep;
import com.krisamerman.kingdomrts.unit.job.WalkToStep;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// The builder's job, as explicit steps. In the pool (not assigned), it goes back to its post, the
// town hall, and waits. Assigned to a construction site, it walks there and builds:
//   plan (work out what's in the way and the missing blocks, a batch per tick: see BuildingRules) ->
//   clear what's in the way first, top down, breaking each block as a player would with the best
//   tool in the site's stock (the drops go into the stock) ->
//   for each planned block it can place now: reserve it (so builders sharing the site don't go
//   for the same block), get within reach, work on it for ticks_per_block, place it from the
//   site's stock (see SitePlacement) -> when the plan runs out, plan again -> when nothing is
//   missing, the building is completed (BuildingOps#completeIfDone) -> it clears out the site's
//   stock (spare materials, what clearing broke, tools), carrying it to settlement storage a load
//   at a time -> once the stock is empty, the site closes and it's back in the pool.
// Assigned to a building being demolished, it takes it apart instead: it clears out its site's
// stock (if it's still a site), then takes the building's own blocks down (top down, what hangs
// on others first: BuildingRules#DEMOLISH_ORDER), emptying containers before breaking them. Each
// block gives back what placing it cost, into its inventory; when that's full it drops the load
// off at storage. Once nothing of the building is left, the settlement forgets it
// (BuildingOps#finishDemolitionIfDone).
// Blocks it can't place yet (no materials in the stock, nothing to hang on yet, something in the
// way) are left for the next plan; with nothing placeable it fetches what the site is missing (one
// builder per site at a time, see SiteReservations): from the nearest settlement storage that has
// some, as much as it can carry, into the site's stock. Failing that it waits a little and plans
// again, so it carries on by itself when materials arrive.
// It never drops what it carries: what doesn't fit in the site's stock it keeps and delivers later,
// and anything it still carries once it's back in the pool goes to settlement storage, or stays
// with it while there's no room. Per tick it only waits, walks, or counts down to the next block;
// planning is spread over ticks and only happens when a plan runs out, and storage is only looked
// through when it has nothing else to do.
public final class BuilderJob implements Job {
    private static final int WAIT_TICKS = 40;
    private static final int RETRY_TICKS = 40;
    private static final int WALK_TIMEOUT_TICKS = 20 * 60;
    // On site: within this many blocks of the building's footprint (it walks next to the site block
    // to get there). Back at its post: within AT_POST of it.
    private static final int ON_SITE_MARGIN = 4;
    private static final double AT_POST = 4.0;
    // How far a builder reaches to place a block (from its eyes, like a player).
    private static final double REACH = 5.0;
    // How many planned blocks one look goes through to choose the next one.
    private static final int CANDIDATES_PER_LOOK = 256;
    // How close it has to be to a container to take from it or put into it.
    private static final double HAND_OVER_REACH = 4.0;
    // How long it waits before trying again to put down what it's holding (no room anywhere).
    private static final int HOLD_RETRY_TICKS = 100;

    private final BuilderOccupation settings;
    @Nullable
    private JobStep walk;
    // The site being planned or built (its building's origin), the planned blocks in build order,
    // and how far through them it is.
    @Nullable
    private BlockPos planSite;
    private final List<Blueprint.Part> plan = new ArrayList<>();
    private final Map<BlockPos, Blueprint.Part> planByPos = new HashMap<>();
    // What's in the way (inside the footprint), top down, with what the blueprint wants there.
    private final List<Blueprint.Part> clear = new ArrayList<>();
    // Blocks it couldn't walk to during this plan, and the block it's walking to now.
    private final Set<BlockPos> unreachable = new HashSet<>();
    @Nullable
    private BlockPos walkingTo;
    // Whether the last plan found anything missing at all (if not, the site is done).
    private boolean planFoundMissing;
    // Trips: the site it's fetching for (it holds the site's fetch) and the storage and items it's
    // fetching; the walks of each kind of trip; the storage it's dropping things off at; and when to
    // try again to deliver to the site or drop off at storage, after finding no room.
    @Nullable
    private BlockPos fetchSite;
    @Nullable
    private BlockPos fetchFrom;
    private Map<Item, Integer> fetchWanted = Map.of();
    @Nullable
    private JobStep fetchWalk;
    @Nullable
    private JobStep deliverWalk;
    @Nullable
    private JobStep dropWalk;
    @Nullable
    private JobStep clearOutWalk;
    @Nullable
    private BlockPos dropAt;
    private long deliverAgainAt;
    private long dropAgainAt;

    public BuilderJob(BuilderOccupation settings) {
        this.settings = settings;
    }

    @Override
    public JobStep next(Unit unit, @Nullable JobStep previous, boolean succeeded) {
        boolean walkFailed = previous != null && previous == walk && !succeeded;
        if (!(unit.level() instanceof ServerLevel level)) {
            return new WaitStep(WAIT_TICKS, "client");
        }
        Settlement settlement = SettlementData.get(level).byId(unit.getSettlementId());
        Building site = settlement != null ? BuilderOps.siteOf(settlement, unit.getUUID()) : null;
        long now = level.getGameTime();
        Container carried = unit.getInventory();
        if (site == null || !site.takesBuilders()) {
            clearPlan();
            endFetch(unit);
            JobStep dropOff = dropOff(level, unit, settlement, previous, succeeded, now, () -> !assigned(unit));
            if (dropOff != null) {
                return dropOff;
            }
            return goTo(unit, unit.getPost(), AT_POST, walkFailed, carried.isEmpty() ? "waiting for a site" : "holding what it carries: no room in storage",
                    () -> !assigned(unit));
        }
        if (!site.origin().equals(planSite)) {
            clearPlan();
        }
        if (site.demolishing()) {
            endFetch(unit);
            return demolish(level, unit, settlement, site, previous, succeeded, walkFailed, now);
        }
        if (site.isBuilt()) {
            clearPlan();
            endFetch(unit);
            return clearOut(level, unit, settlement, site, previous, succeeded, now);
        }
        // A fetch: at the storage, take what the site wants; then (carrying it) deliver it below.
        if (previous != null && previous == fetchWalk) {
            fetchWalk = null;
            if (succeeded && fetchFrom != null && site.origin().equals(fetchSite)) {
                return new TakeStep(fetchFrom, fetchWanted);
            }
            endFetch(unit);
        }
        if (previous instanceof TakeStep) {
            fetchFrom = null;
            if (!succeeded) {
                endFetch(unit);
            }
        }
        if (previous instanceof DeliverStep) {
            endFetch(unit);
            if (!carried.isEmpty()) {
                deliverAgainAt = now + HOLD_RETRY_TICKS; // the site's stock is full: keep it for now
            }
        }
        if (previous != null && previous == deliverWalk) {
            deliverWalk = null;
            if (!succeeded) {
                deliverAgainAt = now + HOLD_RETRY_TICKS;
            }
        }
        // Carrying something: deliver it to the site's stock first.
        if (!carried.isEmpty() && now >= deliverAgainAt) {
            if (fetchSite != null) {
                SiteReservations.reserveFetch(fetchSite, unit.getUUID(), now); // still on its way
            }
            if (unit.position().distanceTo(site.site().getCenter()) <= HAND_OVER_REACH) {
                return new DeliverStep(site.site());
            }
            deliverWalk = WalkToStep.near(site.site(), WALK_TIMEOUT_TICKS, () -> stillAssignedTo(unit, site));
            return deliverWalk;
        }
        if (!site.footprint().inflatedBy(ON_SITE_MARGIN).isInside(unit.blockPosition())) {
            if (walkFailed) {
                return new WaitStep(RETRY_TICKS, "retrying the walk to the site");
            }
            // Next to the site block (it's solid, so not onto it).
            walk = WalkToStep.near(site.site(), WALK_TIMEOUT_TICKS, () -> stillAssignedTo(unit, site));
            return walk;
        }
        if (previous instanceof PlanStep planStep) {
            planSite = site.origin();
            plan.addAll(planStep.missing);
            plan.forEach(part -> planByPos.put(part.pos(), part));
            clear.addAll(planStep.inTheWay);
            planFoundMissing = !plan.isEmpty() || !clear.isEmpty();
        } else if (planSite == null) {
            return new PlanStep(site);
        }
        ConstructionSiteBlockEntity siteStock = level.getBlockEntity(site.site()) instanceof ConstructionSiteBlockEntity entity ? entity : null;
        // First, clear what's in the way: the first block (top down) it can reach, else walk to one.
        if (siteStock != null && !clear.isEmpty()) {
            Blueprint.Part toBreak = null;
            Blueprint.Part toWalkTo = null;
            int looked = 0;
            for (Iterator<Blueprint.Part> it = clear.iterator(); it.hasNext() && looked < CANDIDATES_PER_LOOK; looked++) {
                Blueprint.Part target = it.next();
                if (!BuildingRules.isObstruction(target.state(), level.getBlockState(target.pos()))) {
                    it.remove(); // cleared (by it or someone else)
                    continue;
                }
                if (unreachable.contains(target.pos()) || SiteReservations.heldByOther(site.origin(), target.pos(), unit.getUUID(), now)) {
                    continue;
                }
                if (unit.getEyePosition().distanceTo(target.pos().getCenter()) <= REACH) {
                    toBreak = target;
                    break;
                }
                if (toWalkTo == null) {
                    toWalkTo = target;
                }
            }
            if (walkFailed && walkingTo != null) {
                unreachable.add(walkingTo);
                walkingTo = null;
            }
            if (toBreak != null && SiteReservations.reserve(site.origin(), toBreak.pos(), unit.getUUID(), now)) {
                walkingTo = null;
                return new ClearStep(site, toBreak.pos(), siteStock);
            }
            if (toWalkTo != null && !unreachable.contains(toWalkTo.pos())) {
                walkingTo = toWalkTo.pos();
                walk = WalkToStep.near(toWalkTo.pos(), WALK_TIMEOUT_TICKS, () -> stillAssignedTo(unit, site));
                return walk;
            }
            walkFailed = false; // dealt with; on to placing
        }
        // The next block to place: the first planned block (in build order) it can place now and
        // reach from where it stands; failing that, the first it can place at all, to walk to. Only
        // the first CANDIDATES_PER_LOOK planned blocks are looked at, so a big building's plan
        // doesn't make each look expensive; placed blocks drop out of the plan as it goes.
        ConstructionSiteBlockEntity stock = level.getBlockEntity(site.site()) instanceof ConstructionSiteBlockEntity entity ? entity : null;
        Blueprint.Part standingIn = null;
        Blueprint.Part toWalkTo = null;
        Blueprint.Part toPlace = null;
        int looked = 0;
        for (Iterator<Blueprint.Part> it = plan.iterator(); stock != null && it.hasNext() && looked < CANDIDATES_PER_LOOK; looked++) {
            Blueprint.Part part = it.next();
            if (unreachable.contains(part.pos()) || SiteReservations.heldByOther(site.origin(), part.pos(), unit.getUUID(), now)) {
                continue;
            }
            Blueprint.Part partner = BuildingRules.partnerOf(part, planByPos).orElse(null);
            SitePlacement.Result check = SitePlacement.check(level, part, partner);
            if (check == SitePlacement.Result.ALREADY_DONE) {
                it.remove(); // placed (by it or someone else)
                continue;
            }
            if (check == SitePlacement.Result.CANT_STAY_YET && unit.getBoundingBox().intersects(new AABB(part.pos()))) {
                standingIn = part; // it's standing where this goes: other blocks first, then step aside
                continue;
            }
            if (check != SitePlacement.Result.PLACED || !Stock.has(stock, BuildingRules.costOf(part.state()))) {
                continue;
            }
            if (unit.getEyePosition().distanceTo(part.pos().getCenter()) <= REACH) {
                toPlace = part;
                break;
            }
            if (toWalkTo == null) {
                toWalkTo = part;
            }
        }
        if (walkFailed && walkingTo != null) {
            unreachable.add(walkingTo); // couldn't get to it: leave it for the next plan
            walkingTo = null;
        }
        if (toPlace != null && SiteReservations.reserve(site.origin(), toPlace.pos(), unit.getUUID(), now)) {
            walkingTo = null;
            return new PlaceStep(site, toPlace, BuildingRules.partnerOf(toPlace, planByPos).orElse(null), stock);
        }
        if (toWalkTo != null && !unreachable.contains(toWalkTo.pos())) {
            walkingTo = toWalkTo.pos();
            walk = WalkToStep.near(toWalkTo.pos(), WALK_TIMEOUT_TICKS, () -> stillAssignedTo(unit, site));
            return walk;
        }
        // Nothing else to place, but it's standing where a block goes: step aside onto a spot that
        // isn't part of the plan (e.g. onto the floor it just laid), then plan again.
        if (standingIn != null && !walkFailed) {
            BlockPos aside = asideFrom(level, unit, standingIn);
            if (aside != null) {
                clearPlan();
                walk = WalkToStep.to(aside, 0.4, WALK_TIMEOUT_TICKS, () -> stillAssignedTo(unit, site));
                return walk;
            }
        }
        // Nothing it can place now: done if nothing was missing, else plan again after a pause
        // (materials may arrive, or another builder's blocks may give the rest something to stand on).
        boolean foundMissing = planFoundMissing;
        boolean allPlaced = plan.isEmpty() && clear.isEmpty();
        List<ItemStack> costs = new ArrayList<>();
        plan.forEach(part -> costs.addAll(BuildingRules.costOf(part.state())));
        clearPlan();
        if (foundMissing && allPlaced) {
            return new PlanStep(site); // everything planned is in place: check again straight away
        }
        if (!foundMissing) {
            BuildingOps.completeIfDone(level, settlement, site);
            return new WaitStep(WAIT_TICKS, "finishing up");
        }
        JobStep fetch = startFetch(level, unit, settlement, site, stock, costs, now);
        return fetch != null ? fetch : new WaitStep(RETRY_TICKS, "waiting for materials");
    }

    // Starts a fetch for the site, if no other builder is fetching for it and some settlement storage
    // has something it's missing: walks to the nearest such storage (TakeStep then takes it).
    @Nullable
    private JobStep startFetch(ServerLevel level, Unit unit, Settlement settlement, Building site, @Nullable ConstructionSiteBlockEntity stock,
            List<ItemStack> costs, long now) {
        if (stock == null) {
            return null;
        }
        Map<Item, Integer> wanted = Hauling.stillWanted(costs, stock, unit.getInventory());
        if (wanted.isEmpty() || !SiteReservations.reserveFetch(site.origin(), unit.getUUID(), now)) {
            return null;
        }
        BlockPos from = nearestStorage(level, settlement, unit, storage -> Hauling.hasAny(storage, wanted));
        if (from == null) {
            SiteReservations.releaseFetch(site.origin(), unit.getUUID());
            return null;
        }
        fetchSite = site.origin();
        fetchFrom = from;
        fetchWanted = wanted;
        if (unit.position().distanceTo(from.getCenter()) <= HAND_OVER_REACH) {
            return new TakeStep(from, wanted);
        }
        fetchWalk = WalkToStep.near(from, WALK_TIMEOUT_TICKS, () -> stillAssignedTo(unit, site));
        return fetchWalk;
    }

    private void endFetch(Unit unit) {
        if (fetchSite != null) {
            SiteReservations.releaseFetch(fetchSite, unit.getUUID());
        }
        fetchSite = null;
        fetchFrom = null;
        fetchWanted = Map.of();
        fetchWalk = null;
    }

    // Taking a building apart (see the top). Per call it looks at a bounded number of the planned
    // blocks, like placing.
    private JobStep demolish(ServerLevel level, Unit unit, Settlement settlement, Building site, @Nullable JobStep previous, boolean succeeded,
            boolean walkFailed, long now) {
        Container carried = unit.getInventory();
        // A full load goes to storage first (or it holds onto it while there's no room).
        if (!Hauling.hasEmptySlot(carried) || (previous != null && previous == dropWalk)) {
            JobStep dropOff = dropOff(level, unit, settlement, previous, succeeded, now, () -> true);
            if (dropOff != null) {
                return dropOff;
            }
            if (!Hauling.hasEmptySlot(carried)) {
                return new WaitStep(HOLD_RETRY_TICKS, "holding what it carries: no room in storage");
            }
        }
        // A site's stock is cleared out first.
        if (previous != null && previous == clearOutWalk) {
            clearOutWalk = null;
            if (!succeeded) {
                return new WaitStep(RETRY_TICKS, "retrying the walk to the site");
            }
        }
        if (site.site() != null && level.getBlockEntity(site.site()) instanceof ConstructionSiteBlockEntity stock && !stock.isEmpty()) {
            if (unit.position().distanceTo(site.site().getCenter()) <= HAND_OVER_REACH) {
                return new TakeAllStep(site.site());
            }
            clearOutWalk = WalkToStep.near(site.site(), WALK_TIMEOUT_TICKS, () -> stillAssignedTo(unit, site));
            return clearOutWalk;
        }
        boolean justPlanned = previous instanceof DemolishPlanStep;
        if (previous instanceof DemolishPlanStep planStep) {
            clearPlan();
            planSite = site.origin();
            plan.addAll(planStep.toTakeDown);
        } else if (planSite == null) {
            return new DemolishPlanStep(site);
        }
        if (walkFailed && walkingTo != null) {
            unreachable.add(walkingTo);
            walkingTo = null;
        }
        Blueprint.Part toTakeDown = null;
        Blueprint.Part toWalkTo = null;
        int looked = 0;
        for (Iterator<Blueprint.Part> it = plan.iterator(); it.hasNext() && looked < CANDIDATES_PER_LOOK; looked++) {
            Blueprint.Part part = it.next();
            if (!BuildingRules.isToTakeDown(part.state(), level.getBlockState(part.pos()))) {
                it.remove(); // taken down (by it or someone else)
                continue;
            }
            if (SiteReservations.heldByOther(site.origin(), part.pos(), unit.getUUID(), now)) {
                continue;
            }
            // Within reach it's taken, even if it couldn't walk to it (a roof has nowhere to stand
            // near it, but can be reached from the ground or a wall).
            if (unit.getEyePosition().distanceTo(part.pos().getCenter()) <= REACH) {
                toTakeDown = part;
                break;
            }
            if (toWalkTo == null && !unreachable.contains(part.pos())) {
                toWalkTo = part;
            }
        }
        if (toTakeDown != null && SiteReservations.reserve(site.origin(), toTakeDown.pos(), unit.getUUID(), now)) {
            walkingTo = null;
            if (level.getBlockEntity(toTakeDown.pos()) instanceof Container container && !container.isEmpty()) {
                return new EmptyContainerStep(site, toTakeDown.pos()); // what's in it first; the block once it's empty
            }
            if (!Hauling.fitsAll(carried, BuildingRules.costOf(toTakeDown.state()))) {
                SiteReservations.release(site.origin(), toTakeDown.pos(), unit.getUUID());
                dropAgainAt = 0;
                JobStep dropOff = dropOff(level, unit, settlement, previous, succeeded, now, () -> true);
                return dropOff != null ? dropOff : new WaitStep(HOLD_RETRY_TICKS, "holding what it carries: no room in storage");
            }
            return new TakeDownStep(site, toTakeDown);
        }
        if (toWalkTo != null) {
            walkingTo = toWalkTo.pos();
            walk = WalkToStep.near(toWalkTo.pos(), WALK_TIMEOUT_TICKS, () -> stillAssignedTo(unit, site));
            return walk;
        }
        // Nothing it can take down now: gone, if a fresh look found nothing left; else look again
        // after a pause (other builders are on the rest, or it couldn't get to them).
        boolean nothingLeft = justPlanned && plan.isEmpty();
        clearPlan();
        if (nothingLeft && BuildingOps.finishDemolitionIfDone(level, settlement, site)) {
            return new WaitStep(WAIT_TICKS, "finishing up");
        }
        return justPlanned ? new WaitStep(RETRY_TICKS, "waiting to take down the rest") : new DemolishPlanStep(site);
    }

    // Clearing out a finished site's stock: whatever it carries goes to settlement storage (or it
    // holds onto it while there's no room); with empty hands it takes a load from the site's stock.
    // Once the stock is empty and it carries nothing, the site closes (BuildingOps#closeIfEmpty).
    private JobStep clearOut(ServerLevel level, Unit unit, Settlement settlement, Building site, @Nullable JobStep previous, boolean succeeded,
            long now) {
        JobStep dropOff = dropOff(level, unit, settlement, previous, succeeded, now, () -> true);
        if (dropOff != null) {
            return dropOff;
        }
        if (!unit.getInventory().isEmpty()) {
            return new WaitStep(HOLD_RETRY_TICKS, "holding what it carries: no room in storage");
        }
        if (previous != null && previous == clearOutWalk) {
            clearOutWalk = null;
            if (!succeeded) {
                return new WaitStep(RETRY_TICKS, "retrying the walk to the site");
            }
        }
        if (!(level.getBlockEntity(site.site()) instanceof ConstructionSiteBlockEntity stock) || stock.isEmpty()) {
            BuildingOps.closeIfEmpty(level, settlement, site);
            return new WaitStep(WAIT_TICKS, "finishing up");
        }
        if (unit.position().distanceTo(site.site().getCenter()) <= HAND_OVER_REACH) {
            return new TakeAllStep(site.site());
        }
        clearOutWalk = WalkToStep.near(site.site(), WALK_TIMEOUT_TICKS, () -> stillAssignedTo(unit, site));
        return clearOutWalk;
    }

    // Takes what it carries to the nearest settlement storage with room for it (back in the pool, or
    // clearing out a site). With no room anywhere, it holds onto it and looks again later. Null when
    // there's nothing to drop off (or not yet).
    @Nullable
    private JobStep dropOff(ServerLevel level, Unit unit, @Nullable Settlement settlement, @Nullable JobStep previous, boolean succeeded, long now,
            BooleanSupplier stillWanted) {
        Container carried = unit.getInventory();
        if (previous != null && previous == dropWalk) {
            dropWalk = null;
            if (succeeded && dropAt != null) {
                return new DropOffStep(dropAt);
            }
            dropAgainAt = now + HOLD_RETRY_TICKS;
            return null;
        }
        if (previous instanceof DropOffStep && !succeeded) {
            dropAgainAt = now + HOLD_RETRY_TICKS;
        }
        if (carried.isEmpty() || now < dropAgainAt || settlement == null) {
            return null;
        }
        BlockPos to = nearestStorage(level, settlement, unit, storage -> Hauling.hasRoomForAny(carried, storage));
        if (to == null) {
            dropAgainAt = now + HOLD_RETRY_TICKS;
            return null;
        }
        dropAt = to;
        if (unit.position().distanceTo(to.getCenter()) <= HAND_OVER_REACH) {
            return new DropOffStep(to);
        }
        dropWalk = WalkToStep.near(to, WALK_TIMEOUT_TICKS, stillWanted);
        return dropWalk;
    }

    // The settlement's nearest loaded storage that passes the test, or null.
    @Nullable
    private static BlockPos nearestStorage(ServerLevel level, Settlement settlement, Unit unit, Predicate<Container> test) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : settlement.storage()) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SettlementStorageBlockEntity storage)
                    || storage instanceof ConstructionSiteBlockEntity) {
                continue;
            }
            double distance = unit.blockPosition().distSqr(pos);
            if (distance < bestDistance && test.test(storage)) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    // A place to stand near a block the builder is in the way of: at least 2 blocks from that
    // block's column (a unit straddling two blocks would still be in the way from right next to it),
    // not where it already is, and not where another planned block goes (nor just above one).
    @Nullable
    private BlockPos asideFrom(ServerLevel level, Unit unit, Blueprint.Part part) {
        for (BlockPos spot : Footing.standSpotsNear(level, part.pos(), 3)) {
            boolean planned = planByPos.containsKey(spot) || planByPos.containsKey(spot.above());
            int apart = Math.max(Math.abs(spot.getX() - part.pos().getX()), Math.abs(spot.getZ() - part.pos().getZ()));
            if (!planned && apart >= 2 && !spot.equals(unit.blockPosition())) {
                return spot;
            }
        }
        return null;
    }

    private JobStep goTo(Unit unit, @Nullable BlockPos target, double reach, boolean walkFailed, String waiting, BooleanSupplier stillWanted) {
        if (target == null) {
            return new WaitStep(WAIT_TICKS, "nowhere to go");
        }
        if (unit.position().distanceTo(target.getBottomCenter()) <= reach) {
            return new WaitStep(WAIT_TICKS, waiting);
        }
        if (walkFailed) {
            return new WaitStep(RETRY_TICKS, "retrying the walk");
        }
        walk = WalkToStep.to(target, reach - 1, WALK_TIMEOUT_TICKS, stillWanted);
        return walk;
    }

    private void clearPlan() {
        plan.clear();
        planByPos.clear();
        clear.clear();
        unreachable.clear();
        walkingTo = null;
        planSite = null;
        planFoundMissing = false;
    }

    private static boolean stillAssignedTo(Unit unit, Building site) {
        return unit.level() instanceof ServerLevel level && SettlementData.get(level).byId(unit.getSettlementId()) instanceof Settlement settlement
                && BuilderOps.siteOf(settlement, unit.getUUID()) instanceof Building now && now.origin().equals(site.origin());
    }

    private static boolean assigned(Unit unit) {
        return unit.level() instanceof ServerLevel level && SettlementData.get(level).byId(unit.getSettlementId()) instanceof Settlement settlement
                && BuilderOps.siteOf(settlement, unit.getUUID()) != null;
    }

    // Works out which blocks of the site are missing (its template, minus what's already in place
    // and the site block's own spot), a batch of the template per tick, then puts them in build order.
    private static final class PlanStep implements JobStep {
        private static final int CHECKED_PER_TICK = 512;
        private final Building site;
        private final List<Blueprint.Part> missing = new ArrayList<>();
        private final List<Blueprint.Part> inTheWay = new ArrayList<>();
        @Nullable
        private List<Blueprint.Part> placed;
        private int next;

        PlanStep(Building site) {
            this.site = site;
        }

        @Override
        public void start(Unit unit) {
            placed = unit.level() instanceof ServerLevel level ? BuildingOps.placed(level, site) : null;
        }

        @Override
        public Status tick(Unit unit) {
            if (placed == null) {
                return Status.FAILED;
            }
            for (int i = 0; i < CHECKED_PER_TICK && next < placed.size(); i++, next++) {
                Blueprint.Part part = placed.get(next);
                if (part.pos().equals(site.site()) || !unit.level().isLoaded(part.pos())) {
                    continue;
                }
                BlockState actual = unit.level().getBlockState(part.pos());
                if (BuildingRules.isObstruction(part.state(), actual)) {
                    inTheWay.add(part);
                }
                if (!part.state().isAir() && !BuildingRules.matches(part.state(), actual)) {
                    missing.add(part);
                }
            }
            if (next < placed.size()) {
                return Status.RUNNING;
            }
            missing.sort(BuildingRules.BUILD_ORDER);
            inTheWay.sort(Comparator.comparing(Blueprint.Part::pos, BuildingRules.CLEAR_ORDER));
            return Status.DONE;
        }

        @Override
        public int timeoutTicks() {
            return 20 * 10;
        }

        @Override
        public String describe() {
            return "plan the site at " + site.origin().toShortString();
        }
    }

    // Breaks one block that's in the way, as a player would: for as long as vanilla says it takes with
    // the best tool in the site's stock (held while it works, wearing as it goes, then put back),
    // showing the cracks. The drops go into the site's stock (or on the ground if it's full). Fails
    // if it's pushed out of reach. Releases its reservation either way.
    private final class ClearStep implements JobStep {
        private static final int SWING_TICKS = 5;
        private static final int LONGEST_BREAK_TICKS = 20 * 30;
        private final Building site;
        private final BlockPos target;
        private final ConstructionSiteBlockEntity stock;
        private int ticks;
        private boolean cracking;

        ClearStep(Building site, BlockPos target, ConstructionSiteBlockEntity stock) {
            this.site = site;
            this.target = target;
            this.stock = stock;
        }

        @Override
        public void start(Unit unit) {
            int slot = Stock.bestToolFor(stock, unit.level().getBlockState(target));
            if (slot >= 0 && unit.getMainHandItem().isEmpty()) {
                unit.setItemSlot(EquipmentSlot.MAINHAND, stock.removeItemNoUpdate(slot));
                stock.setChanged();
            }
        }

        @Override
        public Status tick(Unit unit) {
            ServerLevel level = (ServerLevel) unit.level();
            BlockState state = level.getBlockState(target);
            if (state.isAir() || unit.getEyePosition().distanceTo(target.getCenter()) > REACH + 1 || stock.isRemoved()) {
                return state.isAir() ? Status.DONE : Status.FAILED;
            }
            ItemStack tool = unit.getMainHandItem();
            unit.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            if (ticks % SWING_TICKS == 0) {
                unit.swing(InteractionHand.MAIN_HAND);
            }
            int needed = Math.min(LONGEST_BREAK_TICKS, BreakTime.ticks(unit, tool, state, level, target));
            if (++ticks < needed) {
                level.destroyBlockProgress(unit.getId(), target, ticks * 10 / needed); // vanilla's 10 crack stages
                cracking = true;
                return Status.RUNNING;
            }
            // Like a player: blocks that need the right tool (stone, ores) drop nothing without it.
            // The loot table doesn't check that itself.
            boolean harvests = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
            List<ItemStack> drops = harvests ? Block.getDrops(state, level, target, level.getBlockEntity(target), unit, tool) : List.of();
            level.destroyBlock(target, false, unit); // breaking sound and particles; the drops are handled here
            if (!tool.isEmpty()) {
                tool.hurtAndBreak(1, unit, EquipmentSlot.MAINHAND);
            }
            for (ItemStack drop : drops) {
                ItemStack left = Stock.insert(stock, drop);
                if (!left.isEmpty()) {
                    unit.spawnAtLocation(left);
                }
            }
            return Status.DONE;
        }

        @Override
        public void stop(Unit unit) {
            if (cracking) {
                unit.level().destroyBlockProgress(unit.getId(), target, -1);
            }
            ItemStack held = unit.getMainHandItem();
            if (!held.isEmpty()) {
                ItemStack left = Stock.insert(stock, held); // the tool goes back to the site
                unit.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (!left.isEmpty()) {
                    unit.spawnAtLocation(left);
                }
            }
            SiteReservations.release(site.origin(), target, unit.getUUID());
        }

        @Override
        public int timeoutTicks() {
            return LONGEST_BREAK_TICKS + 20;
        }

        @Override
        public String describe() {
            return "clear " + target.toShortString();
        }
    }

    // Takes what's wanted (in order, as much as fits) from a settlement storage into its inventory.
    // Fails if it isn't next to the storage any more, or took nothing.
    private static final class TakeStep implements JobStep {
        private final BlockPos storage;
        private final Map<Item, Integer> wanted;

        TakeStep(BlockPos storage, Map<Item, Integer> wanted) {
            this.storage = storage;
            this.wanted = new LinkedHashMap<>(wanted);
        }

        @Override
        public Status tick(Unit unit) {
            if (unit.position().distanceTo(storage.getCenter()) > HAND_OVER_REACH
                    || !(unit.level().getBlockEntity(storage) instanceof SettlementStorageBlockEntity container)) {
                return Status.FAILED;
            }
            unit.swing(InteractionHand.MAIN_HAND);
            return Hauling.moveWanted(container, unit.getInventory(), wanted) > 0 ? Status.DONE : Status.FAILED;
        }

        @Override
        public int timeoutTicks() {
            return 1;
        }

        @Override
        public String describe() {
            return "take materials from the storage at " + storage.toShortString();
        }
    }

    // Puts everything it carries into the site's stock, as much as fits (the rest it keeps).
    private static final class DeliverStep implements JobStep {
        private final BlockPos siteBlock;

        DeliverStep(BlockPos siteBlock) {
            this.siteBlock = siteBlock;
        }

        @Override
        public Status tick(Unit unit) {
            if (unit.position().distanceTo(siteBlock.getCenter()) > HAND_OVER_REACH
                    || !(unit.level().getBlockEntity(siteBlock) instanceof ConstructionSiteBlockEntity stock)) {
                return Status.FAILED;
            }
            unit.swing(InteractionHand.MAIN_HAND);
            return Hauling.moveAll(unit.getInventory(), stock) > 0 ? Status.DONE : Status.FAILED;
        }

        @Override
        public int timeoutTicks() {
            return 1;
        }

        @Override
        public String describe() {
            return "deliver materials to the site at " + siteBlock.toShortString();
        }
    }

    // Works out which of a building's blocks are still there to take down (its template, as placed,
    // minus what's gone or changed and the site block's spot), a batch per tick, in DEMOLISH_ORDER.
    private static final class DemolishPlanStep implements JobStep {
        private static final int CHECKED_PER_TICK = 512;
        private final Building site;
        private final List<Blueprint.Part> toTakeDown = new ArrayList<>();
        @Nullable
        private List<Blueprint.Part> placed;
        private int next;

        DemolishPlanStep(Building site) {
            this.site = site;
        }

        @Override
        public void start(Unit unit) {
            placed = unit.level() instanceof ServerLevel level ? BuildingOps.placed(level, site) : null;
        }

        @Override
        public Status tick(Unit unit) {
            if (placed == null) {
                return Status.FAILED;
            }
            for (int i = 0; i < CHECKED_PER_TICK && next < placed.size(); i++, next++) {
                Blueprint.Part part = placed.get(next);
                if (!part.pos().equals(site.site()) && unit.level().isLoaded(part.pos())
                        && BuildingRules.isToTakeDown(part.state(), unit.level().getBlockState(part.pos()))) {
                    toTakeDown.add(part);
                }
            }
            if (next < placed.size()) {
                return Status.RUNNING;
            }
            toTakeDown.sort(BuildingRules.DEMOLISH_ORDER);
            return Status.DONE;
        }

        @Override
        public int timeoutTicks() {
            return 20 * 10;
        }

        @Override
        public String describe() {
            return "plan taking down the building at " + site.origin().toShortString();
        }
    }

    // Takes down one of the building's blocks, as a player would break it (as long as vanilla says,
    // with the best tool it carries, showing the cracks), and keeps what placing it cost. The block
    // goes without telling its neighbours, so nothing hanging on it pops off as a drop before its own
    // turn (they're told once the demolition ends: BuildingOps). Fails if pushed out of reach, or if
    // the block isn't the building's any more. Releases its reservation either way.
    private static final class TakeDownStep implements JobStep {
        private static final int SWING_TICKS = 5;
        private static final int LONGEST_BREAK_TICKS = 20 * 30;
        private final Building site;
        private final Blueprint.Part part;
        private int ticks;
        private boolean cracking;

        TakeDownStep(Building site, Blueprint.Part part) {
            this.site = site;
            this.part = part;
        }

        @Override
        public void start(Unit unit) {
            BlockState state = unit.level().getBlockState(part.pos());
            int slot = Stock.bestToolFor(unit.getInventory(), state);
            if (slot >= 0 && unit.getMainHandItem().isEmpty()) {
                unit.setItemSlot(EquipmentSlot.MAINHAND, unit.getInventory().removeItemNoUpdate(slot));
            }
        }

        @Override
        public Status tick(Unit unit) {
            ServerLevel level = (ServerLevel) unit.level();
            BlockPos target = part.pos();
            BlockState state = level.getBlockState(target);
            if (!BuildingRules.isToTakeDown(part.state(), state) || unit.getEyePosition().distanceTo(target.getCenter()) > REACH + 1
                    || (level.getBlockEntity(target) instanceof Container container && !container.isEmpty())) {
                return Status.FAILED;
            }
            ItemStack tool = unit.getMainHandItem();
            unit.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            if (ticks % SWING_TICKS == 0) {
                unit.swing(InteractionHand.MAIN_HAND);
            }
            int needed = Math.min(LONGEST_BREAK_TICKS, BreakTime.ticks(unit, tool, state, level, target));
            if (++ticks < needed) {
                level.destroyBlockProgress(unit.getId(), target, ticks * 10 / needed);
                cracking = true;
                return Status.RUNNING;
            }
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, target, Block.getId(state)); // breaking sound and particles
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.gameEvent(unit, GameEvent.BLOCK_DESTROY, target);
            if (!tool.isEmpty()) {
                tool.hurtAndBreak(1, unit, EquipmentSlot.MAINHAND);
            }
            for (ItemStack cost : BuildingRules.costOf(state)) {
                ItemStack left = Stock.insert(unit.getInventory(), cost);
                if (!left.isEmpty()) {
                    unit.spawnAtLocation(left); // checked before it started, so it shouldn't happen
                }
            }
            return Status.DONE;
        }

        @Override
        public void stop(Unit unit) {
            if (cracking) {
                unit.level().destroyBlockProgress(unit.getId(), part.pos(), -1);
            }
            ItemStack held = unit.getMainHandItem();
            if (!held.isEmpty()) {
                ItemStack left = Stock.insert(unit.getInventory(), held); // the tool goes back with what it carries
                unit.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (!left.isEmpty()) {
                    unit.spawnAtLocation(left);
                }
            }
            SiteReservations.release(site.origin(), part.pos(), unit.getUUID());
        }

        @Override
        public int timeoutTicks() {
            return LONGEST_BREAK_TICKS + 20;
        }

        @Override
        public String describe() {
            return "take down " + part.state().getBlock().getName().getString() + " at " + part.pos().toShortString();
        }
    }

    // Takes what's in one of the building's containers (a chest that's part of it) into its
    // inventory, as much as fits, before the container itself is taken down. Releases its reservation.
    private static final class EmptyContainerStep implements JobStep {
        private final Building site;
        private final BlockPos pos;

        EmptyContainerStep(Building site, BlockPos pos) {
            this.site = site;
            this.pos = pos;
        }

        @Override
        public Status tick(Unit unit) {
            if (unit.getEyePosition().distanceTo(pos.getCenter()) > REACH + 1 || !(unit.level().getBlockEntity(pos) instanceof Container container)) {
                return Status.FAILED;
            }
            unit.swing(InteractionHand.MAIN_HAND);
            return Hauling.moveAll(container, unit.getInventory()) > 0 ? Status.DONE : Status.FAILED;
        }

        @Override
        public void stop(Unit unit) {
            SiteReservations.release(site.origin(), pos, unit.getUUID());
        }

        @Override
        public int timeoutTicks() {
            return 1;
        }

        @Override
        public String describe() {
            return "empty the container at " + pos.toShortString();
        }
    }

    // Takes a load from a finished site's stock into its inventory, as much as fits. Fails if it
    // isn't next to the site block any more, or took nothing.
    private static final class TakeAllStep implements JobStep {
        private final BlockPos siteBlock;

        TakeAllStep(BlockPos siteBlock) {
            this.siteBlock = siteBlock;
        }

        @Override
        public Status tick(Unit unit) {
            if (unit.position().distanceTo(siteBlock.getCenter()) > HAND_OVER_REACH
                    || !(unit.level().getBlockEntity(siteBlock) instanceof ConstructionSiteBlockEntity stock)) {
                return Status.FAILED;
            }
            unit.swing(InteractionHand.MAIN_HAND);
            int moved = Hauling.moveAll(stock, unit.getInventory());
            if (moved > 0) {
                stock.setChanged();
            }
            return moved > 0 ? Status.DONE : Status.FAILED;
        }

        @Override
        public int timeoutTicks() {
            return 1;
        }

        @Override
        public String describe() {
            return "take a load from the site's stock at " + siteBlock.toShortString();
        }
    }

    // Puts everything it carries into a settlement storage, as much as fits (the rest it keeps).
    private static final class DropOffStep implements JobStep {
        private final BlockPos storage;

        DropOffStep(BlockPos storage) {
            this.storage = storage;
        }

        @Override
        public Status tick(Unit unit) {
            if (unit.position().distanceTo(storage.getCenter()) > HAND_OVER_REACH
                    || !(unit.level().getBlockEntity(storage) instanceof SettlementStorageBlockEntity container)) {
                return Status.FAILED;
            }
            unit.swing(InteractionHand.MAIN_HAND);
            return Hauling.moveAll(unit.getInventory(), container) > 0 ? Status.DONE : Status.FAILED;
        }

        @Override
        public int timeoutTicks() {
            return 1;
        }

        @Override
        public String describe() {
            return "drop off what it carries at the storage at " + storage.toShortString();
        }
    }

    // Works on one block for ticks_per_block (swinging and looking at it), then places it from the
    // site's stock. Fails if it's pushed out of reach. Releases its reservation either way.
    private final class PlaceStep implements JobStep {
        private static final int SWING_TICKS = 5;
        private final Building site;
        private final Blueprint.Part part;
        @Nullable
        private final Blueprint.Part partner;
        private final ConstructionSiteBlockEntity stock;
        private int ticks;

        PlaceStep(Building site, Blueprint.Part part, @Nullable Blueprint.Part partner, ConstructionSiteBlockEntity stock) {
            this.site = site;
            this.part = part;
            this.partner = partner;
            this.stock = stock;
        }

        @Override
        public Status tick(Unit unit) {
            if (unit.getEyePosition().distanceTo(part.pos().getCenter()) > REACH + 1 || stock.isRemoved()) {
                return Status.FAILED;
            }
            unit.getLookControl().setLookAt(part.pos().getX() + 0.5, part.pos().getY() + 0.5, part.pos().getZ() + 0.5);
            if (ticks % SWING_TICKS == 0) {
                unit.swing(InteractionHand.MAIN_HAND);
            }
            if (++ticks < settings.ticksPerBlock()) {
                return Status.RUNNING;
            }
            SitePlacement.place((ServerLevel) unit.level(), part, partner, stock, unit);
            return Status.DONE;
        }

        @Override
        public void stop(Unit unit) {
            SiteReservations.release(site.origin(), part.pos(), unit.getUUID());
        }

        @Override
        public int timeoutTicks() {
            return settings.ticksPerBlock() + 20;
        }

        @Override
        public String describe() {
            return "place " + part.state().getBlock().getName().getString() + " at " + part.pos().toShortString();
        }
    }
}
