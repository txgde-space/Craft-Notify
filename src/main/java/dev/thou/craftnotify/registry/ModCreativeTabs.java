package dev.thou.craftnotify.registry;

import dev.thou.craftnotify.CraftNotify;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CraftNotify.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.craft_notify.main"))
                    .icon(() -> ModBlocks.NOTIFIER_ITEM.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.NOTIFIER_ITEM.get());
                        output.accept(ModBlocks.ANTENNA_ITEM.get());
                    })
                    .build()
    );

    static {
        TABS.addAlias(
                CraftNotify.legacyId(CraftNotify.LEGACY_MOD_ID, "main"),
                CraftNotify.id("main")
        );
        TABS.addAlias(
                CraftNotify.legacyId(CraftNotify.EARLIEST_MOD_ID, "main"),
                CraftNotify.id("main")
        );
    }

    private ModCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
