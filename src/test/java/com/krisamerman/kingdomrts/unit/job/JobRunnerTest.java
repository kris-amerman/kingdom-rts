package com.krisamerman.kingdomrts.unit.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.krisamerman.kingdomrts.unit.Unit;

// The step runner on its own, with scripted steps (no unit needed, so null is passed).
class JobRunnerTest {
    // A step that reports a fixed status after a number of ticks (or never, if ticks < 0).
    private static final class ScriptedStep implements JobStep {
        final String name;
        final int ticks;
        final Status result;
        final int timeout;
        int ticked;
        boolean stopped;

        ScriptedStep(String name, int ticks, Status result, int timeout) {
            this.name = name;
            this.ticks = ticks;
            this.result = result;
            this.timeout = timeout;
        }

        @Override
        public Status tick(Unit unit) {
            return ++ticked == ticks ? result : Status.RUNNING;
        }

        @Override
        public void stop(Unit unit) {
            stopped = true;
        }

        @Override
        public int timeoutTicks() {
            return timeout;
        }

        @Override
        public String describe() {
            return name;
        }
    }

    // Hands out steps in order and records how each previous step went.
    private static final class ScriptedJob implements Job {
        final List<JobStep> steps;
        final List<String> history = new ArrayList<>();
        int index;

        ScriptedJob(JobStep... steps) {
            this.steps = List.of(steps);
        }

        @Override
        public JobStep next(Unit unit, JobStep previous, boolean succeeded) {
            history.add(previous == null ? "start" : previous.describe() + (succeeded ? " done" : " failed"));
            return steps.get(index++);
        }
    }

    @Test
    void runsStepsInOrderAndReportsHowEachWent() {
        ScriptedStep walk = new ScriptedStep("walk", 3, JobStep.Status.DONE, 100);
        ScriptedStep chop = new ScriptedStep("chop", 2, JobStep.Status.FAILED, 100);
        ScriptedStep idle = new ScriptedStep("idle", -1, JobStep.Status.DONE, 100);
        ScriptedJob job = new ScriptedJob(walk, chop, idle);
        JobRunner runner = new JobRunner(job);
        for (int i = 0; i < 6; i++) {
            runner.tick(null);
        }
        assertEquals(List.of("start", "walk done", "chop failed"), job.history);
        assertSame(idle, runner.current());
        assertEquals(true, walk.stopped);
        assertEquals(true, chop.stopped);
    }

    @Test
    void aStepThatRunsOutOfTimeFails() {
        ScriptedStep stuck = new ScriptedStep("stuck", -1, JobStep.Status.DONE, 5);
        ScriptedStep next = new ScriptedStep("next", -1, JobStep.Status.DONE, 100);
        ScriptedJob job = new ScriptedJob(stuck, next);
        JobRunner runner = new JobRunner(job);
        for (int i = 0; i < 5; i++) {
            runner.tick(null);
        }
        runner.tick(null); // asks for the next step
        assertEquals(List.of("start", "stuck failed"), job.history);
        assertEquals(5, stuck.ticked);
        assertEquals(true, stuck.stopped);
    }

    @Test
    void stoppingEndsTheCurrentStepAsUnfinished() {
        ScriptedStep walk = new ScriptedStep("walk", -1, JobStep.Status.DONE, 100);
        ScriptedStep again = new ScriptedStep("again", -1, JobStep.Status.DONE, 100);
        ScriptedJob job = new ScriptedJob(walk, again);
        JobRunner runner = new JobRunner(job);
        runner.tick(null);
        runner.stop(null);
        runner.tick(null);
        assertEquals(true, walk.stopped);
        assertEquals(List.of("start", "walk failed"), job.history);
    }
}
