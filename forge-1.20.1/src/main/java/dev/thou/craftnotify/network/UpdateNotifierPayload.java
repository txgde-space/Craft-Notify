package dev.thou.craftnotify.network;

import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record UpdateNotifierPayload(BlockPos pos, int revision, String label, String channelId,
                                    String title, String content, int cooldownSeconds, boolean enabled) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(revision);
        buf.writeUtf(label, NotifierBlockEntity.MAX_LABEL_LENGTH);
        buf.writeUtf(channelId, NotifierBlockEntity.MAX_CHANNEL_LENGTH);
        buf.writeUtf(title, NotifierBlockEntity.MAX_TITLE_LENGTH);
        buf.writeUtf(content, NotifierBlockEntity.MAX_CONTENT_LENGTH);
        buf.writeVarInt(cooldownSeconds);
        buf.writeBoolean(enabled);
    }

    public static UpdateNotifierPayload decode(FriendlyByteBuf buf) {
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
}
