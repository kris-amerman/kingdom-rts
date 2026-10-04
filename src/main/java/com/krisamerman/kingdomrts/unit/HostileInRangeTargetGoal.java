package com.krisamerman.kingdomrts.unit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

// Target selection for a MilitaryUnit: picks the nearest hostile mob within the unit's attack
// range, and drops it once it leaves that range. Dropping the target is what ends a fight.
//
// Built on vanilla's NearestAttackableTargetGoal (which handles searching, line of sight and
// target validity); this subclass only adds the attack-range limit.
public class HostileInRangeTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private final MilitaryUnit unit;

    public HostileInRangeTargetGoal(MilitaryUnit unit) {
        // 5 = check for targets on average every 5 ticks; true = must have line of sight;
        // false = doesn't need a walkable path (the unit doesn't chase anyway).
        // Only hostile mobs qualify, so a unit never targets another unit, of any faction.
        super(unit, LivingEntity.class, 5, true, false,
                target -> MilitaryUnit.isHostileMob(target) && unit.isInAttackRange(target));
        this.unit = unit;
    }

    // Called each tick while the unit has a target. Vanilla keeps a target out to the unit's
    // follow range (16 blocks); this also lets it go as soon as it's out of attack range.
    @Override
    public boolean canContinueToUse() {
        LivingEntity target = unit.getTarget();
        return target != null && unit.isInAttackRange(target) && super.canContinueToUse();
    }
}
