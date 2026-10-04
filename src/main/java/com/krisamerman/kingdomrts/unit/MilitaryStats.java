package com.krisamerman.kingdomrts.unit;

import java.util.Optional;

import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// Combat stats for a military unit, loaded from datapack JSON rather than hardcoded.
// Each file at data/<namespace>/kingdomrts/military_stats/<name>.json becomes one entry,
// e.g. kingdomrts:soldier. A new unit type with different numbers = a new JSON file.
//
// A Java "record" is a compact immutable data class: the fields, constructor, getters
// (maxHealth(), etc.), equals and hashCode are all generated from the header line.
public record MilitaryStats(double maxHealth, double movementSpeed, double attackDamage, double attackRange) {
    // A Codec describes how to read/write this record from JSON (or NBT). The field names
    // here are the JSON keys.
    public static final Codec<MilitaryStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("max_health").forGetter(MilitaryStats::maxHealth),
            Codec.DOUBLE.fieldOf("movement_speed").forGetter(MilitaryStats::movementSpeed),
            Codec.DOUBLE.fieldOf("attack_damage").forGetter(MilitaryStats::attackDamage),
            Codec.DOUBLE.fieldOf("attack_range").forGetter(MilitaryStats::attackRange)
    ).apply(instance, MilitaryStats::new));

    // Identifies the datapack registry, id "kingdomrts:military_stats".
    public static final ResourceKey<Registry<MilitaryStats>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "military_stats"));

    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "soldier");

    // Looks up a stats entry from the datapacks loaded into this world.
    public static Optional<MilitaryStats> get(Level level, ResourceLocation id) {
        return level.registryAccess().registry(REGISTRY_KEY).flatMap(registry -> registry.getOptional(id));
    }
}
