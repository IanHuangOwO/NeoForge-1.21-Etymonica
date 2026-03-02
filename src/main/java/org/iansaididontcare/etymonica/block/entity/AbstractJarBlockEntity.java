package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class AbstractJarBlockEntity extends BlockEntity {
    public static final String STORED_MILLIBUCKETS_KEY = "stored_mb";

    private int storedMillibuckets;

    protected AbstractJarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public int getStoredMillibuckets() {
        return storedMillibuckets;
    }

    public void setStoredMillibuckets(int amount) {
        storedMillibuckets = clampStored(amount);
    }

    public boolean canExtract(int amount) {
        return amount > 0 && storedMillibuckets >= amount;
    }

    public boolean canInsert(int amount) {
        return amount > 0 && storedMillibuckets + amount <= getCapacityMillibuckets();
    }

    public boolean tryExtract(Level level, BlockPos pos, BlockState state, int amount) {
        if (!canExtract(amount)) {
            return false;
        }

        storedMillibuckets -= amount;
        sync(level, pos, state);
        return true;
    }

    public boolean tryInsert(Level level, BlockPos pos, BlockState state, int amount) {
        if (!canInsert(amount)) {
            return false;
        }

        storedMillibuckets += amount;
        sync(level, pos, state);
        return true;
    }

    public final void tickServer(Level level, BlockPos pos, BlockState state) {
        if (storedMillibuckets >= getCapacityMillibuckets()) {
            return;
        }

        int before = storedMillibuckets;
        tickGeneration(level, pos, state);
        storedMillibuckets = clampStored(storedMillibuckets);
        if (storedMillibuckets != before) {
            sync(level, pos, state);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(STORED_MILLIBUCKETS_KEY, storedMillibuckets);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storedMillibuckets = clampStored(input.getInt(STORED_MILLIBUCKETS_KEY).orElse(0));
    }

    protected final void addStoredMillibuckets(int amount) {
        if (amount <= 0) {
            return;
        }
        storedMillibuckets = clampStored(storedMillibuckets + amount);
    }

    protected abstract int getCapacityMillibuckets();

    protected abstract void tickGeneration(Level level, BlockPos pos, BlockState state);

    private int clampStored(int amount) {
        return Math.max(0, Math.min(getCapacityMillibuckets(), amount));
    }

    protected final void sync(Level level, BlockPos pos, BlockState state) {
        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }
}
