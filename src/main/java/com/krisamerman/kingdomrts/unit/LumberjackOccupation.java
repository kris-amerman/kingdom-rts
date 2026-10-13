package com.krisamerman.kingdomrts.unit;

import com.krisamerman.kingdomrts.unit.job.JobGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// The lumberjack occupation (type "kingdomrts:lumberjack"): fells trees in its settlement's claim
// and keeps the logs (see LumberjackJob). Its settings come from the JSON entry, e.g.
//   { "type": "kingdomrts:lumberjack", "movement_speed": 0.3 }
// How long each log takes isn't a setting: it's what a player would take with the same axe.
public record LumberjackOccupation(double movementSpeed) implements Occupation {
    public static final MapCodec<LumberjackOccupation> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.fieldOf("movement_speed").forGetter(LumberjackOccupation::movementSpeed)
    ).apply(instance, LumberjackOccupation::new));

    @Override
    public OccupationType type() {
        return OccupationType.LUMBERJACK;
    }

    @Override
    public void applyTo(Unit unit) {
        unit.setBaseValue(Attributes.MOVEMENT_SPEED, movementSpeed);
        unit.clearRestriction(); // a worker goes where its work is, not just near its post
        unit.addOccupationGoal(false, 2, new JobGoal(unit, new LumberjackJob(this)));
    }

    // What trees drop: logs, saplings, sticks and apples (it brings them all back to its hut).
    // Other things lying around are left alone, so it doesn't fill up on trash.
    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return stack.is(ItemTags.LOGS) || stack.is(ItemTags.SAPLINGS) || stack.is(Items.STICK) || stack.is(Items.APPLE);
    }
}
