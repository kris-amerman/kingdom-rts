package com.krisamerman.kingdomrts.unit;

import com.krisamerman.kingdomrts.unit.job.JobGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

// The builder occupation (type "kingdomrts:builder"): one of its settlement's builders, hired at a
// builders' hut (its workplace), and assigned to construction sites, where it works until
// the building is done (see BuilderJob). Its settings come from the JSON entry, e.g.
//   { "type": "kingdomrts:builder", "movement_speed": 0.3, "ticks_per_block": 10 }
// where ticks_per_block is how long it works on each block it places (optional, 10 by default:
// two blocks a second). Build speed needs playtesting, so it lives in data.
public record BuilderOccupation(double movementSpeed, int ticksPerBlock) implements Occupation {
    public static final int DEFAULT_TICKS_PER_BLOCK = 10;

    public static final MapCodec<BuilderOccupation> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.fieldOf("movement_speed").forGetter(BuilderOccupation::movementSpeed),
            Codec.intRange(1, 20 * 60).optionalFieldOf("ticks_per_block", DEFAULT_TICKS_PER_BLOCK).forGetter(BuilderOccupation::ticksPerBlock)
    ).apply(instance, BuilderOccupation::new));

    @Override
    public OccupationType type() {
        return OccupationType.BUILDER;
    }

    @Override
    public void applyTo(Unit unit) {
        unit.setBaseValue(Attributes.MOVEMENT_SPEED, movementSpeed);
        unit.clearRestriction(); // it goes where its site is, not just near its post
        unit.addOccupationGoal(false, 2, new JobGoal(unit, new BuilderJob(this)));
    }

    // Nothing for now; builders will handle materials in S-025.
    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return false;
    }
}
