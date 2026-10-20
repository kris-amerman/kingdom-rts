package com.krisamerman.kingdomrts.unit;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.unit.job.Job;
import com.krisamerman.kingdomrts.unit.job.JobStep;
import com.krisamerman.kingdomrts.unit.job.WaitStep;
import com.krisamerman.kingdomrts.unit.job.WalkToStep;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// The builder's job, as explicit steps. Assigned to a construction site, it walks to the site's
// block and stays there (building comes in S-025); in the pool (not assigned), it goes back to its
// post, the town hall, and waits. It looks at its assignment again every few seconds, so being
// assigned, unassigned, or having its site finished or cancelled takes effect then. Cheap: one
// short look through its settlement's buildings each time a step ends, nothing per tick.
public final class BuilderJob implements Job {
    // How long it waits before looking at its assignment again.
    private static final int WAIT_TICKS = 40;
    private static final int RETRY_TICKS = 40;
    private static final int WALK_TIMEOUT_TICKS = 20 * 60;
    // Close enough to its site block to count as on site, and to its post to count as back.
    private static final double ON_SITE = 4.0;
    private static final double AT_POST = 4.0;

    @Nullable
    private JobStep walk;

    @Override
    public JobStep next(Unit unit, @Nullable JobStep previous, boolean succeeded) {
        boolean walkFailed = previous != null && previous == walk && !succeeded;
        if (!(unit.level() instanceof ServerLevel level)) {
            return new WaitStep(WAIT_TICKS, "client");
        }
        Settlement settlement = SettlementData.get(level).byId(unit.getSettlementId());
        Building site = settlement != null ? BuilderOps.siteOf(settlement, unit.getUUID()) : null;
        BlockPos target = site != null ? site.site() : unit.getPost();
        if (target == null) {
            return new WaitStep(WAIT_TICKS, "nowhere to go");
        }
        double reach = site != null ? ON_SITE : AT_POST;
        if (unit.position().distanceTo(target.getBottomCenter()) <= reach) {
            return new WaitStep(WAIT_TICKS, site != null ? "waiting at the site" : "waiting for a site");
        }
        if (walkFailed) {
            return new WaitStep(RETRY_TICKS, "retrying the walk");
        }
        walk = site != null
                ? WalkToStep.near(target, WALK_TIMEOUT_TICKS, () -> stillAssignedTo(unit, site))
                : WalkToStep.to(target, AT_POST - 1, WALK_TIMEOUT_TICKS, () -> !assigned(unit));
        return walk;
    }

    private static boolean stillAssignedTo(Unit unit, Building site) {
        return unit.level() instanceof ServerLevel level && SettlementData.get(level).byId(unit.getSettlementId()) instanceof Settlement settlement
                && BuilderOps.siteOf(settlement, unit.getUUID()) instanceof Building now && now.origin().equals(site.origin());
    }

    private static boolean assigned(Unit unit) {
        return unit.level() instanceof ServerLevel level && SettlementData.get(level).byId(unit.getSettlementId()) instanceof Settlement settlement
                && BuilderOps.siteOf(settlement, unit.getUUID()) != null;
    }
}
