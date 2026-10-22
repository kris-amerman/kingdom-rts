package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingPreview;
import com.krisamerman.kingdomrts.building.BuildingTemplates;
import com.krisamerman.kingdomrts.building.BuildingType;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.nation.BlueprintCodec;
import com.krisamerman.kingdomrts.nation.NationPayloads;
import com.krisamerman.kingdomrts.nation.NationRequests;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.Settlement;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

// Commissioning from a preview, and checking where a preview could go, as the server answers a
// player's requests. The same rules as /kingdomrts building commission apply, so these check the
// request and its answers rather than every rule again (BuildingGameTests covers those). Each test
// founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class PreviewCommissionGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    // The shed's template corner, inside the hall's claim and clear of the hall (as in BuildingGameTests).
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);

    // Picked, turned and confirmed: the building commissioned is the one the ghost showed.
    @GameTest(template = TEMPLATE, batch = "preview_commission")
    public static void confirmingAPreviewCommissionsWhatItShowed(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        RecordingPlayer player = member(helper, blue);
        try {
            BuildingPreview preview = new BuildingPreview(SHED, "Storage shed", shedBlueprint(helper));
            preview.rotate();
            // Looking at the spot that puts the shed's corner on SITE.
            BlockPos anchor = helper.absolutePos(SITE).offset(2, 0, 2);
            NationRequests.commission(player, new NationPayloads.CommissionBuilding(SHED, preview.origin(anchor), preview.rotation()));

            Building building = current(helper, settlement).buildingAt(preview.origin(anchor));
            helper.assertTrue(building != null && building.rotation() == Rotation.CLOCKWISE_90 && building.footprint().equals(preview.footprint(anchor)),
                    "Expected the shed where the ghost was, turned as it was: " + building);
            helper.assertTrue(helper.getLevel().getBlockState(building.site()).is(KingdomRts.CONSTRUCTION_SITE.get()), "Expected its site block");
            helper.assertTrue(player.actionBar.contains("Storage shed commissioned in " + settlement.name()), "Expected to be told: " + player.actionBar);
            helper.assertTrue(answers(player).equals(List.of(true)), "Expected the preview to be told it worked: " + answers(player));
        } finally {
            leaveAndRemove(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "preview_commission_refused")
    public static void refusedCommissionsBuildNothingAndSayWhy(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        RecordingPlayer member = member(helper, blue);
        RecordingPlayer outsider = member(helper, red);
        RecordingPlayer factionless = recordingPlayer(helper);
        BlockPos site = helper.absolutePos(SITE);
        try {
            NationRequests.commission(member, new NationPayloads.CommissionBuilding(SHED, site, Rotation.NONE));
            int before = current(helper, settlement).buildings().size();
            member.actionBar.clear();
            member.sent.clear();

            refused(helper, member, site.offset(160, 0, 0), "isn't in a settlement's claim");
            refused(helper, member, site.offset(3, 0, 0), "overlaps the storage shed");
            refused(helper, outsider, site.south(5), "not " + red.name() + "'s");
            refused(helper, factionless, site.south(5), "faction");
            helper.assertTrue(current(helper, settlement).buildings().size() == before, "Expected nothing more commissioned");
        } finally {
            leaveAndRemove(helper, member);
            leaveAndRemove(helper, outsider);
            removePlayer(helper, factionless);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Checks answer whether the building fits without commissioning it, and a client asking too
    // fast is answered at most every CHECK_INTERVAL_TICKS.
    @GameTest(template = TEMPLATE, batch = "preview_check")
    public static void checkingAPlacementAnswersWithoutBuildingAndIsRateLimited(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        RecordingPlayer player = member(helper, blue);
        BlockPos site = helper.absolutePos(SITE);
        NationRequests.checkPlacement(player, new NationPayloads.CheckPlacement(SHED, site, Rotation.NONE));
        NationPayloads.PlacementAnswer fits = lastAnswer(player);
        helper.assertTrue(fits != null && fits.fits() && fits.origin().equals(site), "Expected the shed to fit at the site: " + fits);
        helper.assertTrue(current(helper, settlement).buildings().isEmpty(), "Expected a check to commission nothing");

        player.sent.clear();
        NationRequests.checkPlacement(player, new NationPayloads.CheckPlacement(SHED, helper.absolutePos(HALL), Rotation.NONE));
        helper.assertTrue(lastAnswer(player) == null, "Expected a check straight after another to be dropped");
        helper.runAfterDelay(NationRequests.CHECK_INTERVAL_TICKS, () -> {
            try {
                NationRequests.checkPlacement(player, new NationPayloads.CheckPlacement(SHED, helper.absolutePos(HALL).offset(-2, 0, -2), Rotation.NONE));
                NationPayloads.PlacementAnswer covered = lastAnswer(player);
                helper.assertTrue(covered != null && covered.problem().contains("cover the town hall"), "Expected the reason it can't go there: " + covered);
            } finally {
                leaveAndRemove(helper, player);
            }
            removeHall(helper, HALL);
            helper.succeed();
        });
    }

    // While previewing, a player is sent their faction's unfinished buildings nearby, with their
    // blueprints, to show them finished: not finished ones, nothing far away, nothing without a
    // faction, and not more often than SITES_INTERVAL_TICKS.
    @GameTest(template = TEMPLATE, batch = "preview_sites")
    public static void aPreviewIsSentTheUnfinishedBuildingsAround(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        RecordingPlayer player = member(helper, blue);
        RecordingPlayer factionless = recordingPlayer(helper);
        try {
            BlockPos unfinished = helper.absolutePos(SITE);
            BlockPos finished = helper.absolutePos(SITE).south(6);
            BuildingOps.commission(helper.getLevel(), blue, SHED, unfinished, Rotation.CLOCKWISE_90);
            Building done = BuildingOps.commission(helper.getLevel(), blue, SHED, finished, Rotation.NONE).building();
            BuildingOps.finish(helper.getLevel(), current(helper, settlement), done);
            standAt(helper, player, HALL);
            standAt(helper, factionless, HALL);

            NationRequests.sites(player);
            NationPayloads.ShowSites shown = TestSupport.lastPayload(player, NationPayloads.ShowSites.class);
            helper.assertTrue(shown != null && shown.sites().equals(List.of(new NationPayloads.SiteView(SHED, unfinished, Rotation.CLOCKWISE_90))),
                    "Expected just the unfinished shed, turned as it was commissioned: " + (shown != null ? shown.sites() : null));
            helper.assertTrue(shown.blueprints().keySet().equals(Set.of(SHED)) && shown.blueprints().get(SHED).equals(shedBlueprint(helper)),
                    "Expected the shed's blueprint with it");
            // As a client reads it: the sites as sent, and the blueprint without its air.
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
            NationPayloads.ShowSites.STREAM_CODEC.encode(buf, shown);
            NationPayloads.ShowSites received = NationPayloads.ShowSites.STREAM_CODEC.decode(buf);
            helper.assertTrue(received.sites().equals(shown.sites()) && received.blueprints().get(SHED).equals(BlueprintCodec.withoutAir(shedBlueprint(helper))),
                    "Expected the sites to arrive as sent");

            player.sent.clear();
            NationRequests.sites(player);
            helper.assertTrue(TestSupport.lastPayload(player, NationPayloads.ShowSites.class) == null, "Expected an ask straight after another to be dropped");

            NationRequests.sites(factionless);
            NationPayloads.ShowSites none = TestSupport.lastPayload(factionless, NationPayloads.ShowSites.class);
            helper.assertTrue(none != null && none.sites().isEmpty(), "Expected nothing for a player without a faction: " + none);

            BlockPos far = HALL.offset(NationRequests.SITES_RANGE + 40, 0, 0);
            helper.assertTrue(NationRequests.sitesFor(atRelative(helper, player, far)).sites().isEmpty(), "Expected nothing from far away");
        } finally {
            leaveAndRemove(helper, player);
            removePlayer(helper, factionless);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    private static void standAt(GameTestHelper helper, RecordingPlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static RecordingPlayer atRelative(GameTestHelper helper, RecordingPlayer player, BlockPos relativePos) {
        standAt(helper, player, relativePos);
        return player;
    }

    private static void refused(GameTestHelper helper, RecordingPlayer player, BlockPos origin, String reason) {
        player.actionBar.clear();
        player.sent.clear();
        NationRequests.commission(player, new NationPayloads.CommissionBuilding(SHED, origin, Rotation.NONE));
        helper.assertTrue(player.actionBar.size() == 1 && player.actionBar.get(0).contains(reason),
                "Expected to be told \"" + reason + "\": " + player.actionBar);
        helper.assertTrue(answers(player).equals(List.of(false)), "Expected the preview to be told it was refused: " + answers(player));
    }

    // The commission answers sent to the player, oldest first.
    private static List<Boolean> answers(RecordingPlayer player) {
        return player.sent.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof NationPayloads.CommissionAnswer)
                .map(packet -> ((NationPayloads.CommissionAnswer) ((ClientboundCustomPayloadPacket) packet).payload()).commissioned())
                .toList();
    }

    @Nullable
    private static NationPayloads.PlacementAnswer lastAnswer(RecordingPlayer player) {
        return TestSupport.lastPayload(player, NationPayloads.PlacementAnswer.class);
    }

    private static Blueprint shedBlueprint(GameTestHelper helper) {
        return BuildingType.get(helper.getLevel(), SHED).flatMap(type -> BuildingTemplates.blueprint(helper.getLevel(), type)).orElseThrow();
    }

    private static RecordingPlayer member(GameTestHelper helper, Faction faction) {
        RecordingPlayer player = recordingPlayer(helper);
        FactionData.get(helper.getLevel()).join(player.getUUID(), faction);
        return player;
    }

    private static void leaveAndRemove(GameTestHelper helper, RecordingPlayer player) {
        FactionData.get(helper.getLevel()).leave(player.getUUID());
        removePlayer(helper, player);
    }
}
