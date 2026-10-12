package com.krisamerman.kingdomrts.unit.job;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.unit.Unit;

// A job is a loop of explicit steps (see JobStep). It holds whatever it needs to remember between
// steps (the tree it's felling, say) and picks each next step from how the last one went.
public interface Job {
    // The step to run next. previous is the step that just ended (null when the job starts) and
    // succeeded says whether it finished (false if it failed or ran out of time).
    JobStep next(Unit unit, @Nullable JobStep previous, boolean succeeded);
}
