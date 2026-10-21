package com.krisamerman.kingdomrts.settlement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

// A building of a settlement, from when it's commissioned: what it is (a BuildingType id), where its
// template's corner goes and how it's turned, the box it takes up, and when it was commissioned and
// built (game time; built is NOT_BUILT until then). While it's a site, site is where its
// construction site block stands (see ConstructionSiteBlock), and builders are the settlement's
// builders assigned to it (unit UUID to name, in assignment order). It stays a site after it's
// built, until its builders have carried what's left in the site's stock to storage; then the
// site is closed: site is null and nobody is assigned. The box is kept so overlap checks don't need the template. Kept on the
// Settlement, like workplaces; only SettlementData changes it.
public record Building(ResourceLocation type, BlockPos origin, Rotation rotation, BoundingBox footprint, @Nullable BlockPos site,
        long commissioned, long built, Map<UUID, String> builders) {
    public static final long NOT_BUILT = -1;

    private static final String TYPE_TAG = "type";
    private static final String ORIGIN_TAG = "origin";
    private static final String ROTATION_TAG = "rotation";
    private static final String FOOTPRINT_TAG = "footprint";
    private static final String SITE_TAG = "site";
    private static final String COMMISSIONED_TAG = "commissioned";
    private static final String BUILT_TAG = "built";
    private static final String BUILDERS_TAG = "builders";
    private static final String ID_TAG = "id";
    private static final String NAME_TAG = "name";

    public Building {
        origin = origin.immutable();
        site = site != null ? site.immutable() : null;
        builders = Collections.unmodifiableMap(new LinkedHashMap<>(builders));
    }

    // A building with nobody assigned to it.
    public Building(ResourceLocation type, BlockPos origin, Rotation rotation, BoundingBox footprint, @Nullable BlockPos site,
            long commissioned, long built) {
        this(type, origin, rotation, footprint, site, commissioned, built, Map.of());
    }

    public boolean isBuilt() {
        return built != NOT_BUILT;
    }

    // The same building, finished at the given game time. It's still a site (with the same
    // builders) until its stock is cleared out.
    Building finishedAt(long gameTime) {
        return new Building(type, origin, rotation, footprint, site, commissioned, gameTime, builders);
    }

    // The same building with its site closed: no construction site block, and its builders free again.
    Building withSiteClosed() {
        return new Building(type, origin, rotation, footprint, null, commissioned, built, Map.of());
    }

    // Built, but its site's stock is still being cleared out.
    public boolean isBeingClearedOut() {
        return isBuilt() && site != null;
    }

    // The same building with a builder assigned, or taken off it.
    Building withBuilder(UUID builder, String name) {
        Map<UUID, String> more = new LinkedHashMap<>(builders);
        more.put(builder, name);
        return new Building(type, origin, rotation, footprint, site, commissioned, built, more);
    }

    Building withoutBuilder(UUID builder) {
        Map<UUID, String> fewer = new LinkedHashMap<>(builders);
        fewer.remove(builder);
        return new Building(type, origin, rotation, footprint, site, commissioned, built, fewer);
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
        ListTag builderList = new ListTag();
        builders.forEach((id, name) -> {
            CompoundTag builder = new CompoundTag();
            builder.putUUID(ID_TAG, id);
            builder.putString(NAME_TAG, name);
            builderList.add(builder);
        });
        tag.put(BUILDERS_TAG, builderList);
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
        Map<UUID, String> builders = new LinkedHashMap<>();
        for (Tag t : tag.getList(BUILDERS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag builder = (CompoundTag) t;
            builders.put(builder.getUUID(ID_TAG), builder.getString(NAME_TAG));
        }
        return new Building(ResourceLocation.parse(tag.getString(TYPE_TAG)), origin, rotation, footprint, site, tag.getLong(COMMISSIONED_TAG),
                built, builders);
    }
}
