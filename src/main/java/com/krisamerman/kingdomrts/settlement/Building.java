package com.krisamerman.kingdomrts.settlement;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

// A building of a settlement, from when it's commissioned: what it is (a BuildingType id), where its
// template's corner goes and how it's turned, the box it takes up, and when it was commissioned and
// built (game time; built is NOT_BUILT until then). While it's being built, site is where its
// construction site block stands (see ConstructionSiteBlock); once built it's null. The box is kept
// so overlap checks don't need the template. Kept on the Settlement, like workplaces; only
// SettlementData changes it.
public record Building(ResourceLocation type, BlockPos origin, Rotation rotation, BoundingBox footprint, @Nullable BlockPos site,
        long commissioned, long built) {
    public static final long NOT_BUILT = -1;

    private static final String TYPE_TAG = "type";
    private static final String ORIGIN_TAG = "origin";
    private static final String ROTATION_TAG = "rotation";
    private static final String FOOTPRINT_TAG = "footprint";
    private static final String SITE_TAG = "site";
    private static final String COMMISSIONED_TAG = "commissioned";
    private static final String BUILT_TAG = "built";

    public Building {
        origin = origin.immutable();
        site = site != null ? site.immutable() : null;
    }

    public boolean isBuilt() {
        return built != NOT_BUILT;
    }

    // The same building, finished at the given game time: no longer a construction site.
    Building finishedAt(long gameTime) {
        return new Building(type, origin, rotation, footprint, null, commissioned, gameTime);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TYPE_TAG, type.toString());
        tag.putLong(ORIGIN_TAG, origin.asLong());
        tag.putString(ROTATION_TAG, rotation.getSerializedName());
        tag.put(FOOTPRINT_TAG, new IntArrayTag(new int[] {footprint.minX(), footprint.minY(), footprint.minZ(),
                footprint.maxX(), footprint.maxY(), footprint.maxZ()}));
        if (site != null) {
            tag.putLong(SITE_TAG, site.asLong());
        }
        tag.putLong(COMMISSIONED_TAG, commissioned);
        tag.putLong(BUILT_TAG, built);
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
        BlockPos site = tag.contains(SITE_TAG) ? BlockPos.of(tag.getLong(SITE_TAG)) : null;
        long built = tag.contains(BUILT_TAG) ? tag.getLong(BUILT_TAG) : NOT_BUILT;
        return new Building(ResourceLocation.parse(tag.getString(TYPE_TAG)), origin, rotation, footprint, site, tag.getLong(COMMISSIONED_TAG), built);
    }
}
