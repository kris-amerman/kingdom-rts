package com.krisamerman.kingdomrts.building;

import java.util.Optional;

import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// What a kind of building is, as data: the structure template it's built from, its name, how many
// builders can work on it at once (max_builders, optional, 2 by default), and its core block (core,
// optional). Each file at data/<namespace>/kingdomrts/building/<name>.json is one entry, e.g.
// kingdomrts:storage_shed:
//   { "structure": "kingdomrts:buildings/storage_shed", "name": "Storage shed" }
// The structure is a vanilla structure file, data/<namespace>/structure/<path>.nbt, so it can be
// made in-game with a structure block. Styles, levels and costs come later.
//
// A building that does something (a hut) names its core, the block that manages it once it's built,
// e.g. "core": "kingdomrts:lumberjack_hut". Its template has the core exactly once (see
// BuildingRules#coreOf). While it's built, the construction site block stands on the core's spot,
// and it becomes the core when the building is finished (see BuildingOps#completeIfDone). Buildings
// that are only blocks (a shed) have none.
public record BuildingType(ResourceLocation structure, String name, int maxBuilders, Optional<ResourceLocation> core) {
    public static final int DEFAULT_MAX_BUILDERS = 2;

    public static final Codec<BuildingType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("structure").forGetter(BuildingType::structure),
            Codec.STRING.fieldOf("name").forGetter(BuildingType::name),
            Codec.intRange(1, 16).optionalFieldOf("max_builders", DEFAULT_MAX_BUILDERS).forGetter(BuildingType::maxBuilders),
            ResourceLocation.CODEC.optionalFieldOf("core").forGetter(BuildingType::core)
    ).apply(instance, BuildingType::new));

    public BuildingType(ResourceLocation structure, String name) {
        this(structure, name, DEFAULT_MAX_BUILDERS, Optional.empty());
    }

    // Identifies the datapack registry, id "kingdomrts:building".
    public static final ResourceKey<Registry<BuildingType>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "building"));

    public static Optional<BuildingType> get(Level level, ResourceLocation id) {
        return level.registryAccess().registry(REGISTRY_KEY).flatMap(registry -> registry.getOptional(id));
    }
}
