package org.iansaididontcare.etymonica.block.entity.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

public class InfusionAltarBlockEntityRenderState extends BlockEntityRenderState {
    public BlockPos lightPosition;
    public Level blockEntityLevel;
    public float rotation;
    public long gameTime;

    // Use a list of render states for multiple items
    public final List<ItemStackRenderState> itemStates = new ArrayList<>();

    public InfusionAltarBlockEntityRenderState() {
        // Pre-fill some states to avoid constant allocation
        for (int i = 0; i < 16; i++) {
            itemStates.add(new ItemStackRenderState());
        }
    }
    
    public int activeCount = 0;
}
