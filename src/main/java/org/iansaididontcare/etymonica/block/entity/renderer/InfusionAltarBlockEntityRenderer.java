package org.iansaididontcare.etymonica.block.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.iansaididontcare.etymonica.block.entity.AbstractInfusionAltarBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class InfusionAltarBlockEntityRenderer implements BlockEntityRenderer<AbstractInfusionAltarBlockEntity, InfusionAltarBlockEntityRenderState> {
    private final ItemModelResolver itemModelResolver;

    public InfusionAltarBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public InfusionAltarBlockEntityRenderState createRenderState() {
        return new InfusionAltarBlockEntityRenderState();
    }

    @Override
    public void extractRenderState(AbstractInfusionAltarBlockEntity blockEntity, InfusionAltarBlockEntityRenderState renderState, float partialTick,
                                   Vec3 cameraPosition, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPosition, breakProgress);

        renderState.lightPosition = blockEntity.getBlockPos();
        renderState.blockEntityLevel = blockEntity.getLevel();
        renderState.rotation = blockEntity.getRenderingRotation();

        itemModelResolver.updateForTopItem(renderState.itemStackRenderState,
                blockEntity.inventory.getStackInSlot(0), ItemDisplayContext.FIXED, blockEntity.getLevel(), null, 0);
    }

    @Override
    public void submit(InfusionAltarBlockEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();

        // Adjust translation based on your Infusion Altar model height.
        // Pedestal uses 1.15f, Altar might need something similar or higher.
        poseStack.translate(0.5f, 1.15f, 0.5f);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.rotation));

        renderState.itemStackRenderState.submit(poseStack, submitNodeCollector, getLightLevel(renderState.blockEntityLevel,
                renderState.lightPosition), OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }

    private int getLightLevel(Level level, BlockPos pos) {
        if (level == null) return 0;
        int bLight = level.getBrightness(LightLayer.BLOCK, pos);
        int sLight = level.getBrightness(LightLayer.SKY, pos);
        return LightTexture.pack(bLight, sLight);
    }
}
