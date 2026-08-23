package dev.thou.craftnotify.registry;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CraftNotify.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NotifierBlockEntity>> NOTIFIER =
            BLOCK_ENTITIES.register("notifier", () -> BlockEntityType.Builder
                    .of(NotifierBlockEntity::new, ModBlocks.NOTIFIER.get())
                    .build(null));

    static {
        BLOCK_ENTITIES.addAlias(
                CraftNotify.legacyId(CraftNotify.LEGACY_MOD_ID, "notifier"),
                CraftNotify.id("notifier")
        );
        BLOCK_ENTITIES.addAlias(
                CraftNotify.legacyId(CraftNotify.EARLIEST_MOD_ID, "notifier"),
                CraftNotify.id("notifier")
        );
    }

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
