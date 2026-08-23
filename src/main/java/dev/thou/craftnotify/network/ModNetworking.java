package dev.thou.craftnotify.network;

import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import dev.thou.craftnotify.menu.NotifierMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(UpdateNotifierPayload.TYPE, UpdateNotifierPayload.STREAM_CODEC,
                ModNetworking::handleUpdate);
        registrar.playToServer(TestNotifierPayload.TYPE, TestNotifierPayload.STREAM_CODEC,
                ModNetworking::handleTest);
    }

    private static void handleUpdate(UpdateNotifierPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
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
                payload.cooldownSeconds(),
                payload.enabled()
        );
        if (saved) {
            player.displayClientMessage(Component.translatable("message.craft_notify.saved"), false);
        } else {
            player.displayClientMessage(Component.translatable("message.craft_notify.save_rejected"), false);
        }
    }

    private static void handleTest(TestNotifierPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof NotifierMenu menu)
                || !menu.blockPos().equals(payload.pos())
                || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(payload.pos()) instanceof NotifierBlockEntity notifier)) {
            return;
        }
        notifier.test(level, player, payload.label(), payload.channelId(), payload.title(), payload.content());
    }
}
