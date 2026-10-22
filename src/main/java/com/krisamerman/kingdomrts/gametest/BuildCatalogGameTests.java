package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.chat;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lastPayload;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingPreview;
import com.krisamerman.kingdomrts.building.BuildingRules;
import com.krisamerman.kingdomrts.building.BuildingTemplates;
import com.krisamerman.kingdomrts.building.BuildingType;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.nation.BlueprintCodec;
import com.krisamerman.kingdomrts.nation.CatalogEntry;
import com.krisamerman.kingdomrts.nation.NationPayloads;
import com.krisamerman.kingdomrts.nation.NationRequests;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

// The Build catalog and building previews, as the server answers them: what a player's client is
// sent when they press Build or pick a building. (Drawing the ghost is client-only, so it's on the
// manual checklist.) None of these found a settlement: the catalog only needs a faction.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class BuildCatalogGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");

    @GameTest(template = TEMPLATE)
    public static void aMemberGetsEveryBuildableBuildingByName(GameTestHelper helper) {
        RecordingPlayer player = member(helper);
        try {
            NationRequests.catalog(player);
            NationPayloads.ShowCatalog catalog = lastPayload(player, NationPayloads.ShowCatalog.class);
            helper.assertTrue(catalog != null, "Expected the catalog to be sent");
            Blueprint shed = shedBlueprint(helper);
            helper.assertTrue(catalog.entries().contains(new CatalogEntry(SHED, "Storage shed", shed.size())), "Expected the storage shed: " + catalog.entries());
            int buildable = (int) helper.getLevel().registryAccess().registryOrThrow(BuildingType.REGISTRY_KEY).stream()
                    .filter(type -> BuildingTemplates.blueprint(helper.getLevel(), type).isPresent()).count();
            helper.assertTrue(catalog.entries().size() == buildable, "Expected all " + buildable + " buildable buildings: " + catalog.entries());
            List<String> names = catalog.entries().stream().map(CatalogEntry::name).toList();
            helper.assertTrue(names.equals(names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()), "Expected them by name: " + names);
        } finally {
            leaveAndRemove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPlayerWithoutAFactionGetsNoCatalogOrBlueprint(GameTestHelper helper) {
        RecordingPlayer player = recordingPlayer(helper);
        try {
            NationRequests.catalog(player);
            NationRequests.blueprint(player, SHED);
            helper.assertTrue(lastPayload(player, NationPayloads.ShowCatalog.class) == null, "Expected no catalog");
            helper.assertTrue(lastPayload(player, NationPayloads.ShowBlueprint.class) == null, "Expected no blueprint");
            helper.assertTrue(chat(player).stream().filter(line -> line.contains("faction")).count() == 2, "Expected to be told why, twice: " + chat(player));
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    // The blueprint reaches the client whole: every block but air, where the template has it. Read
    // back through the network codec, so this checks what a client would decode.
    @GameTest(template = TEMPLATE)
    public static void pickingABuildingSendsItsBlueprintForThePreview(GameTestHelper helper) {
        RecordingPlayer player = member(helper);
        try {
            NationRequests.blueprint(player, SHED);
            NationPayloads.ShowBlueprint sent = lastPayload(player, NationPayloads.ShowBlueprint.class);
            helper.assertTrue(sent != null && sent.building().equals(SHED) && sent.name().equals("Storage shed"), "Expected the shed's blueprint: " + sent);
            NationPayloads.ShowBlueprint received = decoded(helper, sent);
            Blueprint shed = shedBlueprint(helper);
            Blueprint expected = BlueprintCodec.withoutAir(shed);
            helper.assertTrue(!expected.parts().isEmpty() && expected.parts().size() < shed.parts().size(), "Expected the shed to have air to leave out");
            helper.assertTrue(received.blueprint().equals(expected), "Expected every block but air, in place: " + received.blueprint().parts().size()
                    + " of " + expected.parts().size());

            // Previewed at a spot and turned, it shows the blocks the shed would be built from there.
            BuildingPreview preview = new BuildingPreview(received.building(), received.name(), received.blueprint());
            preview.rotate();
            BlockPos anchor = new BlockPos(30, 70, -12);
            BlockPos origin = preview.origin(anchor);
            List<Blueprint.Part> ghost = preview.turnedParts().stream().map(part -> new Blueprint.Part(part.pos().offset(origin), part.state())).toList();
            List<Blueprint.Part> built = BuildingRules.place(shed, origin, Rotation.CLOCKWISE_90).stream().filter(part -> !part.state().isAir()).toList();
            helper.assertTrue(ghost.equals(built), "Expected the ghost to match what would be built");
        } finally {
            leaveAndRemove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anUnknownBuildingIsRefused(GameTestHelper helper) {
        RecordingPlayer player = member(helper);
        try {
            NationRequests.blueprint(player, ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "no_such_building"));
            helper.assertTrue(lastPayload(player, NationPayloads.ShowBlueprint.class) == null, "Expected no blueprint");
            helper.assertTrue(chat(player).stream().anyMatch(line -> line.contains("no_such_building")), "Expected to be told: " + chat(player));
        } finally {
            leaveAndRemove(helper, player);
        }
        helper.succeed();
    }

    private static Blueprint shedBlueprint(GameTestHelper helper) {
        return BuildingType.get(helper.getLevel(), SHED).flatMap(type -> BuildingTemplates.blueprint(helper.getLevel(), type)).orElseThrow();
    }

    private static RecordingPlayer member(GameTestHelper helper) {
        RecordingPlayer player = recordingPlayer(helper);
        FactionData.get(helper.getLevel()).join(player.getUUID(), faction(helper, "Blue"));
        return player;
    }

    // The payload as a client would read it: written and read back with its network codec.
    private static NationPayloads.ShowBlueprint decoded(GameTestHelper helper, NationPayloads.ShowBlueprint sent) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        NationPayloads.ShowBlueprint.STREAM_CODEC.encode(buf, sent);
        return NationPayloads.ShowBlueprint.STREAM_CODEC.decode(buf);
    }

    private static void leaveAndRemove(GameTestHelper helper, RecordingPlayer player) {
        FactionData.get(helper.getLevel()).leave(player.getUUID());
        removePlayer(helper, player);
    }
}
