package com.krisamerman.kingdomrts.unit;

import java.util.Map;

import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import net.minecraft.resources.ResourceLocation;

// A kind of occupation, backed by code: "kingdomrts:military" means hold a post and fight. The
// type's codec says which fields an occupation entry of that type has.
//
// Types are a fixed list for now (military, lumberjack and builder).
// If other mods ever need to add types, this can become a NeoForge registry.
public record OccupationType(ResourceLocation id, MapCodec<? extends Occupation> codec) {
    public static final OccupationType MILITARY = new OccupationType(
            ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "military"), MilitaryOccupation.CODEC);

    public static final OccupationType LUMBERJACK = new OccupationType(
            ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "lumberjack"), LumberjackOccupation.CODEC);

    public static final OccupationType BUILDER = new OccupationType(
            ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "builder"), BuilderOccupation.CODEC);

    private static final Map<ResourceLocation, OccupationType> TYPES = Map.of(MILITARY.id(), MILITARY, LUMBERJACK.id(), LUMBERJACK,
            BUILDER.id(), BUILDER);

    // Reads a type from its id, refusing ids that aren't a known type.
    public static final Codec<OccupationType> CODEC = ResourceLocation.CODEC.comapFlatMap(
            id -> TYPES.containsKey(id) ? DataResult.success(TYPES.get(id)) : DataResult.error(() -> "Unknown occupation type: " + id),
            OccupationType::id);

    // Reads any occupation by first reading "type", then the rest with that type's codec. It lives
    // here rather than on Occupation so the classes initialize in a safe order.
    public static final Codec<Occupation> OCCUPATION_CODEC = CODEC.dispatch("type", Occupation::type, OccupationType::codec);
}
