package com.krisamerman.kingdomrts.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

// A building commissioned for a settlement: what it is (a BuildingType id), where its template's
// corner goes and how it's turned, the box it takes up, and when it was commissioned (game time).
// The box is kept so overlap checks don't need the template. Kept on the Settlement, like
// workplaces; only SettlementData changes it.
public record Building(ResourceLocation type, BlockPos origin, Rotation rotation, BoundingBox footprint, long commissioned) {
    private static final String TYPE_TAG = "type";
    private static final String ORIGIN_TAG = "origin";
    private static final String ROTATION_TAG = "rotation";
    private static final String FOOTPRINT_TAG = "footprint";
    private static final String COMMISSIONED_TAG = "commissioned";

    public Building {
        origin = origin.immutable();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TYPE_TAG, type.toString());
        tag.putLong(ORIGIN_TAG, origin.asLong());
        tag.putString(ROTATION_TAG, rotation.getSerializedName());
        tag.put(FOOTPRINT_TAG, new IntArrayTag(new int[] {footprint.minX(), footprint.minY(), footprint.minZ(),
                footprint.maxX(), footprint.maxY(), footprint.maxZ()}));
        tag.putLong(COMMISSIONED_TAG, commissioned);
        return tag;
    }

    static Building load(CompoundTag tag) {
        int[] box = tag.getIntArray(FOOTPRINT_TAG);
        Rotation rotation = Rotation.NONE;
        for (Rotation r : Rotation.values()) {
            if (r.getSerializedName().equals(tag.getString(ROTATION_TAG))) {
                rotation = r;
            }
        }
        BlockPos origin = BlockPos.of(tag.getLong(ORIGIN_TAG));
        BoundingBox footprint = box.length == 6 ? new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]) : new BoundingBox(origin);
        return new Building(ResourceLocation.parse(tag.getString(TYPE_TAG)), origin, rotation, footprint, tag.getLong(COMMISSIONED_TAG));
    }
}
