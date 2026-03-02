package org.iansaididontcare.etymonica.block.entity.jar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.iansaididontcare.etymonica.block.entity.AbstractJarBlockEntity;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;

public class ZombieBrainInAJarBlockEntity extends AbstractJarBlockEntity {
    public static final int CAPACITY_MILLIBUCKETS = 8_000;

    private static final int XP_TO_MILLIBUCKETS = 20;
    private static final int XP_PER_DRAIN = 10;
    private static final int DRAIN_INTERVAL_TICKS = 20;
    private static final double DRAIN_RADIUS = 5.0D;

    public ZombieBrainInAJarBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BRAIN_IN_A_JAR_BE.get(), pos, blockState);
    }

    @Override
    protected int getCapacityMillibuckets() {
        return CAPACITY_MILLIBUCKETS;
    }

    @Override
    protected void tickGeneration(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % DRAIN_INTERVAL_TICKS != 0) {
            return;
        }

        int remainingXpDrain = XP_PER_DRAIN;
        for (Player player : level.getEntitiesOfClass(Player.class, getDrainAabb(pos))) {
            while (remainingXpDrain > 0
                    && getStoredMillibuckets() < getCapacityMillibuckets()
                    && (player.experienceLevel > 0 || player.experienceProgress > 0.0F)) {
                player.giveExperiencePoints(-1);
                addStoredMillibuckets(XP_TO_MILLIBUCKETS);
                remainingXpDrain--;
            }

            if (remainingXpDrain <= 0 || getStoredMillibuckets() >= getCapacityMillibuckets()) {
                break;
            }
        }
    }

    private static AABB getDrainAabb(BlockPos pos) {
        return new AABB(pos).inflate(DRAIN_RADIUS);
    }
}
