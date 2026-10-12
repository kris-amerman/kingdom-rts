package com.krisamerman.kingdomrts.unit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;

// The military occupation (type "kingdomrts:military"): holds position near its post, attacks
// hostile mobs and units of hostile factions within range, and counts as its faction's presence
// at a capture point. Its combat stats come from the JSON entry, e.g.
//   { "type": "kingdomrts:military", "max_health": 24, "movement_speed": 0.3,
//     "attack_damage": 4, "attack_range": 2.5 }
//
// A Java "record" is a compact immutable data class: the fields, constructor, getters
// (maxHealth(), etc.), equals and hashCode are all generated from the header line.
public record MilitaryOccupation(double maxHealth, double movementSpeed, double attackDamage, double attackRange)
        implements Occupation {
    // A MapCodec reads fields that sit next to "type" in the same JSON object (see Occupation.CODEC).
    // The field names here are the JSON keys.
    public static final MapCodec<MilitaryOccupation> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.fieldOf("max_health").forGetter(MilitaryOccupation::maxHealth),
            Codec.DOUBLE.fieldOf("movement_speed").forGetter(MilitaryOccupation::movementSpeed),
            Codec.DOUBLE.fieldOf("attack_damage").forGetter(MilitaryOccupation::attackDamage),
            Codec.DOUBLE.fieldOf("attack_range").forGetter(MilitaryOccupation::attackRange)
    ).apply(instance, MilitaryOccupation::new));

    @Override
    public OccupationType type() {
        return OccupationType.MILITARY;
    }

    // Goals are the vanilla mob AI building blocks; lower number = higher priority. These slot in
    // around the goals every unit has (see Unit#registerGoals).
    //
    // The unit's restriction to its post does the "hold position" work: random strolls stay inside
    // it, and MoveTowardsRestrictionGoal walks the unit back if it ends up outside (e.g. knocked
    // back during a fight). Once a fight ends (target dead or out of range, see
    // HostileInRangeTargetGoal), the attack goal stops and these goals take over again.
    @Override
    public void applyTo(Unit unit) {
        unit.setBaseValue(Attributes.MAX_HEALTH, maxHealth);
        unit.setBaseValue(Attributes.MOVEMENT_SPEED, movementSpeed);
        unit.setBaseValue(Attributes.ATTACK_DAMAGE, attackDamage);
        unit.setAttackRange(attackRange);
        unit.addOccupationGoal(false, 1, new HoldPostAttackGoal(unit));
        unit.addOccupationGoal(false, 2, new MoveTowardsRestrictionGoal(unit, 1.0));
        unit.addOccupationGoal(true, 1, new HostileInRangeTargetGoal(unit));
    }

    @Override
    public boolean isMilitary() {
        return true;
    }
}
