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
import net.minecraft.world.phys.Vec3;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarStats;
import org.iansaididontcare.etymonica.registry.infusion.data.InfusionAltarData;
import org.jetbrains.annotations.Nullable;

public class InfusionAltarBlockEntityRenderer implements BlockEntityRenderer<AbstractInfusionAltarBlockEntity, InfusionAltarBlockEntityRenderState> {
    private final ItemModelResolver itemModelResolver;

    // Controls for the orbital display
    private static final float ORBIT_HEIGHT = 1.5f;
    private static final float ORBIT_RADIUS = 1.5f;
    private static final float WAVE_AMPLITUDE_Y = 0.2f;
    private static final float WAVE_SPEED = 0.1f;
    private static final float SELF_ROTATION_SPEED = 1f;

    private static final int MAX_VISIBLE_ITEMS = 16;

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

        // Multiblock preview data
        renderState.isFormed = blockEntity.isFormed();
        
        // Extract tier stats for preview
        String tierId = getTierIdFromBlock(blockEntity.getBlockState());
        InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
        renderState.multiblockRadius = stats.multiblockRadius();
        renderState.multiblockBlock = BuiltInRegistries.BLOCK.get(stats.multiblockBlock())
                .orElseThrow(() -> new IllegalStateException("Block not found: " + stats.multiblockBlock()))
                .value().defaultBlockState();

        int stateIdx = 0;
        // Collect items from all slots, up to MAX_VISIBLE_ITEMS
        for (int slot = 0; slot < blockEntity.getInventory().getSlots() && stateIdx < MAX_VISIBLE_ITEMS; slot++) {
            ItemStack stack = blockEntity.getInventory().getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            int countToShow = Math.min(stack.getCount(), MAX_VISIBLE_ITEMS - stateIdx);
            
            for (int i = 0; i < countToShow; i++) {
                itemModelResolver.updateForTopItem(renderState.itemStates.get(stateIdx),
                        stack, ItemDisplayContext.FIXED, blockEntity.getLevel(), null, stateIdx);
                stateIdx++;
            }
        }
        renderState.activeCount = stateIdx;
    }

    private String getTierIdFromBlock(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.startsWith("infusion_altar_")) return path.substring("infusion_altar_".length());
        return "tier0";
    }

    @Override
    public void submit(InfusionAltarBlockEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        int light = getLightLevel(renderState.blockEntityLevel, renderState.lightPosition);

        // 1. Render Ghost Blocks if not formed
        if (!renderState.isFormed && renderState.multiblockBlock != null) {
            renderGhostRings(renderState, poseStack, submitNodeCollector, light);
        }

        // 2. Render Spinning Items
        if (renderState.activeCount > 0) {
            renderSpinningItems(renderState, poseStack, submitNodeCollector, light);
        }
    }

    private void renderGhostRings(InfusionAltarBlockEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light) {
        int r = renderState.multiblockRadius;
        BlockState ghostState = renderState.multiblockBlock;

        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (Math.round(Math.sqrt(i * i + j * j)) == r) {
                    // XZ Ring
                    renderGhostBlock(poseStack, submitNodeCollector, ghostState, i, 0, j, light);
                    // XY Ring
                    renderGhostBlock(poseStack, submitNodeCollector, ghostState, i, j, 0, light);
                    // YZ Ring
                    renderGhostBlock(poseStack, submitNodeCollector, ghostState, 0, i, j, light);
                }
            }
        }
    }

    private void renderGhostBlock(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, BlockState state, int dx, int dy, int dz, int light) {
        // Skip the center block
        if (dx == 0 && dy == 0 && dz == 0) return;

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        
        // Center the 0.5 scale block within the 1x1x1 space
        poseStack.translate(0.25f, 0.25f, 0.25f);
        poseStack.scale(0.5f, 0.5f, 0.5f);

        submitNodeCollector.submitBlock(poseStack, state, light, OverlayTexture.NO_OVERLAY, -1);
        
        poseStack.popPose();
    }

    private void renderSpinningItems(InfusionAltarBlockEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light) {
        for (int i = 0; i < renderState.activeCount; i++) {
            poseStack.pushPose();

            float offsetX = 0;
            float offsetZ = 0;
            float offsetY = ORBIT_HEIGHT;

            if (renderState.activeCount > 1) {
                float angle = (float) (i * (2.0 * Math.PI / renderState.activeCount));
                float rotationRad = (float) Math.toRadians(renderState.rotation);
                
                offsetX = (float) (Math.cos(angle + rotationRad) * ORBIT_RADIUS);
                offsetZ = (float) (Math.sin(angle + rotationRad) * ORBIT_RADIUS);
                
                float wave = (float) Math.sin((renderState.gameTime + i * 10) * WAVE_SPEED) * WAVE_AMPLITUDE_Y;
                offsetY += wave;
            }

            poseStack.translate(0.5f + offsetX, offsetY, 0.5f + offsetZ);
            poseStack.scale(0.4f, 0.4f, 0.4f);
            poseStack.mulPose(Axis.YP.rotationDegrees(renderState.rotation * SELF_ROTATION_SPEED + (i * 45)));

            renderState.itemStates.get(i).submit(poseStack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, 0);

            poseStack.popPose();
        }
    }

    private int getLightLevel(Level level, BlockPos pos) {
        if (level == null) return 0;
        int bLight = level.getBrightness(LightLayer.BLOCK, pos);
        int sLight = level.getBrightness(LightLayer.SKY, pos);
        return LightTexture.pack(bLight, sLight);
    }
}
