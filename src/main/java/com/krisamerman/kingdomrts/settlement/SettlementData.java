package com.krisamerman.kingdomrts.settlement;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.util.NameRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

// All settlements in a world, plus an index of which settlement claims each chunk.
// Saved like FactionData: <world>/data/kingdomrts_settlements.dat, kept in the overworld's storage
// so every dimension shares it.
public class SettlementData extends SavedData {
    private static final String FILE_NAME = "kingdomrts_settlements";
    private static final String SETTLEMENTS_TAG = "settlements";

    private static final SavedData.Factory<SettlementData> FACTORY = new SavedData.Factory<>(SettlementData::new, SettlementData::load);

    // LinkedHashMap keeps founding order, so listings are stable.
    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
    // Which settlement claims each chunk. Not saved: rebuilt from the settlements on load.
    // Answers "who owns this chunk?" with one map lookup instead of searching every settlement.
    private final Map<ClaimKey, UUID> claimIndex = new HashMap<>();

    // A chunk in a particular dimension (chunk coordinates repeat across dimensions).
    private record ClaimKey(ResourceKey<Level> dimension, long chunk) {
        static ClaimKey of(ResourceKey<Level> dimension, ChunkPos pos) {
            return new ClaimKey(dimension, pos.toLong());
        }
    }

    public static SettlementData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static SettlementData get(ServerLevel level) {
        return get(level.getServer());
    }

    // ---- Lookups ----

    @Nullable
    public Settlement byId(@Nullable UUID id) {
        return id == null ? null : settlements.get(id);
    }

    // Case-insensitive.
    @Nullable
    public Settlement byName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (Settlement settlement : settlements.values()) {
            if (settlement.name().toLowerCase(Locale.ROOT).equals(lower)) {
                return settlement;
            }
        }
        return null;
    }

    // The settlement that claims this chunk, or null if it's unclaimed.
    @Nullable
    public Settlement claimAt(ResourceKey<Level> dimension, ChunkPos chunk) {
        return byId(claimIndex.get(ClaimKey.of(dimension, chunk)));
    }

    public Collection<Settlement> all() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    public List<String> names() {
        return settlements.values().stream().map(Settlement::name).toList();
    }

    // Returns why a name can't be used for a settlement, or null if it's fine.
    @Nullable
    public String nameProblem(String name) {
        String formatProblem = NameRules.formatProblem("Settlement", name);
        if (formatProblem != null) {
            return formatProblem;
        }
        if (byName(name) != null) {
            return "A settlement named " + name + " already exists";
        }
        return null;
    }

    // ---- Changes (callers go through SettlementOps, which checks the rules first) ----

    Settlement create(String name, UUID owner, ResourceKey<Level> dimension, BlockPos townHall, Set<ChunkPos> chunks) {
        Settlement settlement = new Settlement(UUID.randomUUID(), name, owner, dimension, townHall, chunks);
        settlements.put(settlement.id(), settlement);
        index(settlement);
        setDirty();
        return settlement;
    }

    // Removes a settlement and releases its claim.
    void dissolve(Settlement settlement) {
        settlements.remove(settlement.id());
        for (ChunkPos chunk : settlement.chunks()) {
            claimIndex.remove(ClaimKey.of(settlement.dimension(), chunk));
        }
        setDirty();
    }

    // The settlement and its claimed chunks pass to the new owner together.
    void setOwner(Settlement settlement, UUID owner) {
        settlement.setOwner(owner);
        setDirty();
    }

    void addUnit(Settlement settlement, UUID unit) {
        if (settlement.addUnit(unit)) {
            setDirty();
        }
    }

    void removeUnit(Settlement settlement, UUID unit) {
        if (settlement.removeUnit(unit)) {
            setDirty();
        }
    }

    void addStorage(Settlement settlement, BlockPos pos) {
        if (settlement.addStorage(pos)) {
            setDirty();
        }
    }

    void removeStorage(Settlement settlement, BlockPos pos) {
        if (settlement.removeStorage(pos)) {
            setDirty();
        }
    }

    void rename(Settlement settlement, String name) {
        settlement.setName(name);
        setDirty();
    }

    private void index(Settlement settlement) {
        for (ChunkPos chunk : settlement.chunks()) {
            claimIndex.put(ClaimKey.of(settlement.dimension(), chunk), settlement.id());
        }
    }

    // ---- Saving / loading ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        settlements.values().forEach(settlement -> list.add(settlement.save()));
        tag.put(SETTLEMENTS_TAG, list);
        return tag;
    }

    private static SettlementData load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementData data = new SettlementData();
        for (Tag t : tag.getList(SETTLEMENTS_TAG, Tag.TAG_COMPOUND)) {
            Settlement settlement = Settlement.load((CompoundTag) t);
            data.settlements.put(settlement.id(), settlement);
            data.index(settlement);
        }
        return data;
    }
}
