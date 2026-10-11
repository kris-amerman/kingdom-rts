package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.mockPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.SettlementStorageBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Settlement Storage: placement only in your own claim, access only for the owning faction (and it
// follows capture), and no way in from outside: hoppers, explosions, other factions' players.
// Tests that found a settlement have their own batch so claims never overlap.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class SettlementStorageGameTests {
    private static final BlockPos STORAGE = new BlockPos(4, FLOOR_Y + 2, 4); // raised, so hoppers fit under and over it

    @GameTest(template = TEMPLATE, batch = "storage_placement")
    public static void placementOnlyInOwnClaim(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        found(helper, HALL, blue);
        BlockPos pos = helper.absolutePos(STORAGE);
        helper.assertTrue(SettlementOps.storagePlacementProblem(helper.getLevel(), pos, blue.id()) == null,
                "The owner should be able to place storage in its own claim");
        helper.assertTrue(SettlementOps.storagePlacementProblem(helper.getLevel(), pos, red.id()) != null,
                "Another faction shouldn't be able to place storage in Blue's claim");
        helper.assertTrue(SettlementOps.storagePlacementProblem(helper.getLevel(), pos, null) != null,
                "A factionless player shouldn't be able to place settlement storage");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void placementOutsideAnyClaimRefused(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        helper.assertTrue(SettlementOps.storagePlacementProblem(helper.getLevel(), helper.absolutePos(STORAGE), blue.id()) != null,
                "Storage shouldn't be placeable outside any claim");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "storage_hoppers", timeoutTicks = 200)
    public static void hoppersCantReachStorage(GameTestHelper helper) {
        found(helper, HALL, faction(helper, "Blue"));
        Container storage = placeStorage(helper);
        storage.setItem(0, new ItemStack(Items.DIAMOND));
        // A hopper underneath pulls from the block above it; one on top pushes down into it.
        helper.setBlock(STORAGE.below(), Blocks.HOPPER);
        helper.setBlock(STORAGE.above(), Blocks.HOPPER);
        Container hopperAbove = (Container) helper.getBlockEntity(STORAGE.above());
        hopperAbove.setItem(0, new ItemStack(Items.EMERALD));
        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(storage.getItem(0).is(Items.DIAMOND), "A hopper pulled items out of settlement storage");
                    helper.assertTrue(hopperAbove.getItem(0).is(Items.EMERALD), "A hopper pushed items into settlement storage");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "storage_explosion")
    public static void ownedStorageSurvivesExplosion(GameTestHelper helper) {
        found(helper, HALL, faction(helper, "Blue"));
        Container storage = placeStorage(helper);
        storage.setItem(0, new ItemStack(Items.DIAMOND));
        BlockPos dirt = STORAGE.east(2);
        helper.setBlock(dirt, Blocks.DIRT);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(STORAGE.east()));
        helper.getLevel().explode(null, center.x, center.y, center.z, 4.0f, Level.ExplosionInteraction.TNT);
        helper.assertBlockPresent(Blocks.AIR, dirt); // the blast happened
        helper.assertBlockPresent(KingdomRts.SETTLEMENT_STORAGE.get(), STORAGE);
        helper.assertTrue(storage.getItem(0).is(Items.DIAMOND), "Expected the contents to survive");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "storage_access")
    public static void accessFollowsOwner(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        SettlementStorageBlockEntity storage = placeStorage(helper);
        ServerPlayer player = mockPlayer(helper);
        FactionData factions = FactionData.get(helper.getLevel());
        try {
            factions.join(player.getUUID(), blue);
            helper.assertTrue(storage.canAccess(player), "A member of the owning faction should have access");
            factions.join(player.getUUID(), red);
            helper.assertFalse(storage.canAccess(player), "Another faction's member shouldn't have access");
            SettlementOps.transfer(helper.getLevel(), settlement, red.id()); // as on capture
            helper.assertTrue(storage.canAccess(player), "After capture, the new owner's member should have access");
            factions.join(player.getUUID(), blue);
            helper.assertFalse(storage.canAccess(player), "After capture, the old owner's member shouldn't have access");
        } finally {
            factions.leave(player.getUUID());
            removePlayer(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "storage_breaking")
    public static void onlyOwnerCanBreakStorage(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        placeStorage(helper).setItem(0, new ItemStack(Items.DIAMOND));
        BlockPos pos = helper.absolutePos(STORAGE);
        ServerPlayer player = mockPlayer(helper);
        FactionData factions = FactionData.get(helper.getLevel());
        try {
            factions.join(player.getUUID(), red);
            player.gameMode.destroyBlock(pos);
            helper.assertBlockPresent(KingdomRts.SETTLEMENT_STORAGE.get(), STORAGE);

            factions.join(player.getUUID(), blue);
            player.gameMode.destroyBlock(pos);
            helper.assertBlockNotPresent(KingdomRts.SETTLEMENT_STORAGE.get(), STORAGE);
            helper.assertItemEntityPresent(Items.DIAMOND, STORAGE, 2.0); // contents dropped
            helper.assertFalse(SettlementData.get(helper.getLevel()).byId(settlement.id()).storage().contains(pos),
                    "Expected the broken storage to be removed from the settlement's list");
        } finally {
            factions.leave(player.getUUID());
            removePlayer(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Places a storage block and registers it with the settlement claiming that spot, as placing
    // it by hand does.
    private static SettlementStorageBlockEntity placeStorage(GameTestHelper helper) {
        helper.setBlock(STORAGE, KingdomRts.SETTLEMENT_STORAGE.get());
        Settlement settlement = SettlementOps.registerStorage(helper.getLevel(), helper.absolutePos(STORAGE));
        if (settlement == null || !(helper.getBlockEntity(STORAGE) instanceof SettlementStorageBlockEntity storage)) {
            throw new GameTestAssertException("Couldn't place settlement storage");
        }
        return storage;
    }
}
