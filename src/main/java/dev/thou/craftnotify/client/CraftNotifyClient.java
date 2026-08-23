package dev.thou.craftnotify.client;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.client.screen.NotifierScreen;
import dev.thou.craftnotify.registry.ModBlockEntities;
import dev.thou.craftnotify.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CraftNotify.MOD_ID, value = Dist.CLIENT)
public final class CraftNotifyClient {
    private CraftNotifyClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.NOTIFIER.get(), NotifierScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.NOTIFIER.get(), NotifierTransmitRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ANTENNA.get(), AntennaArmRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AntennaArmRenderer.LAYER, AntennaArmRenderer::createLayer);
    }
}
