package dev.thou.craftnotify;

import com.mojang.logging.LogUtils;
import dev.thou.craftnotify.command.NotifyCommands;
import dev.thou.craftnotify.notification.NotificationDispatcher;
import dev.thou.craftnotify.notification.SecretChannelStore;
import dev.thou.craftnotify.notification.WebhookCallbackServer;
import dev.thou.craftnotify.network.ModNetworking;
import dev.thou.craftnotify.registry.ModBlockEntities;
import dev.thou.craftnotify.registry.ModBlocks;
import dev.thou.craftnotify.registry.ModCreativeTabs;
import dev.thou.craftnotify.registry.ModMenus;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

@Mod(CraftNotify.MOD_ID)
public final class CraftNotify {
    public static final String MOD_ID = "craft_notify";
    public static final String LEGACY_MOD_ID = "otherworld_calling";
    public static final String EARLIEST_MOD_ID = "redstone_messenger";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ResourceLocation legacyId(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public CraftNotify(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(this::addCreativeTabContents);
        modBus.addListener(ModNetworking::register);
        modBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.register(this);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.NOTIFIER.get(),
                (notifier, side) -> notifier.energyStorage()
        );
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModBlocks.NOTIFIER_ITEM);
            event.accept(ModBlocks.ANTENNA_ITEM);
        }
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        NotifyCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void serverStarting(ServerStartingEvent event) {
        SecretChannelStore.reload();
        NotificationDispatcher.start();
        WebhookCallbackServer.reload();
    }

    @SubscribeEvent
    public void serverStopping(ServerStoppingEvent event) {
        WebhookCallbackServer.stop();
        NotificationDispatcher.stop();
    }
}
