package com.krisamerman.kingdomrts.unit;

import java.util.Optional;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// A unit's role: what it does and what it's good at. Occupations are datapack entries, so a new
// kind of soldier (or, later, worker) is a new JSON file rather than new code. Each file at
// data/<namespace>/kingdomrts/occupation/<name>.json becomes one entry, e.g. kingdomrts:soldier.
//
// Every entry names a "type" (see OccupationType), which picks the code that runs it and which
// other fields the JSON has. Many entries can share a type: a "soldier" and an "archer" would both
// be "kingdomrts:military" with different stats.
public interface Occupation {
    // Identifies the datapack registry, id "kingdomrts:occupation".
    ResourceKey<Registry<Occupation>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "occupation"));

    ResourceLocation SOLDIER = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "soldier");

    OccupationType type();

    // Sets the unit up for this occupation: its stats and its AI goals (added with
    // Unit#addOccupationGoal, so they're removed again if the occupation changes). Server only.
    void applyTo(Unit unit);

    // Whether this is a military occupation. A military unit counts as its faction's presence at a
    // capture point, and keeps its faction when its settlement is captured (other units pass to the
    // new owner).
    default boolean isMilitary() {
        return false;
    }

    // Looks up an entry from the datapacks loaded into this world.
    static Optional<Occupation> get(Level level, ResourceLocation id) {
        return level.registryAccess().registry(REGISTRY_KEY).flatMap(registry -> registry.getOptional(id));
    }
}
