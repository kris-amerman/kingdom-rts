package com.krisamerman.kingdomrts.building;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

// Where buildings' blueprints come from: vanilla structure templates (through the level's structure
// manager), read into Blueprints and kept until datapacks reload. Everything that needs a
// building's layout goes through here, so where templates come from can change later (building
// packs, styles and levels) without touching the rest.
@EventBusSubscriber(modid = KingdomRts.MODID)
public final class BuildingTemplates {
    // Server thread only. Empty optionals are kept too, so a missing template is only reported once.
    private static final Map<ResourceLocation, Optional<Blueprint>> CACHE = new HashMap<>();

    private BuildingTemplates() {
    }

    // The blueprint of a structure template, if the template exists and is a valid building.
    public static Optional<Blueprint> blueprint(ServerLevel level, ResourceLocation structure) {
        return CACHE.computeIfAbsent(structure, id -> load(level, id));
    }

    // The blueprint of a building type.
    public static Optional<Blueprint> blueprint(ServerLevel level, BuildingType type) {
        return blueprint(level, type.structure());
    }

    private static Optional<Blueprint> load(ServerLevel level, ResourceLocation id) {
        return level.getStructureManager().get(id).flatMap(template -> {
            try {
                return Optional.of(Blueprint.read(template.save(new CompoundTag()), level.holderLookup(Registries.BLOCK)));
            } catch (IllegalArgumentException e) {
                KingdomRts.LOGGER.warn("Structure {} can't be used as a building: {}", id, e.getMessage());
                return Optional.empty();
            }
        });
    }

    // Datapacks (re)loaded: templates may have changed.
    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) resourceManager -> CACHE.clear());
    }
}
