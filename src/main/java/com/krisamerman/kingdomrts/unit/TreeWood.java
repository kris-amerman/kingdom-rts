package com.krisamerman.kingdomrts.unit;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

// The kinds of wood a lumberjack knows, as data: which log blocks are tree trunks, and which sapling
// replants each. They're the wood types listed in the hut panel (to fell or skip) and what it plants
// where a felled trunk stood. A NeoForge data map on blocks (extra data attached to existing
// registry entries), loaded from data/<namespace>/data_maps/block/tree_wood.json, e.g.
//   { "values": { "minecraft:oak_log": { "sapling": "minecraft:oak_sapling" } } }
// or just "minecraft:oak_log": "minecraft:oak_sapling". Trees of wood that isn't listed are still
// felled, but can't be skipped or replanted.
public record TreeWood(Block sapling) {
    private static final Codec<TreeWood> SAPLING_ONLY_CODEC = BuiltInRegistries.BLOCK.byNameCodec().xmap(TreeWood::new, TreeWood::sapling);

    public static final Codec<TreeWood> CODEC = Codec.withAlternative(
            RecordCodecBuilder.create(instance -> instance.group(
                    BuiltInRegistries.BLOCK.byNameCodec().fieldOf("sapling").forGetter(TreeWood::sapling)
            ).apply(instance, TreeWood::new)),
            SAPLING_ONLY_CODEC);

    // Server only: not synced, since the panel gets the list of woods from the server.
    public static final DataMapType<Block, TreeWood> DATA_MAP = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "tree_wood"), Registries.BLOCK, CODEC).build();

    // The sapling that replants a tree of this log, if the log is a known wood.
    public static Optional<Block> saplingFor(Block log) {
        return BuiltInRegistries.BLOCK.getResourceKey(log).map(key -> BuiltInRegistries.BLOCK.getData(DATA_MAP, key)).map(TreeWood::sapling);
    }

    // Every known wood (its log's id), in id order.
    public static List<ResourceLocation> woods() {
        return BuiltInRegistries.BLOCK.getDataMap(DATA_MAP).keySet().stream().map(ResourceKey::location)
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }
}
