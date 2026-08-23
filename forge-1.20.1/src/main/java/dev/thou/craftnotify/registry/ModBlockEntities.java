package dev.thou.craftnotify.registry;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CraftNotify.MOD_ID);

    public static final RegistryObject<BlockEntityType<NotifierBlockEntity>> NOTIFIER =
            BLOCK_ENTITIES.register("notifier", () -> BlockEntityType.Builder
                    .of(NotifierBlockEntity::new, ModBlocks.NOTIFIER.get())
                    .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
