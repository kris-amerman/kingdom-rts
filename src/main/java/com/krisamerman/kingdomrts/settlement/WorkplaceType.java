package com.krisamerman.kingdomrts.settlement;

import java.util.Optional;

import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// What a kind of workplace is, as data: the occupation its workers get, how many it can employ, and
// its work area (the radius around it its workers stay within: the starting radius, and the most a
// player can set it to). Each file at data/<namespace>/kingdomrts/workplace/<name>.json is one entry,
// named after the workplace's block, e.g. kingdomrts:lumberjack_hut:
//   { "occupation": "kingdomrts:lumberjack", "workers": 1, "work_radius": 16, "max_work_radius": 48 }
public record WorkplaceType(ResourceLocation occupation, int workers, int workRadius, int maxWorkRadius) {
    public static final int DEFAULT_WORK_RADIUS = 16;
    public static final int DEFAULT_MAX_WORK_RADIUS = 48;

    public static final Codec<WorkplaceType> CODEC = RecordCodecBuilder.<WorkplaceType>create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("occupation").forGetter(WorkplaceType::occupation),
            Codec.intRange(1, 64).optionalFieldOf("workers", 1).forGetter(WorkplaceType::workers),
            Codec.intRange(WorkArea.MIN_RADIUS, 256).optionalFieldOf("work_radius", DEFAULT_WORK_RADIUS).forGetter(WorkplaceType::workRadius),
            Codec.intRange(WorkArea.MIN_RADIUS, 256).optionalFieldOf("max_work_radius", DEFAULT_MAX_WORK_RADIUS).forGetter(WorkplaceType::maxWorkRadius)
    ).apply(instance, WorkplaceType::new)).validate(type -> type.workRadius() <= type.maxWorkRadius()
            ? DataResult.success(type) : DataResult.error(() -> "work_radius can't be more than max_work_radius"));

    // Identifies the datapack registry, id "kingdomrts:workplace".
    public static final ResourceKey<Registry<WorkplaceType>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "workplace"));

    public static Optional<WorkplaceType> get(Level level, ResourceLocation id) {
        return level.registryAccess().registry(REGISTRY_KEY).flatMap(registry -> registry.getOptional(id));
    }
}
