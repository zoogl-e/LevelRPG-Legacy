package net.zoogle.levelrpg.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.block.IndexBlock;

public final class LevelRpgBlocks {
    private LevelRpgBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LevelRPG.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LevelRPG.MODID);

    public static final DeferredBlock<Block> THE_INDEX = BLOCKS.register(
            "the_index",
            () -> new IndexBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.0F, 6.0F)
                            .sound(SoundType.STONE)
            )
    );

    public static final DeferredItem<BlockItem> THE_INDEX_ITEM = ITEMS.register(
            "the_index",
            () -> new BlockItem(THE_INDEX.get(), new Item.Properties())
    );

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(LevelRpgBlocks::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(THE_INDEX_ITEM.get());
        }
    }
}

