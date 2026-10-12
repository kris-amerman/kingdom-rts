package com.krisamerman.kingdomrts.unit.job;

import com.krisamerman.kingdomrts.unit.Unit;

// One explicit step of a job, such as "walk to the tree" or "chop it down". A step runs a bit each
// tick until it reports DONE or FAILED. Every step has a time limit: a step still RUNNING when it
// runs out counts as FAILED, so a unit that can't make progress never stalls forever. The job then
// decides how to recover (see Job#next).
public interface JobStep {
    enum Status { RUNNING, DONE, FAILED }

    default void start(Unit unit) {
    }

    Status tick(Unit unit);

    // Called when the step ends for any reason (done, failed, timed out, or the job was stopped).
    default void stop(Unit unit) {
    }

    int timeoutTicks();

    // For logs and debugging.
    String describe();
}
