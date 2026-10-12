package com.krisamerman.kingdomrts.settlement;

import static com.krisamerman.kingdomrts.settlement.TownHallBlockEntity.CONTROL_CHANGE_PER_CHECK;
import static com.krisamerman.kingdomrts.settlement.TownHallBlockEntity.MAX_CONTROL;
import static com.krisamerman.kingdomrts.settlement.TownHallBlockEntity.MIN_CONTROL;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

// The capture rule as a pure function: given this check's control and presence, what happens.
// No world access, so it can be unit tested on its own; TownHallBlockEntity#serverTick counts the
// units and applies the outcome (transfer, boss bar, alerts).
//
// Majority rule: each hostile faction is compared with the defenders on its own (hostile factions
// aren't added together; they may be at war with each other too). The strongest hostile faction
// outnumbers the defenders -> drain; the defenders outnumber it, or there are no hostile units at
// all -> regenerate; equal -> hold.
//
// Capture: when a drain would take control below 1, the settlement changes owner on this check,
// to the hostile faction with the most units present, at control 1 in its favor. On a tie for
// most attackers, control holds at 1 until one faction leads.
public final class CaptureRules {
    // Which way control is moving this check.
    public enum Trend { DRAINING, HOLDING, RISING }

    // newControl is the control after this check (MIN_CONTROL on a capture, for the new owner).
    // capturedBy is the new owner, or null if the settlement doesn't change hands this check.
    public record Outcome(int newControl, Trend trend, int strongestAttackers, @Nullable UUID capturedBy, boolean tiedForCapture) {
        public boolean draining() {
            return trend == Trend.DRAINING;
        }
    }

    private CaptureRules() {
    }

    // The faction a unit of a captured settlement ends up with: units with a military occupation
    // keep theirs (and stay hostile to the new owner); every other unit passes to the new owner.
    @Nullable
    public static UUID factionAfterCapture(@Nullable UUID unitFaction, boolean military, UUID newOwner) {
        return military ? unitFaction : newOwner;
    }

    public static Outcome evaluate(int control, int defenders, Map<UUID, Integer> attackersByFaction) {
        int strongestAttackers = attackersByFaction.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (strongestAttackers > defenders) {
            int newControl = control - CONTROL_CHANGE_PER_CHECK;
            if (newControl >= MIN_CONTROL) {
                return new Outcome(newControl, Trend.DRAINING, strongestAttackers, null, false);
            }
            UUID capturedBy = strongest(attackersByFaction);
            return new Outcome(MIN_CONTROL, Trend.DRAINING, strongestAttackers, capturedBy, capturedBy == null);
        }
        if (strongestAttackers < defenders || strongestAttackers == 0) {
            return new Outcome(Math.min(control + CONTROL_CHANGE_PER_CHECK, MAX_CONTROL), Trend.RISING, strongestAttackers, null, false);
        }
        return new Outcome(control, Trend.HOLDING, strongestAttackers, null, false);
    }

    // The faction with the most units, or null if there's a tie for most (or no one).
    @Nullable
    static UUID strongest(Map<UUID, Integer> countsByFaction) {
        UUID best = null;
        int bestCount = 0;
        boolean tied = false;
        for (Map.Entry<UUID, Integer> entry : countsByFaction.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
                tied = false;
            } else if (entry.getValue() == bestCount) {
                tied = true;
            }
        }
        return tied ? null : best;
    }
}
