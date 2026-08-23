package dev.thou.craftnotify.client;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.client.screen.NotifierScreen;
import dev.thou.craftnotify.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CraftNotify.MOD_ID, value = Dist.CLIENT)
public final class CraftNotifyClient {
    private CraftNotifyClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.NOTIFIER.get(), NotifierScreen::new);
    }
}
