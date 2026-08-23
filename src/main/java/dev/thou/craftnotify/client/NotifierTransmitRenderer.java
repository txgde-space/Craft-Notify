package dev.thou.craftnotify.client;

import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;

public final class NotifierTransmitRenderer implements BlockEntityRenderer<NotifierBlockEntity> {
    private static final int BEAM_COLOR = 0xFF8AF0E8;

    public NotifierTransmitRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NotifierBlockEntity notifier, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float elapsed = notifier.sendElapsed(partialTick);
        if (elapsed < 0.0F || elapsed >= NotifierBlockEntity.SEND_ANIM_TICKS) {
            return;
        }
        BlockPos antenna = notifier.antennaBasePos();
        if (antenna == null) {
            return;
        }

        if (elapsed < NotifierBlockEntity.CHARGE_TICKS) {
            return;
        }

        float height = NotifierBlockEntity.beamHeightAt(elapsed);
        float radius = NotifierBlockEntity.beamRadiusAt(elapsed);
        if (height < 0.35F || radius < 0.015F) {
            return;
        }

        BlockPos origin = notifier.getBlockPos();
        poseStack.pushPose();
        poseStack.translate(
                antenna.getX() - origin.getX(),
                antenna.getY() - origin.getY() + 3.0,
                antenna.getZ() - origin.getZ()
        );
        poseStack.scale(1.0F, height, 1.0F);
        long gameTime = notifier.getLevel() != null ? notifier.getLevel().getGameTime() : 0L;
        BeaconRenderer.renderBeaconBeam(
                poseStack,
                bufferSource,
                BeaconRenderer.BEAM_LOCATION,
                partialTick,
                1.15F,
                gameTime,
                0,
                1,
                BEAM_COLOR,
                radius,
                radius * 1.7F
        );
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(NotifierBlockEntity notifier) {
        return notifier.isTransmitAnimating();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRender(NotifierBlockEntity notifier, Vec3 cameraPos) {
        return notifier.isTransmitAnimating();
    }

    @Override
    public AABB getRenderBoundingBox(NotifierBlockEntity notifier) {
        if (!notifier.isTransmitAnimating()) {
            return new AABB(notifier.getBlockPos());
        }
        BlockPos antenna = notifier.antennaBasePos();
        BlockPos origin = antenna != null ? antenna : notifier.getBlockPos();
        return new AABB(origin).expandTowards(0.0, NotifierBlockEntity.BEAM_HEIGHT + 4.0, 0.0).inflate(1.5);
    }
}
