package com.krisamerman.kingdomrts.unit;

import java.util.EnumSet;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

// Attacks the unit's current target while it's within attack range, without chasing it:
// the unit holds its ground. Damage comes from the ATTACK_DAMAGE attribute (set from data).
public class HoldPostAttackGoal extends Goal {
    // Ticks between swings (20 = 1 second, same as vanilla melee mobs). Behavior tuning, not a stat.
    public static final int ATTACK_COOLDOWN_TICKS = 20;

    private final MilitaryUnit unit;
    private int cooldown;

    public HoldPostAttackGoal(MilitaryUnit unit) {
        this.unit = unit;
        // MOVE and LOOK: while fighting, lower-priority goals that move or turn the unit
        // (wandering, looking around) are paused.
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = unit.getTarget();
        return target != null && target.isAlive() && unit.isInAttackRange(target);
    }

    @Override
    public void start() {
        unit.getNavigation().stop();
        cooldown = 0; // first swing right away
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = unit.getTarget();
        if (target == null) {
            return;
        }
        unit.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (unit.getSensing().hasLineOfSight(target)) {
            unit.swing(InteractionHand.MAIN_HAND); // arm swing animation
            unit.doHurtTarget(target);             // vanilla damage using ATTACK_DAMAGE
            cooldown = ATTACK_COOLDOWN_TICKS;
        }
    }
}
