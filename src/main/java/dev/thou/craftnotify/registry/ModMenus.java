package dev.thou.craftnotify.registry;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.menu.NotifierMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CraftNotify.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<NotifierMenu>> NOTIFIER =
            MENUS.register("notifier", () -> IMenuTypeExtension.create(NotifierMenu::fromNetwork));

    static {
        MENUS.addAlias(
                CraftNotify.legacyId(CraftNotify.LEGACY_MOD_ID, "notifier"),
                CraftNotify.id("notifier")
        );
        MENUS.addAlias(
                CraftNotify.legacyId(CraftNotify.EARLIEST_MOD_ID, "notifier"),
                CraftNotify.id("notifier")
        );
    }

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
