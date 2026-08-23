package dev.thou.craftnotify.network;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import dev.thou.craftnotify.menu.NotifierMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class ModNetworking {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CraftNotify.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private ModNetworking() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, UpdateNotifierPayload.class,
                UpdateNotifierPayload::encode, UpdateNotifierPayload::decode, ModNetworking::handleUpdate);
        CHANNEL.registerMessage(id, TestNotifierPayload.class,
                TestNotifierPayload::encode, TestNotifierPayload::decode, ModNetworking::handleTest);
    }

    private static void handleUpdate(UpdateNotifierPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null
                    || !(player.containerMenu instanceof NotifierMenu menu)
                    || !menu.blockPos().equals(payload.pos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.pos()) instanceof NotifierBlockEntity notifier)) {
                return;
            }
            boolean saved = notifier.configure(
                    player,
                    payload.revision(),
                    payload.label(),
                    payload.channelId(),
                    payload.title(),
                    payload.content(),
                    payload.cooldownSeconds()
            );
            if (saved) {
                player.displayClientMessage(Component.translatable("message.craft_notify.saved"), false);
            } else {
                player.displayClientMessage(Component.translatable("message.craft_notify.save_rejected"), false);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleTest(TestNotifierPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null
                    || !(player.containerMenu instanceof NotifierMenu menu)
                    || !menu.blockPos().equals(payload.pos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.pos()) instanceof NotifierBlockEntity notifier)) {
                return;
            }
            notifier.test(level, player, payload.label(), payload.channelId(), payload.title(), payload.content());
        });
        context.setPacketHandled(true);
    }
}
