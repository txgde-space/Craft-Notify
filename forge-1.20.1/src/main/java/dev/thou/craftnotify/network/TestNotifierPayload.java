package dev.thou.craftnotify.network;

import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record TestNotifierPayload(BlockPos pos, String label, String channelId, String title, String content) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(label, NotifierBlockEntity.MAX_LABEL_LENGTH);
        buf.writeUtf(channelId, NotifierBlockEntity.MAX_CHANNEL_LENGTH);
        buf.writeUtf(title, NotifierBlockEntity.MAX_TITLE_LENGTH);
        buf.writeUtf(content, NotifierBlockEntity.MAX_CONTENT_LENGTH);
    }

    public static TestNotifierPayload decode(FriendlyByteBuf buf) {
        return new TestNotifierPayload(
                buf.readBlockPos(),
                buf.readUtf(NotifierBlockEntity.MAX_LABEL_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_CHANNEL_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_TITLE_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_CONTENT_LENGTH)
        );
    }
}
