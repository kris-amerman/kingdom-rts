package com.krisamerman.kingdomrts.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingRules;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.ConstructionSiteBlockEntity;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.SitePlacement;
import com.krisamerman.kingdomrts.settlement.SiteReservations;
import com.krisamerman.kingdomrts.unit.job.Footing;
import com.krisamerman.kingdomrts.unit.job.Job;
import com.krisamerman.kingdomrts.unit.job.JobStep;
import com.krisamerman.kingdomrts.unit.job.WaitStep;
import com.krisamerman.kingdomrts.unit.job.WalkToStep;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;

// The builder's job, as explicit steps. In the pool (not assigned), it goes back to its post, the
// town hall, and waits. Assigned to a construction site, it walks there and builds:
//   plan (work out the missing blocks, a batch per tick, in build order: see BuildingRules) ->
//   for each planned block it can place now: reserve it (so builders sharing the site don't go
//   for the same block), get within reach, work on it for ticks_per_block, place it from the
//   site's stock (see SitePlacement) -> when the plan runs out, plan again -> when nothing is
//   missing, the site is completed (BuildingOps#completeIfDone) and it's back in the pool.
// Blocks it can't place yet (no materials in the stock, nothing to hang on yet, something in the
// way) are left for the next plan; with nothing placeable it waits a little and plans again, so
// it carries on by itself when materials arrive. Per tick it only waits, walks, or counts down to
// the next block; planning is spread over ticks and only happens when a plan runs out.
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

    private final BuilderOccupation settings;
    @Nullable
    private JobStep walk;
    // The site being planned or built (its building's origin), the planned blocks in build order,
    // and how far through them it is.
    @Nullable
    private BlockPos planSite;
    private final List<Blueprint.Part> plan = new ArrayList<>();
    private final Map<BlockPos, Blueprint.Part> planByPos = new HashMap<>();
    // Blocks it couldn't walk to during this plan, and the block it's walking to now.
    private final Set<BlockPos> unreachable = new HashSet<>();
    @Nullable
    private BlockPos walkingTo;
    // Whether the last plan found anything missing at all (if not, the site is done).
    private boolean planFoundMissing;

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
        if (site == null || site.site() == null) {
            clearPlan();
            return goTo(unit, unit.getPost(), AT_POST, walkFailed, "waiting for a site", () -> !assigned(unit));
        }
        if (!site.origin().equals(planSite)) {
            clearPlan();
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
            planFoundMissing = !plan.isEmpty();
        } else if (planSite == null) {
            return new PlanStep(site);
        }
        // The next block to place: the first planned block (in build order) it can place now and
        // reach from where it stands; failing that, the first it can place at all, to walk to. Only
        // the first CANDIDATES_PER_LOOK planned blocks are looked at, so a big building's plan
        // doesn't make each look expensive; placed blocks drop out of the plan as it goes.
        ConstructionSiteBlockEntity stock = level.getBlockEntity(site.site()) instanceof ConstructionSiteBlockEntity entity ? entity : null;
        long now = level.getGameTime();
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
        boolean allPlaced = plan.isEmpty();
        clearPlan();
        if (foundMissing && allPlaced) {
            return new PlanStep(site); // everything planned is in place: check again straight away
        }
        if (!foundMissing) {
            BuildingOps.completeIfDone(level, settlement, site);
            return new WaitStep(WAIT_TICKS, "finishing up");
        }
        return new WaitStep(RETRY_TICKS, "waiting for materials");
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
                if (!part.state().isAir() && !part.pos().equals(site.site())
                        && unit.level().isLoaded(part.pos()) && !BuildingRules.matches(part.state(), unit.level().getBlockState(part.pos()))) {
                    missing.add(part);
                }
            }
            if (next < placed.size()) {
                return Status.RUNNING;
            }
            missing.sort(BuildingRules.BUILD_ORDER);
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
