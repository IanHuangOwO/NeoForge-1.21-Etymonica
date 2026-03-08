package org.iansaididontcare.etymonica.block.entity.infusionaltar;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;

public class AltarPartBlockEntity extends BlockEntity {
    private BlockPos masterPos;
    private Identifier originalBlock;
    private boolean reverting = false;

    public AltarPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALTAR_PART_BE.get(), pos, state);
    }

    public void setMetadata(BlockPos masterPos, Identifier originalBlock) {
        this.masterPos = masterPos;
        this.originalBlock = originalBlock;
        setChanged();
    }

    public BlockPos getMasterPos() { return masterPos; }
    public Identifier getOriginalBlock() { return originalBlock; }
    
    public void setReverting(boolean reverting) { this.reverting = reverting; }
    public boolean isReverting() { return reverting; }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (masterPos != null) {
            output.putLong("master_pos", masterPos.asLong());
        }
        if (originalBlock != null) {
            output.putString("original_block", originalBlock.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.getLong("master_pos").ifPresent(l -> masterPos = BlockPos.of(l));
        input.getString("original_block").ifPresent(s -> originalBlock = Identifier.parse(s));
    }
}
