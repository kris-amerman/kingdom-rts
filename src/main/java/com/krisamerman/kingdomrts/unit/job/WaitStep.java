package com.krisamerman.kingdomrts.unit.job;

import com.krisamerman.kingdomrts.unit.Unit;

// Stands still for a while: used to back off before trying again, or to idle when there's nothing
// to do.
public final class WaitStep implements JobStep {
    private final int ticks;
    private final String reason;
    private int waited;

    public WaitStep(int ticks, String reason) {
        this.ticks = ticks;
        this.reason = reason;
    }

    @Override
    public void start(Unit unit) {
        waited = 0;
    }

    @Override
    public Status tick(Unit unit) {
        return ++waited >= ticks ? Status.DONE : Status.RUNNING;
    }

    @Override
    public int timeoutTicks() {
        return ticks + 1;
    }

    @Override
    public String describe() {
        return "wait (" + reason + ")";
    }
}
