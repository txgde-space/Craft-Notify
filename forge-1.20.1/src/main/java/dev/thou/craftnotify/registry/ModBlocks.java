package dev.thou.craftnotify.registry;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.block.NotifierBlock;
import dev.thou.craftnotify.block.AntennaBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CraftNotify.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CraftNotify.MOD_ID);

    public static final RegistryObject<NotifierBlock> NOTIFIER = BLOCKS.register(
            "notifier",
            () -> new NotifierBlock(BlockBehaviour.Properties.of()
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
                    }))
    );

    public static final RegistryObject<BlockItem> NOTIFIER_ITEM = ITEMS.register(
            "notifier", () -> new BlockItem(NOTIFIER.get(), new Item.Properties()));

    public static final RegistryObject<AntennaBlock> ANTENNA = BLOCKS.register(
            "antenna",
            () -> new AntennaBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> state.hasProperty(AntennaBlock.TRANSMITTING)
                            && state.getValue(AntennaBlock.TRANSMITTING) ? 8 : 0))
    );

    public static final RegistryObject<BlockItem> ANTENNA_ITEM = ITEMS.register(
            "antenna", () -> new BlockItem(ANTENNA.get(), new Item.Properties()));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
