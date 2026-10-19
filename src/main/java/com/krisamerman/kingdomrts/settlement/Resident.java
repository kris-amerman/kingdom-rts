package com.krisamerman.kingdomrts.settlement;

import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;

// What a settlement remembers about one of its units, so it can list them even while they're
// unloaded (the town hall's population page): its name and occupation. Kept up to date by
// SettlementOps#refreshResident when the unit joins, is hired or fired, and whenever it loads.
public record Resident(String name, @Nullable ResourceLocation occupation) {
    // For units saved before residents were remembered; filled in when the unit next loads.
    public static final Resident UNKNOWN = new Resident("", null);

    private static final String ID_TAG = "id";
    private static final String NAME_TAG = "name";
    private static final String OCCUPATION_TAG = "occupation";

    public static Resident of(Unit unit) {
        return new Resident(unit.getName().getString(), unit.getOccupationId());
    }

    CompoundTag save(UUID id) {
        CompoundTag tag = new CompoundTag();
        tag.put(ID_TAG, NbtUtils.createUUID(id));
        tag.putString(NAME_TAG, name);
        if (occupation != null) {
            tag.putString(OCCUPATION_TAG, occupation.toString());
        }
        return tag;
    }

    static UUID loadId(CompoundTag tag) {
        return NbtUtils.loadUUID(tag.get(ID_TAG));
    }

    static Resident load(CompoundTag tag) {
        return new Resident(tag.getString(NAME_TAG),
                tag.contains(OCCUPATION_TAG) ? ResourceLocation.tryParse(tag.getString(OCCUPATION_TAG)) : null);
    }
}
