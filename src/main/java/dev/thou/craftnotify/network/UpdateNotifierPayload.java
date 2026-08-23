package dev.thou.craftnotify.network;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateNotifierPayload(BlockPos pos, int revision, String label, String channelId,
                                    String title, String content, int cooldownSeconds, boolean enabled)
        implements CustomPacketPayload {
    public static final Type<UpdateNotifierPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CraftNotify.MOD_ID, "update_notifier"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateNotifierPayload> STREAM_CODEC =
            StreamCodec.of(UpdateNotifierPayload::encode, UpdateNotifierPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, UpdateNotifierPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.revision);
        buf.writeUtf(payload.label, NotifierBlockEntity.MAX_LABEL_LENGTH);
        buf.writeUtf(payload.channelId, NotifierBlockEntity.MAX_CHANNEL_LENGTH);
        buf.writeUtf(payload.title, NotifierBlockEntity.MAX_TITLE_LENGTH);
        buf.writeUtf(payload.content, NotifierBlockEntity.MAX_CONTENT_LENGTH);
        buf.writeVarInt(payload.cooldownSeconds);
        buf.writeBoolean(payload.enabled);
    }

    private static UpdateNotifierPayload decode(RegistryFriendlyByteBuf buf) {
        return new UpdateNotifierPayload(
                buf.readBlockPos(),
                buf.readVarInt(),
                buf.readUtf(NotifierBlockEntity.MAX_LABEL_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_CHANNEL_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_TITLE_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_CONTENT_LENGTH),
                buf.readVarInt(),
                buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
