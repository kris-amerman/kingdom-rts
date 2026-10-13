package com.krisamerman.kingdomrts.unit.job;

import java.util.EnumSet;

import com.krisamerman.kingdomrts.unit.Unit;

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
        // A profiler section, so Minecraft's profiler (and tools like Spark) show what jobs cost.
        unit.level().getProfiler().push("kingdomrts:job");
        runner.tick(unit);
        unit.level().getProfiler().pop();
    }

    @Override
    public void stop() {
        runner.stop(unit);
    }
}
