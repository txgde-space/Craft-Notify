package dev.thou.craftnotify.client;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.client.screen.NotifierScreen;
import dev.thou.craftnotify.registry.ModBlockEntities;
import dev.thou.craftnotify.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CraftNotify.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CraftNotifyClient {
    private CraftNotifyClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.NOTIFIER.get(), NotifierScreen::new));
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
