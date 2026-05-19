package net.zoogle.levelrpg.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.block.DormantIndexCoreBlock;
import net.zoogle.levelrpg.block.EnchantmentGlyphBarrierBlock;
import net.zoogle.levelrpg.block.IndexBlock;

public final class LevelRpgBlocks {
    private LevelRpgBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LevelRPG.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LevelRPG.MODID);

    public static final DeferredBlock<Block> THE_INDEX = BLOCKS.register(
            "the_index",
            () -> new IndexBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(50.0F, 1200.0F)
                            .sound(SoundType.STONE)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredItem<BlockItem> THE_INDEX_ITEM = ITEMS.register(
            "the_index",
            () -> new BlockItem(THE_INDEX.get(), new Item.Properties())
    );

    public static final DeferredBlock<Block> ENCHANTMENT_GLYPH_BARRIER = BLOCKS.register(
            "enchantment_glyph_barrier",
            () -> new EnchantmentGlyphBarrierBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.BARRIER)
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .noOcclusion()
                            .lightLevel(state -> 8)
            )
    );

    public static final DeferredItem<BlockItem> ENCHANTMENT_GLYPH_BARRIER_ITEM = ITEMS.register(
            "enchantment_glyph_barrier",
            () -> new BlockItem(ENCHANTMENT_GLYPH_BARRIER.get(), new Item.Properties())
    );

    public static final DeferredBlock<Block> DORMANT_INDEX_CORE = BLOCKS.register(
            "dormant_index_core",
            () -> new DormantIndexCoreBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(50.0F, 1200.0F)
                            .sound(SoundType.AMETHYST)
                            .lightLevel(state -> 4)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredItem<BlockItem> DORMANT_INDEX_CORE_ITEM = ITEMS.register(
            "dormant_index_core",
            () -> new BlockItem(DORMANT_INDEX_CORE.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> INDEX_KEY = ITEMS.register(
            "index_key",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(LevelRpgBlocks::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(THE_INDEX_ITEM.get());
            event.accept(ENCHANTMENT_GLYPH_BARRIER_ITEM.get());
            event.accept(DORMANT_INDEX_CORE_ITEM.get());
            event.accept(INDEX_KEY.get());
        }
    }
}
