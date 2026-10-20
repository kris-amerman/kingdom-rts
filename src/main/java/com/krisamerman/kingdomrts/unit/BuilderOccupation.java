package com.krisamerman.kingdomrts.unit;

import com.krisamerman.kingdomrts.unit.job.JobGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

// The builder occupation (type "kingdomrts:builder"): one of its settlement's pool of builders,
// hired at the town hall (no workplace), and assigned to construction sites, where it works until
// the building is done (see BuilderJob). Its settings come from the JSON entry, e.g.
//   { "type": "kingdomrts:builder", "movement_speed": 0.3 }
public record BuilderOccupation(double movementSpeed) implements Occupation {
    public static final MapCodec<BuilderOccupation> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.fieldOf("movement_speed").forGetter(BuilderOccupation::movementSpeed)
    ).apply(instance, BuilderOccupation::new));

    @Override
    public OccupationType type() {
        return OccupationType.BUILDER;
    }

    @Override
    public void applyTo(Unit unit) {
        unit.setBaseValue(Attributes.MOVEMENT_SPEED, movementSpeed);
        unit.clearRestriction(); // it goes where its site is, not just near its post
        unit.addOccupationGoal(false, 2, new JobGoal(unit, new BuilderJob()));
    }

    // Nothing for now; builders will handle materials in S-025.
    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return false;
    }
}
