package com.krisamerman.kingdomrts.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

// The first and last names units are given, read from datapacks (like SettlementNames): every file
// at data/<namespace>/kingdomrts/unit_names/<any>.json with {"first": [...], "last": [...]} adds to
// the pools, so other packs can add names too. A unit's name is a random first plus a random last
// name; two units can share one.
@EventBusSubscriber(modid = KingdomRts.MODID)
public final class UnitNames extends SimpleJsonResourceReloadListener {
    private static final String DIRECTORY = KingdomRts.MODID + "/unit_names";
    private static final String FALLBACK = "Settler";
    private static final RandomSource RANDOM = RandomSource.create();

    private static List<String> first = List.of();
    private static List<String> last = List.of();

    private UnitNames() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new UnitNames());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<String> firstNames = new ArrayList<>();
        List<String> lastNames = new ArrayList<>();
        files.forEach((file, json) -> {
            JsonObject object = json.getAsJsonObject();
            GsonHelper.getAsJsonArray(object, "first", new JsonArray()).forEach(name -> firstNames.add(name.getAsString()));
            GsonHelper.getAsJsonArray(object, "last", new JsonArray()).forEach(name -> lastNames.add(name.getAsString()));
        });
        first = List.copyOf(firstNames);
        last = List.copyOf(lastNames);
        KingdomRts.LOGGER.info("Loaded {} first and {} last names for units", first.size(), last.size());
    }

    // A name for a new unit.
    public static String pick() {
        return pick(first, last, RANDOM);
    }

    // Pure: a random first name and a random last name, either part left out if its list is empty,
    // or "Settler" if both are.
    public static String pick(List<String> firstNames, List<String> lastNames, RandomSource random) {
        String firstName = firstNames.isEmpty() ? "" : firstNames.get(random.nextInt(firstNames.size()));
        String lastName = lastNames.isEmpty() ? "" : lastNames.get(random.nextInt(lastNames.size()));
        String name = (firstName + " " + lastName).trim();
        return name.isEmpty() ? FALLBACK : name;
    }

    public static List<String> firstNames() {
        return first;
    }

    public static List<String> lastNames() {
        return last;
    }
}
