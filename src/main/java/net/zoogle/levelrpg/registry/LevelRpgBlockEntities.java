package net.zoogle.levelrpg.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.block.entity.TheIndexBlockEntity;

import java.util.function.Supplier;

public final class LevelRpgBlockEntities {
    private LevelRpgBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, LevelRPG.MODID);

    public static final Supplier<BlockEntityType<TheIndexBlockEntity>> THE_INDEX = BLOCK_ENTITY_TYPES.register(
            "the_index",
            () -> BlockEntityType.Builder.of(TheIndexBlockEntity::new, LevelRpgBlocks.THE_INDEX.get()).build(null)
    );

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
