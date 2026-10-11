package com.krisamerman.kingdomrts.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.util.NameRules;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

// The pool of names new settlements are given, read from datapacks: every file at
// data/<namespace>/settlement_names/<any>.json with {"names": ["Oakhaven", ...]} adds to the pool,
// so other packs can add names too. Reloaded with /reload like other datapack content.
//
// A "reload listener" is the hook vanilla calls whenever datapacks are (re)loaded; the JSON variant
// hands us every matching file already parsed.
@EventBusSubscriber(modid = KingdomRts.MODID)
public final class SettlementNames extends SimpleJsonResourceReloadListener {
    private static final String DIRECTORY = "settlement_names";
    private static final String FALLBACK = "Settlement";
    private static final RandomSource RANDOM = RandomSource.create();

    private static List<String> pool = List.of();

    private SettlementNames() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SettlementNames());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<String> names = new ArrayList<>();
        files.forEach((file, json) -> {
            for (JsonElement element : GsonHelper.getAsJsonArray(json.getAsJsonObject(), "names")) {
                String name = element.getAsString();
                if (NameRules.formatProblem("Settlement", name) == null) {
                    names.add(name);
                } else {
                    KingdomRts.LOGGER.warn("Skipping settlement name '{}' in {}: not a valid name", name, file);
                }
            }
        });
        pool = List.copyOf(names);
        KingdomRts.LOGGER.info("Loaded {} settlement names", pool.size());
    }

    // A name for a new settlement: a random unused name from the pool, or, once every pool name is
    // taken, a pool name with a number ("Oakhaven_2"). Always ends: names only clash by being taken.
    public static String pick(SettlementData settlements) {
        List<String> free = pool.stream().filter(name -> settlements.byName(name) == null).toList();
        if (!free.isEmpty()) {
            return free.get(RANDOM.nextInt(free.size()));
        }
        String base = pool.isEmpty() ? FALLBACK : pool.get(RANDOM.nextInt(pool.size()));
        String name = base;
        for (int i = 2; settlements.nameProblem(name) != null; i++) {
            name = base + "_" + i;
        }
        return name;
    }
}
