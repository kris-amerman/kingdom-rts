package com.krisamerman.kingdomrts.faction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.brigadier.StringReader;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

// All factions in a world, which players belong to which faction, and which factions are at war.
//
// SavedData is vanilla's mechanism for extra per-world data: it's written to
// <world>/data/kingdomrts_factions.dat whenever it's marked dirty (setDirty) and the world saves.
// It's kept in the overworld's storage so every dimension shares the same factions.
public class FactionData extends SavedData {
    private static final String FILE_NAME = "kingdomrts_factions";
    private static final String FACTIONS_TAG = "factions";
    private static final String MEMBERS_TAG = "members";
    private static final String WARS_TAG = "wars";
    private static final int MAX_NAME_LENGTH = 32;

    private static final SavedData.Factory<FactionData> FACTORY = new SavedData.Factory<>(FactionData::new, FactionData::load);

    // LinkedHashMap keeps creation order, so listings are stable.
    private final Map<UUID, Faction> factions = new LinkedHashMap<>();
    // player UUID -> faction id. A player is in at most one faction.
    private final Map<UUID, UUID> memberships = new HashMap<>();
    private final Set<WarPair> wars = new HashSet<>();

    // An unordered pair of factions at war. The two ids are always stored in sorted order, so
    // (A, B) and (B, A) are the same pair: that's what makes hostility mutual.
    private record WarPair(UUID first, UUID second) {
        static WarPair of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new WarPair(a, b) : new WarPair(b, a);
        }
    }

    public static FactionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static FactionData get(ServerLevel level) {
        return get(level.getServer());
    }

    // ---- Factions ----

    // Returns why a name can't be used for a new faction, or null if it's fine.
    @Nullable
    public String nameProblem(String name) {
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return "Faction names must be 1-" + MAX_NAME_LENGTH + " characters";
        }
        for (char c : name.toCharArray()) {
            if (!StringReader.isAllowedInUnquotedString(c)) {
                return "Faction names may only use letters, digits, _ - . +";
            }
        }
        if (name.equalsIgnoreCase("none")) {
            return "'none' is reserved";
        }
        if (byName(name) != null) {
            return "A faction named " + name + " already exists";
        }
        return null;
    }

    // Creates a faction. The caller checks nameProblem first.
    public Faction create(String name, @Nullable UUID leader) {
        Faction faction = new Faction(UUID.randomUUID(), name, leader);
        factions.put(faction.id(), faction);
        setDirty();
        return faction;
    }

    // Founds a new faction for a factionless player, named after them (Steve, Steve_2, ...),
    // with the player as its leader and first member.
    public Faction foundFactionFor(ServerPlayer player) {
        String base = player.getGameProfile().getName();
        String name = base;
        for (int i = 2; nameProblem(name) != null; i++) {
            name = base + "_" + i;
        }
        Faction faction = create(name, player.getUUID());
        join(player.getUUID(), faction);
        return faction;
    }

    public void rename(Faction faction, String newName) {
        faction.setName(newName);
        setDirty();
    }

    @Nullable
    public Faction byId(@Nullable UUID id) {
        return id == null ? null : factions.get(id);
    }

    // Case-insensitive.
    @Nullable
    public Faction byName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (Faction faction : factions.values()) {
            if (faction.name().toLowerCase(Locale.ROOT).equals(lower)) {
                return faction;
            }
        }
        return null;
    }

    // Display name for a faction id: "none" for no faction.
    public String nameOf(@Nullable UUID id) {
        if (id == null) {
            return "none";
        }
        Faction faction = factions.get(id);
        return faction != null ? faction.name() : "unknown(" + id + ")";
    }

    public Collection<Faction> all() {
        return Collections.unmodifiableCollection(factions.values());
    }

    public List<String> names() {
        return factions.values().stream().map(Faction::name).toList();
    }

    // ---- Membership ----

    @Nullable
    public Faction factionOf(UUID player) {
        return byId(memberships.get(player));
    }

    // Moves the player into the faction, leaving their current one first.
    public void join(UUID player, Faction faction) {
        leave(player);
        memberships.put(player, faction.id());
        setDirty();
    }

    // Returns the faction the player left, or null if they weren't in one. A leader who leaves
    // simply leaves the faction leaderless.
    @Nullable
    public Faction leave(UUID player) {
        Faction old = byId(memberships.remove(player));
        if (old != null) {
            if (player.equals(old.leader())) {
                old.setLeader(null);
            }
            setDirty();
        }
        return old;
    }

    public List<UUID> members(Faction faction) {
        List<UUID> members = new ArrayList<>();
        memberships.forEach((player, factionId) -> {
            if (factionId.equals(faction.id())) {
                members.add(player);
            }
        });
        return members;
    }

    // ---- Hostility ----

    // True only for two different, existing factions that are at war. Factionless (null) is
    // never hostile to anyone, and a faction is never hostile to itself.
    public boolean isHostile(@Nullable UUID a, @Nullable UUID b) {
        return a != null && b != null && !a.equals(b) && wars.contains(WarPair.of(a, b));
    }

    // Sets two factions at war (true) or at peace (false). Returns false if nothing changed.
    public boolean setHostile(Faction a, Faction b, boolean hostile) {
        WarPair pair = WarPair.of(a.id(), b.id());
        boolean changed = hostile ? wars.add(pair) : wars.remove(pair);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public List<Faction> enemiesOf(Faction faction) {
        List<Faction> enemies = new ArrayList<>();
        for (Faction other : factions.values()) {
            if (isHostile(faction.id(), other.id())) {
                enemies.add(other);
            }
        }
        return enemies;
    }

    // ---- Saving / loading ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag factionList = new ListTag();
        factions.values().forEach(faction -> factionList.add(faction.save()));
        tag.put(FACTIONS_TAG, factionList);

        ListTag memberList = new ListTag();
        memberships.forEach((player, factionId) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putUUID("faction", factionId);
            memberList.add(entry);
        });
        tag.put(MEMBERS_TAG, memberList);

        ListTag warList = new ListTag();
        wars.forEach(pair -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("a", pair.first());
            entry.putUUID("b", pair.second());
            warList.add(entry);
        });
        tag.put(WARS_TAG, warList);
        return tag;
    }

    private static FactionData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionData data = new FactionData();
        for (Tag t : tag.getList(FACTIONS_TAG, Tag.TAG_COMPOUND)) {
            Faction faction = Faction.load((CompoundTag) t);
            data.factions.put(faction.id(), faction);
        }
        // Entries pointing at factions that don't exist are skipped.
        for (Tag t : tag.getList(MEMBERS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            UUID factionId = entry.getUUID("faction");
            if (data.factions.containsKey(factionId)) {
                data.memberships.put(entry.getUUID("player"), factionId);
            }
        }
        for (Tag t : tag.getList(WARS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            UUID a = entry.getUUID("a");
            UUID b = entry.getUUID("b");
            if (data.factions.containsKey(a) && data.factions.containsKey(b)) {
                data.wars.add(WarPair.of(a, b));
            }
        }
        return data;
    }
}
