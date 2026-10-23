package com.krisamerman.kingdomrts.unit.job;

import java.util.EnumSet;

import com.krisamerman.kingdomrts.unit.Unit;
import com.krisamerman.kingdomrts.util.Perf;

import net.minecraft.world.entity.ai.goal.Goal;

// Runs a unit's job as an AI goal. While the job runs it holds MOVE and LOOK, so the idle goals
// every unit has (strolling, looking around) stay paused.
public class JobGoal extends Goal {
    private final Unit unit;
    private final JobRunner runner;

    public JobGoal(Unit unit, Job job) {
        this.unit = unit;
        this.runner = new JobRunner(job);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return unit.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // Timed, so the profiler and the stress test show what jobs cost.
        long start = Perf.start(unit.level(), Perf.Section.JOB);
        runner.tick(unit);
        Perf.end(unit.level(), Perf.Section.JOB, start);
    }

    // What the job is doing right now, e.g. "taking down oak_planks at 1, 2, 3" (for diagnostics).
    public String activity() {
        JobStep step = runner.current();
        return step != null ? step.describe() : "between steps";
    }

    @Override
    public void stop() {
        runner.stop(unit);
    }
}
