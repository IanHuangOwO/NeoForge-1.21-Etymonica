package org.iansaididontcare.etymonica.block.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.iansaididontcare.etymonica.block.custom.AbstractInfusionAltarBlock;
import net.minecraft.world.phys.Vec3;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarStats;
import org.iansaididontcare.etymonica.registry.infusion.data.InfusionAltarData;
import org.jetbrains.annotations.Nullable;

public class InfusionAltarBlockEntityRenderer implements BlockEntityRenderer<AbstractInfusionAltarBlockEntity, InfusionAltarBlockEntityRenderState> {
    private final ItemModelResolver itemModelResolver;

    private static final float SELF_ROTATION_SPEED = 1f;

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
        renderState.gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0;

        renderState.isFormed = blockEntity.isFormed();
        BlockState blockState = blockEntity.getBlockState();
        if (blockState.hasProperty(AbstractInfusionAltarBlock.FORMED)) {
            renderState.altarBlock = blockState.setValue(AbstractInfusionAltarBlock.FORMED, false);
        } else {
            renderState.altarBlock = blockState;
        }
        
        String tierId = getTierIdFromBlock(blockEntity.getBlockState());
        InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
        renderState.multiblockRadius = stats.multiblockRadius();
        renderState.multiblockBlock = BuiltInRegistries.BLOCK.get(stats.multiblockBlock())
                .orElseThrow(() -> new IllegalStateException("Block not found: " + stats.multiblockBlock()))
                .value().defaultBlockState();

        ItemStack stack = blockEntity.getInventory().getStackInSlot(0);
        renderState.hasItem = !stack.isEmpty();
        if (renderState.hasItem) {
            itemModelResolver.updateForTopItem(
                    renderState.itemState,
                    stack,
                    ItemDisplayContext.FIXED,
                    blockEntity.getLevel(),
                    null,
                    0
            );
        }
    }

    private String getTierIdFromBlock(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.startsWith("infusion_altar_")) return path.substring("infusion_altar_".length());
        return "tier0";
    }

    @Override
    public void submit(InfusionAltarBlockEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        int light = getLightLevel(renderState.blockEntityLevel, renderState.lightPosition);

        // 1. Render Multiblock Overlays
        if (!renderState.isFormed) {
            if (renderState.multiblockBlock != null) {
                renderGhostRings(renderState, poseStack, submitNodeCollector, light);
            }
        } else {
            // Render the "Giant Placeholder" when formed
            renderFormedPlaceholder(renderState, poseStack, submitNodeCollector, light);
        }

        int itemLight = getFormedLightLevel(
                renderState.blockEntityLevel,
                renderState.lightPosition,
                Math.max(1, renderState.multiblockRadius)
        );

        // 2. Render centered item
        if (renderState.hasItem) {
            renderCenteredItem(renderState, poseStack, submitNodeCollector, itemLight);
        }
    }

    private void renderFormedPlaceholder(InfusionAltarBlockEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light) {
        poseStack.pushPose();

        float scale = Math.max(1.0f, renderState.multiblockRadius);
        int formedLight = getFormedLightLevel(renderState.blockEntityLevel, renderState.lightPosition, renderState.multiblockRadius);

        // Rotate and scale around the altar block center.
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.rotation * 0.5f));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        submitNodeCollector.submitBlock(poseStack, renderState.altarBlock, formedLight, OverlayTexture.NO_OVERLAY, 0);
        
        poseStack.popPose();
    }

    private void renderGhostRings(InfusionAltarBlockEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light) {
        int r = renderState.multiblockRadius;
        BlockState ghostState = renderState.multiblockBlock;

        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (Math.round(Math.sqrt(i * i + j * j)) == r) {
                    renderGhostBlock(poseStack, submitNodeCollector, ghostState, i, 0, j, light);
                    renderGhostBlock(poseStack, submitNodeCollector, ghostState, i, j, 0, light);
                    renderGhostBlock(poseStack, submitNodeCollector, ghostState, 0, i, j, light);
                }
            }
        }
    }

    private void renderGhostBlock(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, BlockState state, int dx, int dy, int dz, int light) {
        if (dx == 0 && dy == 0 && dz == 0) return;

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        poseStack.translate(0.25f, 0.25f, 0.25f);
        poseStack.scale(0.5f, 0.5f, 0.5f);

        submitNodeCollector.submitBlock(poseStack, state, light, OverlayTexture.NO_OVERLAY, 0);
        
        poseStack.popPose();
    }

    private void renderCenteredItem(InfusionAltarBlockEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.scale(1f, 1f, 1f);
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.rotation * SELF_ROTATION_SPEED));
        renderState.itemState.submit(poseStack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private int getLightLevel(Level level, BlockPos pos) {
        if (level == null) return 0;
        int bLight = level.getBrightness(LightLayer.BLOCK, pos);
        int sLight = level.getBrightness(LightLayer.SKY, pos);
        return LightTexture.pack(bLight, sLight);
    }

    private int getFormedLightLevel(Level level, BlockPos pos, int radius) {
        if (level == null || pos == null) return 0;

        int r = Math.max(1, radius);
        int maxBlock = 0;
        int maxSky = 0;

        BlockPos[] samplePoints = new BlockPos[] {
                pos,
                pos.above(r),
                pos.offset(r, r, 0),
                pos.offset(-r, r, 0),
                pos.offset(0, r, r),
                pos.offset(0, r, -r)
        };

        for (BlockPos sample : samplePoints) {
            maxBlock = Math.max(maxBlock, level.getBrightness(LightLayer.BLOCK, sample));
            maxSky = Math.max(maxSky, level.getBrightness(LightLayer.SKY, sample));
        }

        return LightTexture.pack(maxBlock, maxSky);
    }
}
