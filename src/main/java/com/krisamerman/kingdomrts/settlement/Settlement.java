package com.krisamerman.kingdomrts.settlement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

// A settlement: a city around a town hall, owned by a faction, with the chunks it claims.
// Settlements live in SettlementData; the town hall block entity only keeps this settlement's id
// (plus its capture meter). Only SettlementData changes a settlement, so it can mark itself for saving.
public final class Settlement {
    private static final String ID_TAG = "id";
    private static final String NAME_TAG = "name";
    private static final String OWNER_TAG = "owner";
    private static final String DIMENSION_TAG = "dimension";
    private static final String HALL_TAG = "hall";
    private static final String CHUNKS_TAG = "chunks";
    private static final String STORAGE_TAG = "storage";
    private static final String UNITS_TAG = "units";
    private static final String OWNER_CHANGES_TAG = "owner_changes";
    private static final String WORKPLACES_TAG = "workplaces";
    private static final String BUILDINGS_TAG = "buildings";

    private final UUID id;
    private String name;
    // The owning faction (see FactionData). Every settlement in the MVP is owned; unclaimed ones
    // will be naturally generated settlements, which come later.
    private UUID owner;
    private final ResourceKey<Level> dimension;
    private final BlockPos townHall;
    // Claimed chunks, in this settlement's dimension. Fixed at founding for the MVP.
    private final Set<ChunkPos> chunks;
    // Positions of this settlement's Settlement Storage blocks (in its dimension).
    private final Set<BlockPos> storage = new LinkedHashSet<>();
    // The units that belong to this settlement (entity UUIDs; a unit may be unloaded).
    private final Set<UUID> units = new LinkedHashSet<>();
    // Counts changes of owner, so units that were unloaded during a capture can tell they missed
    // one when they load (see Unit#getSeenOwnerChanges).
    private int ownerChanges;
    // Its workplaces, by block position (in its dimension).
    private final Map<BlockPos, Workplace> workplaces = new LinkedHashMap<>();
    // Its commissioned buildings, by template origin (in its dimension).
    private final Map<BlockPos, Building> buildings = new LinkedHashMap<>();

    Settlement(UUID id, String name, UUID owner, ResourceKey<Level> dimension, BlockPos townHall, Set<ChunkPos> chunks) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.dimension = dimension;
        this.townHall = townHall.immutable();
        this.chunks = new LinkedHashSet<>(chunks);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID owner() {
        return owner;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos townHall() {
        return townHall;
    }

    public Set<ChunkPos> chunks() {
        return Collections.unmodifiableSet(chunks);
    }

    public Set<BlockPos> storage() {
        return Collections.unmodifiableSet(storage);
    }

    public Set<UUID> units() {
        return Collections.unmodifiableSet(units);
    }

    public Collection<Workplace> workplaces() {
        return Collections.unmodifiableCollection(workplaces.values());
    }

    @Nullable
    public Workplace workplaceAt(BlockPos pos) {
        return workplaces.get(pos);
    }

    boolean addWorkplace(Workplace workplace) {
        return workplaces.putIfAbsent(workplace.pos(), workplace) == null;
    }

    boolean removeWorkplace(BlockPos pos) {
        return workplaces.remove(pos) != null;
    }

    public Collection<Building> buildings() {
        return Collections.unmodifiableCollection(buildings.values());
    }

    @Nullable
    public Building buildingAt(BlockPos origin) {
        return buildings.get(origin);
    }

    // The building whose footprint includes pos, if any.
    @Nullable
    public Building buildingContaining(BlockPos pos) {
        for (Building building : buildings.values()) {
            if (building.footprint().isInside(pos)) {
                return building;
            }
        }
        return null;
    }

    boolean addBuilding(Building building) {
        return buildings.putIfAbsent(building.origin(), building) == null;
    }

    boolean removeBuilding(BlockPos origin) {
        return buildings.remove(origin) != null;
    }

    public int ownerChanges() {
        return ownerChanges;
    }

    boolean addUnit(UUID unit) {
        return units.add(unit);
    }

    boolean removeUnit(UUID unit) {
        return units.remove(unit);
    }

    boolean addStorage(BlockPos pos) {
        return storage.add(pos.immutable());
    }

    boolean removeStorage(BlockPos pos) {
        return storage.remove(pos);
    }

    void setName(String name) {
        this.name = name;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
        ownerChanges++;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID_TAG, id);
        tag.putString(NAME_TAG, name);
        tag.putUUID(OWNER_TAG, owner);
        tag.putString(DIMENSION_TAG, dimension.location().toString());
        tag.put(HALL_TAG, NbtUtils.writeBlockPos(townHall));
        // A ChunkPos packs into one long, so the claim saves as a compact long array.
        tag.put(CHUNKS_TAG, new LongArrayTag(chunks.stream().mapToLong(ChunkPos::toLong).toArray()));
        tag.put(STORAGE_TAG, new LongArrayTag(storage.stream().mapToLong(BlockPos::asLong).toArray()));
        ListTag unitList = new ListTag();
        units.forEach(unit -> unitList.add(NbtUtils.createUUID(unit)));
        tag.put(UNITS_TAG, unitList);
        tag.putInt(OWNER_CHANGES_TAG, ownerChanges);
        ListTag workplaceList = new ListTag();
        workplaces.values().forEach(workplace -> workplaceList.add(workplace.save()));
        tag.put(WORKPLACES_TAG, workplaceList);
        ListTag buildingList = new ListTag();
        buildings.values().forEach(building -> buildingList.add(building.save()));
        tag.put(BUILDINGS_TAG, buildingList);
        return tag;
    }

    static Settlement load(CompoundTag tag) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (long packed : tag.getLongArray(CHUNKS_TAG)) {
            chunks.add(new ChunkPos(packed));
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(DIMENSION_TAG)));
        BlockPos townHall = NbtUtils.readBlockPos(tag, HALL_TAG).orElse(BlockPos.ZERO);
        Settlement settlement = new Settlement(tag.getUUID(ID_TAG), tag.getString(NAME_TAG), tag.getUUID(OWNER_TAG), dimension, townHall, chunks);
        for (long packed : tag.getLongArray(STORAGE_TAG)) {
            settlement.storage.add(BlockPos.of(packed));
        }
        for (Tag unit : tag.getList(UNITS_TAG, Tag.TAG_INT_ARRAY)) {
            settlement.units.add(NbtUtils.loadUUID(unit));
        }
        settlement.ownerChanges = tag.getInt(OWNER_CHANGES_TAG);
        for (Tag workplace : tag.getList(WORKPLACES_TAG, Tag.TAG_COMPOUND)) {
            settlement.addWorkplace(Workplace.load((CompoundTag) workplace));
        }
        for (Tag building : tag.getList(BUILDINGS_TAG, Tag.TAG_COMPOUND)) {
            settlement.addBuilding(Building.load((CompoundTag) building));
        }
        return settlement;
    }
}
