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
        String path = location.getPath().replace('_', ' ');
        String fallback = path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return Component.translatableWithFallback("occupation." + location.getNamespace() + "." + location.getPath(), fallback).getString();
    }
}
