package com.krisamerman.kingdomrts.unit.job;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.unit.Unit;

// Runs a job one step at a time: starts each step, ticks it, enforces its time limit, and asks the
// job for the next step when it ends. Kept apart from the AI goal so it can be unit tested.
public final class JobRunner {
    private final Job job;
    @Nullable
    private JobStep current;
    @Nullable
    private JobStep previous;
    private boolean previousSucceeded;
    private int ticksInStep;

    public JobRunner(Job job) {
        this.job = job;
    }

    public void tick(Unit unit) {
        if (current == null) {
            current = job.next(unit, previous, previousSucceeded);
            ticksInStep = 0;
            current.start(unit);
        }
        JobStep.Status status = current.tick(unit);
        ticksInStep++;
        if (status == JobStep.Status.RUNNING && ticksInStep >= current.timeoutTicks()) {
            KingdomRts.LOGGER.debug("Job step '{}' ran out of time", current.describe());
            status = JobStep.Status.FAILED;
        }
        if (status != JobStep.Status.RUNNING) {
            end(unit, status == JobStep.Status.DONE);
        }
    }

    // Ends the current step without finishing it, e.g. when the unit's AI goal stops.
    public void stop(Unit unit) {
        if (current != null) {
            end(unit, false);
        }
    }

    @Nullable
    public JobStep current() {
        return current;
    }

    private void end(Unit unit, boolean succeeded) {
        current.stop(unit);
        previous = current;
        previousSucceeded = succeeded;
        current = null;
    }
}
