package dev.thou.craftnotify.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.blockentity.AntennaBlockEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public final class AntennaArmRenderer implements BlockEntityRenderer<AntennaBlockEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(CraftNotify.id("antenna_arm"), "main");
    private static final ResourceLocation COPPER = CraftNotify.id("textures/block/terminal_copper.png");
    private static final ResourceLocation COIL = CraftNotify.id("textures/block/antenna_coil.png");
    private static final float FOLD_DEGREES = 82.0F;
    private static final float[] LAYER_Y = {1.5F, 2.0F + 9.0F / 16.0F};
    private static final float[] LAYER_DELAY = {0.0F, 0.16F};

    private final ModelPart arm;
    private final ModelPart tip;

    public AntennaArmRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        this.arm = root.getChild("arm");
        this.tip = root.getChild("tip");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -0.85F, 1.6F, 2.0F, 1.7F, 5.2F),
                PartPose.ZERO);
        root.addOrReplaceChild("tip",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.2F, -1.15F, 6.6F, 2.4F, 2.3F, 1.6F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void render(AntennaBlockEntity antenna, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float deploy = antenna.deployAmount(partialTick);
        VertexConsumer copper = bufferSource.getBuffer(RenderType.entitySolid(COPPER));
        VertexConsumer coil = bufferSource.getBuffer(RenderType.entitySolid(COIL));
        for (int layer = 0; layer < 2; layer++) {
            float local = layerProgress(deploy, LAYER_DELAY[layer]);
            float fold = -(1.0F - local) * FOLD_DEGREES;
            for (int dir = 0; dir < 4; dir++) {
                poseStack.pushPose();
                poseStack.translate(0.5, LAYER_Y[layer], 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(dir * 90.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(fold));
                arm.render(poseStack, copper, packedLight, OverlayTexture.NO_OVERLAY);
                tip.render(poseStack, coil, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
    }

    private static float layerProgress(float deploy, float delay) {
        float span = 1.0F - delay;
        float t = Math.max(0.0F, Math.min(1.0F, (deploy - delay) / span));
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    @Override
    public boolean shouldRenderOffScreen(AntennaBlockEntity antenna) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public AABB getRenderBoundingBox(AntennaBlockEntity antenna) {
        return new AABB(antenna.getBlockPos()).expandTowards(0.0, 3.0, 0.0).inflate(1.25);
    }
}
