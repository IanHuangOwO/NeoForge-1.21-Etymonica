package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BrainInAJarBlockEntity extends BlockEntity {
    public static final String STORED_MILLIBUCKETS_KEY = "stored_mb";
    public static final int CAPACITY_MILLIBUCKETS = 8_000;
    private static final int XP_TO_MILLIBUCKETS = 20;
    private static final int XP_PER_DRAIN = 10;
    private static final int DRAIN_INTERVAL_TICKS = 20;
    private static final double DRAIN_RADIUS = 5.0D;

    private int storedMillibuckets;

    public BrainInAJarBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BRAIN_IN_A_JAR_BE.get(), pos, blockState);
    }

    public int getStoredMillibuckets() {
        return storedMillibuckets;
    }

    public void setStoredMillibuckets(int amount) {
        storedMillibuckets = Math.max(0, Math.min(CAPACITY_MILLIBUCKETS, amount));
    }

    public boolean canExtract(int amount) {
        return amount > 0 && storedMillibuckets >= amount;
    }

    public boolean canInsert(int amount) {
        return amount > 0 && storedMillibuckets + amount <= CAPACITY_MILLIBUCKETS;
    }

    public boolean tryExtract(Level level, BlockPos pos, BlockState state, int amount) {
        if (!canExtract(amount)) {
            return false;
        }

        storedMillibuckets -= amount;
        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
        return true;
    }

    public boolean tryInsert(Level level, BlockPos pos, BlockState state, int amount) {
        if (!canInsert(amount)) {
            return false;
        }

        storedMillibuckets += amount;
        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
        return true;
    }

    public void tickServer(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % DRAIN_INTERVAL_TICKS != 0 || storedMillibuckets >= CAPACITY_MILLIBUCKETS) {
            return;
        }

        int before = storedMillibuckets;
        int remainingXpDrain = XP_PER_DRAIN;

        for (Player player : level.getEntitiesOfClass(Player.class, getDrainAabb(pos))) {
            while (remainingXpDrain > 0
                    && storedMillibuckets < CAPACITY_MILLIBUCKETS
                    && (player.experienceLevel > 0 || player.experienceProgress > 0.0F)) {
                player.giveExperiencePoints(-1);
                storedMillibuckets = Math.min(CAPACITY_MILLIBUCKETS, storedMillibuckets + XP_TO_MILLIBUCKETS);
                remainingXpDrain--;
            }

            if (remainingXpDrain <= 0 || storedMillibuckets >= CAPACITY_MILLIBUCKETS) {
                break;
            }
        }

        if (storedMillibuckets != before) {
            setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
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
        storedMillibuckets = input.getInt(STORED_MILLIBUCKETS_KEY).orElse(0);
    }

    private static net.minecraft.world.phys.AABB getDrainAabb(BlockPos pos) {
        return new net.minecraft.world.phys.AABB(pos).inflate(DRAIN_RADIUS);
    }
}
