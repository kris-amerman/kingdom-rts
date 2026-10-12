package com.krisamerman.kingdomrts;

import org.slf4j.Logger;

import com.krisamerman.kingdomrts.settlement.SettlementStorageBlock;
import com.krisamerman.kingdomrts.settlement.SettlementStorageBlockEntity;
import com.krisamerman.kingdomrts.settlement.TownHallBlock;
import com.krisamerman.kingdomrts.settlement.TownHallBlockEntity;
import com.krisamerman.kingdomrts.settlement.TownHallMenu;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.OccupationType;
import com.krisamerman.kingdomrts.unit.Unit;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(KingdomRts.MODID)
public class KingdomRts {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "kingdomrts";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "kingdomrts" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "kingdomrts" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "kingdomrts" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    // Create a Deferred Register to hold BlockEntityTypes which will all be registered under the "kingdomrts" namespace
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    // Create a Deferred Register to hold EntityTypes which will all be registered under the "kingdomrts" namespace
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    // Create a Deferred Register to hold MenuTypes (screens backed by a server-side menu)
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);

    // The block that founds a settlement, id "kingdomrts:town_hall"
    public static final DeferredBlock<TownHallBlock> TOWN_HALL = BLOCKS.registerBlock("town_hall", TownHallBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f));
    public static final DeferredItem<BlockItem> TOWN_HALL_ITEM = ITEMS.registerSimpleBlockItem("town_hall", TOWN_HALL);
    // Per-town-hall data (the capture control meter), id "kingdomrts:town_hall"
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TownHallBlockEntity>> TOWN_HALL_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("town_hall", () -> BlockEntityType.Builder.of(TownHallBlockEntity::new, TOWN_HALL.get()).build(null));

    // A settlement's protected chest, id "kingdomrts:settlement_storage"
    public static final DeferredBlock<SettlementStorageBlock> SETTLEMENT_STORAGE = BLOCKS.registerBlock("settlement_storage", SettlementStorageBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> SETTLEMENT_STORAGE_ITEM = ITEMS.registerSimpleBlockItem("settlement_storage", SETTLEMENT_STORAGE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SettlementStorageBlockEntity>> SETTLEMENT_STORAGE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("settlement_storage", () -> BlockEntityType.Builder.of(SettlementStorageBlockEntity::new, SETTLEMENT_STORAGE.get()).build(null));

    // The town hall menu, id "kingdomrts:town_hall". IMenuTypeExtension.create lets the server send
    // extra data (the hall's position and owner name) when the menu opens.
    public static final DeferredHolder<MenuType<?>, MenuType<TownHallMenu>> TOWN_HALL_MENU =
            MENU_TYPES.register("town_hall", () -> IMenuTypeExtension.create(TownHallMenu::new));

    // A unit (whatever its occupation), id "kingdomrts:unit". Player-sized hitbox.
    public static final DeferredHolder<EntityType<?>, EntityType<Unit>> UNIT =
            ENTITY_TYPES.register("unit", () -> EntityType.Builder.of(Unit::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .build("unit"));

    // The mod's creative tab, id "kingdomrts:main", placed after the vanilla combat tab.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.kingdomrts"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> TOWN_HALL_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(TOWN_HALL_ITEM.get());
                output.accept(SETTLEMENT_STORAGE_ITEM.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public KingdomRts(IEventBus modEventBus) {
        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so block entity types get registered
        BLOCK_ENTITY_TYPES.register(modEventBus);
        // Register the Deferred Register to the mod event bus so entity types get registered
        ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(this::registerDataPackRegistries);
    }

    // Every living entity type needs its attributes (health, speed, ...) registered.
    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(UNIT.get(), Unit.createAttributes().build());
    }

    // Datapack registries are loaded from JSON when a world loads. No network codec:
    // occupations only matter on the server, so they aren't sent to clients.
    private void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(Occupation.REGISTRY_KEY, OccupationType.OCCUPATION_CODEC);
    }
}
