package dev.thou.craftnotify.registry;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.menu.NotifierMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CraftNotify.MOD_ID);

    public static final RegistryObject<MenuType<NotifierMenu>> NOTIFIER =
            MENUS.register("notifier", () -> IForgeMenuType.create(NotifierMenu::fromNetwork));

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
