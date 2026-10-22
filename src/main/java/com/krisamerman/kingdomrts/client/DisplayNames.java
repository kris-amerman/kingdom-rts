package com.krisamerman.kingdomrts.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Client only. How ids are shown to players.
public final class DisplayNames {
    private DisplayNames() {
    }

    // "kingdomrts:soldier" -> the "occupation.kingdomrts.soldier" translation, or "Soldier" if a
    // datapack's occupation has none. Anything that isn't an id is shown as it is.
    public static String occupation(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return id;
        }
        String fallback = location.getPath().isEmpty() ? id : sentenceCase(location.getPath());
        return Component.translatableWithFallback("occupation." + location.getNamespace() + "." + location.getPath(), fallback).getString();
    }

    // "minecraft:the_nether" -> "The nether". Anything that isn't an id is shown as it is.
    public static String dimension(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null || location.getPath().isEmpty() ? id : sentenceCase(location.getPath());
    }

    // "the_nether" -> "The nether".
    private static String sentenceCase(String path) {
        String words = path.replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
