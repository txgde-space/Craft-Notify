package dev.thou.craftnotify.registry;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.block.NotifierBlock;
import dev.thou.craftnotify.block.AntennaBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CraftNotify.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CraftNotify.MOD_ID);

    public static final DeferredBlock<NotifierBlock> NOTIFIER = BLOCKS.registerBlock(
            "notifier",
            NotifierBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> {
                        if (state.hasProperty(NotifierBlock.SENDING) && state.getValue(NotifierBlock.SENDING)) {
                            return 7;
                        }
                        if (state.hasProperty(NotifierBlock.POWERED) && state.getValue(NotifierBlock.POWERED)) {
                            return 4;
                        }
                        if (state.hasProperty(NotifierBlock.ENERGY) && state.getValue(NotifierBlock.ENERGY) >= 3) {
                            return 2;
                        }
                        return 0;
                    })
    );

    public static final DeferredItem<BlockItem> NOTIFIER_ITEM =
            ITEMS.registerSimpleBlockItem("notifier", NOTIFIER, new Item.Properties());

    public static final DeferredBlock<AntennaBlock> ANTENNA = BLOCKS.registerBlock(
            "antenna",
            AntennaBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> state.hasProperty(AntennaBlock.TRANSMITTING)
                            && state.getValue(AntennaBlock.TRANSMITTING) ? 8 : 0)
    );

    public static final DeferredItem<BlockItem> ANTENNA_ITEM =
            ITEMS.registerSimpleBlockItem("antenna", ANTENNA, new Item.Properties());

    static {
        addAliases(BLOCKS, "notifier", "antenna");
        addAliases(ITEMS, "notifier", "antenna");
    }

    private ModBlocks() {
    }

    private static void addAliases(DeferredRegister<?> register, String... paths) {
        for (String path : paths) {
            register.addAlias(CraftNotify.legacyId(CraftNotify.LEGACY_MOD_ID, path), CraftNotify.id(path));
            register.addAlias(CraftNotify.legacyId(CraftNotify.EARLIEST_MOD_ID, path), CraftNotify.id(path));
        }
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
