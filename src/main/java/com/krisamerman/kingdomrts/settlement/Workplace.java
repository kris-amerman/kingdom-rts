package com.krisamerman.kingdomrts.settlement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

// A settlement's workplace (say, a lumberjack hut): where its block is, what kind it is (a
// WorkplaceType id, the same as the block's id) and who works there. Workers are kept with the name
// they had when hired, so the workplace can list them even while they're unloaded. Kept on the
// Settlement, so it doesn't depend on the workplace's chunk being loaded; only SettlementData
// changes it.
public final class Workplace {
    private static final String POS_TAG = "pos";
    private static final String TYPE_TAG = "type";
    private static final String WORKERS_TAG = "workers";
    private static final String ID_TAG = "id";
    private static final String NAME_TAG = "name";

    private final BlockPos pos;
    private final ResourceLocation type;
    private final Map<UUID, String> workers = new LinkedHashMap<>();

    Workplace(BlockPos pos, ResourceLocation type) {
        this.pos = pos.immutable();
        this.type = type;
    }

    public BlockPos pos() {
        return pos;
    }

    public ResourceLocation type() {
        return type;
    }

    // Worker unit UUIDs, in hiring order, with their names.
    public Map<UUID, String> workers() {
        return Collections.unmodifiableMap(workers);
    }

    boolean addWorker(UUID unit, String name) {
        return workers.put(unit, name) == null;
    }

    boolean removeWorker(UUID unit) {
        return workers.remove(unit) != null;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(POS_TAG, pos.asLong());
        tag.putString(TYPE_TAG, type.toString());
        ListTag list = new ListTag();
        workers.forEach((id, name) -> {
            CompoundTag worker = new CompoundTag();
            worker.putUUID(ID_TAG, id);
            worker.putString(NAME_TAG, name);
            list.add(worker);
        });
        tag.put(WORKERS_TAG, list);
        return tag;
    }

    static Workplace load(CompoundTag tag) {
        Workplace workplace = new Workplace(BlockPos.of(tag.getLong(POS_TAG)), ResourceLocation.parse(tag.getString(TYPE_TAG)));
        for (Tag t : tag.getList(WORKERS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag worker = (CompoundTag) t;
            workplace.workers.put(worker.getUUID(ID_TAG), worker.getString(NAME_TAG));
        }
        return workplace;
    }
}
