package dev.thou.craftnotify.network;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TestNotifierPayload(BlockPos pos, String label, String channelId, String title, String content)
        implements CustomPacketPayload {
    public static final Type<TestNotifierPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CraftNotify.MOD_ID, "test_notifier"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TestNotifierPayload> STREAM_CODEC =
            StreamCodec.of(TestNotifierPayload::encode, TestNotifierPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TestNotifierPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeUtf(payload.label, NotifierBlockEntity.MAX_LABEL_LENGTH);
        buf.writeUtf(payload.channelId, NotifierBlockEntity.MAX_CHANNEL_LENGTH);
        buf.writeUtf(payload.title, NotifierBlockEntity.MAX_TITLE_LENGTH);
        buf.writeUtf(payload.content, NotifierBlockEntity.MAX_CONTENT_LENGTH);
    }

    private static TestNotifierPayload decode(RegistryFriendlyByteBuf buf) {
        return new TestNotifierPayload(
                buf.readBlockPos(),
                buf.readUtf(NotifierBlockEntity.MAX_LABEL_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_CHANNEL_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_TITLE_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_CONTENT_LENGTH)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
