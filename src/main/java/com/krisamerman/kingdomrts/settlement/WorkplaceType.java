package com.krisamerman.kingdomrts.settlement;

import java.util.Optional;

import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// What a kind of workplace is, as data: the occupation its workers get and how many it can employ.
// Each file at data/<namespace>/kingdomrts/workplace/<name>.json is one entry, named after the
// workplace's block, e.g. kingdomrts:lumberjack_hut:
//   { "occupation": "kingdomrts:lumberjack", "workers": 1 }
public record WorkplaceType(ResourceLocation occupation, int workers) {
    public static final Codec<WorkplaceType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("occupation").forGetter(WorkplaceType::occupation),
            Codec.intRange(1, 64).optionalFieldOf("workers", 1).forGetter(WorkplaceType::workers)
    ).apply(instance, WorkplaceType::new));

    // Identifies the datapack registry, id "kingdomrts:workplace".
    public static final ResourceKey<Registry<WorkplaceType>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "workplace"));

    public static Optional<WorkplaceType> get(Level level, ResourceLocation id) {
        return level.registryAccess().registry(REGISTRY_KEY).flatMap(registry -> registry.getOptional(id));
    }
}
