package dev.thou.craftnotify;

import com.mojang.logging.LogUtils;
import dev.thou.craftnotify.command.NotifyCommands;
import dev.thou.craftnotify.notification.NotificationDispatcher;
import dev.thou.craftnotify.notification.SecretChannelStore;
import dev.thou.craftnotify.notification.WebhookCallbackServer;
import dev.thou.craftnotify.preset.GuiPresetStore;
import dev.thou.craftnotify.network.ModNetworking;
import dev.thou.craftnotify.registry.ModBlockEntities;
import dev.thou.craftnotify.registry.ModBlocks;
import dev.thou.craftnotify.registry.ModCreativeTabs;
import dev.thou.craftnotify.registry.ModMenus;
import dev.thou.craftnotify.registry.ModSounds;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CraftNotify.MOD_ID)
public final class CraftNotify {
    public static final String MOD_ID = "craft_notify";
    public static final String LEGACY_MOD_ID = "otherworld_calling";
    public static final String EARLIEST_MOD_ID = "redstone_messenger";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static ResourceLocation legacyId(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public CraftNotify() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModSounds.register(modBus);
        ModCreativeTabs.register(modBus);
        ModNetworking.register();
        modBus.addListener(this::addCreativeTabContents);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModBlocks.NOTIFIER_ITEM.get());
            event.accept(ModBlocks.ANTENNA_ITEM.get());
        }
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        NotifyCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void serverStarting(ServerStartingEvent event) {
        SecretChannelStore.reload();
        GuiPresetStore.reload();
        NotificationDispatcher.start();
        WebhookCallbackServer.reload();
    }

    @SubscribeEvent
    public void serverStopping(ServerStoppingEvent event) {
        WebhookCallbackServer.stop();
        NotificationDispatcher.stop();
    }
}
