package com.krisamerman.kingdomrts.settlement;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// A construction site: put down when a building is commissioned, on a spot inside it that the
// building leaves clear (see BuildingRules#siteSpot), and taken away when the building is finished
// (see BuildingOps). Right-clicking opens the site panel (BuildingSiteMenu), and its stock is where
// the site's materials are pooled. Breaking it (only its owners can) cancels the site. It has no
// item: it only ever comes from commissioning. Protection rules: see SettlementContainerBlock.
//
// Buildings that do something (huts, the town hall, homes) will have a block of their own as their
// lasting core; buildings that are only blocks (a shed) have nothing once they're built.
public class ConstructionSiteBlock extends SettlementContainerBlock {
    public static final MapCodec<ConstructionSiteBlock> CODEC = simpleCodec(ConstructionSiteBlock::new);

    public ConstructionSiteBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConstructionSiteBlockEntity(pos, state);
    }

    // Never placed by a player (it has no item); commissioning records it.
    @Override
    protected void register(ServerLevel level, BlockPos pos) {
    }

    // Broken: the building it was the site of is no longer commissioned, or, if it's already built
    // (its stock being cleared out), its site is closed (its stock drops). When a site is closed
    // normally, it's no longer recorded as one before its block is taken away, so nothing happens
    // here then.
    @Override
    protected void unregister(ServerLevel level, Settlement settlement, BlockPos pos) {
        Building building = BuildingOps.siteAt(settlement, pos);
        if (building != null && building.isBuilt()) {
            SettlementData.get(level).closeSite(settlement, building);
        } else if (building != null) {
            BuildingOps.forget(level, settlement, building);
        }
    }

    @Override
    protected String noun() {
        return "construction site";
    }

    @Override
    protected void open(ServerPlayer player, SettlementStorageBlockEntity storage) {
        if (storage instanceof ConstructionSiteBlockEntity site && site.getSettlement() != null
                && BuildingOps.siteAt(site.getSettlement(), site.getBlockPos()) != null) {
            BuildingSiteMenu.open(player, site, BuildingSiteMenu.View.OVERVIEW, 0);
        } else {
            player.displayClientMessage(Component.literal("This construction site has no building"), true);
            player.openMenu(storage);
        }
    }
}
