package com.krisamerman.kingdomrts.faction;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

// A faction: a side that owns town halls and units. Factions are data (see FactionData), so a
// new faction is created at runtime, never by adding code.
public final class Faction {
    private static final String ID_TAG = "id";
    private static final String NAME_TAG = "name";
    private static final String LEADER_TAG = "leader";

    // Stable identity. Town halls and units refer to a faction by this id, so renaming is safe.
    private final UUID id;
    private String name;
    // The player who owns the faction, if any. Factions created by command have no leader.
    @Nullable
    private UUID leader;

    Faction(UUID id, String name, @Nullable UUID leader) {
        this.id = id;
        this.name = name;
        this.leader = leader;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    @Nullable
    public UUID leader() {
        return leader;
    }

    // Package-private setters: only FactionData changes factions, so it can mark itself for saving.
    void setName(String name) {
        this.name = name;
    }

    void setLeader(@Nullable UUID leader) {
        this.leader = leader;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID_TAG, id);
        tag.putString(NAME_TAG, name);
        if (leader != null) {
            tag.putUUID(LEADER_TAG, leader);
        }
        return tag;
    }

    static Faction load(CompoundTag tag) {
        return new Faction(tag.getUUID(ID_TAG), tag.getString(NAME_TAG),
                tag.hasUUID(LEADER_TAG) ? tag.getUUID(LEADER_TAG) : null);
    }
}
