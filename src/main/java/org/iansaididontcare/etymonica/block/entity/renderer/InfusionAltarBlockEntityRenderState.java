package org.iansaididontcare.etymonica.block.entity.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class InfusionAltarBlockEntityRenderState extends BlockEntityRenderState {
    public BlockPos lightPosition;
    public Level blockEntityLevel;
    public float rotation;
    public long gameTime;

    // Altar state for the giant model
    public BlockState altarBlock;

    // Multiblock preview info
    public boolean isFormed;
    public int multiblockRadius;
    public BlockState multiblockBlock;
    public int glassSphereRadius;
    public BlockState glassSphereBlock;

    public final ItemStackRenderState itemState = new ItemStackRenderState();
    public boolean hasItem = false;
}
