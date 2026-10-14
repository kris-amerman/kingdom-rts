package com.krisamerman.kingdomrts.building;

import java.util.Optional;

import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// What a kind of building is, as data: the structure template it's built from, and its name. Each
// file at data/<namespace>/kingdomrts/building/<name>.json is one entry, e.g. kingdomrts:storage_shed:
//   { "structure": "kingdomrts:buildings/storage_shed", "name": "Storage shed" }
// The structure is a vanilla structure file, data/<namespace>/structure/<path>.nbt, so it can be
// made in-game with a structure block. Styles, levels and costs come later.
public record BuildingType(ResourceLocation structure, String name) {
    public static final Codec<BuildingType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("structure").forGetter(BuildingType::structure),
            Codec.STRING.fieldOf("name").forGetter(BuildingType::name)
    ).apply(instance, BuildingType::new));

    // Identifies the datapack registry, id "kingdomrts:building".
    public static final ResourceKey<Registry<BuildingType>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "building"));

    public static Optional<BuildingType> get(Level level, ResourceLocation id) {
        return level.registryAccess().registry(REGISTRY_KEY).flatMap(registry -> registry.getOptional(id));
    }
}
