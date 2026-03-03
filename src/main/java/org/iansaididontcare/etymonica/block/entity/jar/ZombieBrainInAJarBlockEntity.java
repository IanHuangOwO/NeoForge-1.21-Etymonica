package org.iansaididontcare.etymonica.block.entity.jar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.iansaididontcare.etymonica.block.entity.AbstractJarBlockEntity;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.registry.jar.api.JarTypeStats;
import org.iansaididontcare.etymonica.registry.jar.api.ZombieJarStats;
import org.iansaididontcare.etymonica.registry.jar.data.JarData;

public class ZombieBrainInAJarBlockEntity extends AbstractJarBlockEntity {

    public ZombieBrainInAJarBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BRAIN_IN_A_JAR_BE.get(), pos, blockState);
    }

    private JarTypeStats getStats() {
        Identifier id = getLevel().registryAccess().lookupOrThrow(Registries.BLOCK).getKey(getBlockState().getBlock());
        return JarData.getJarType(id);
    }

    @Override
    protected int getCapacityMillibuckets() {
        return getStats().capacity();
    }

    @Override
    protected void tickGeneration(Level level, BlockPos pos, BlockState state) {
        JarTypeStats stats = getStats();
        ZombieJarStats zombie = stats.zombieSpecial().orElse(ZombieJarStats.DEFAULT);

        if (level.getGameTime() % zombie.interval() != 0) {
            return;
        }

        int capacity = stats.capacity();
        int remainingXpDrain = zombie.xpPerDrain();
        AABB drainAabb = new AABB(pos).inflate(zombie.radius());

        for (Player player : level.getEntitiesOfClass(Player.class, drainAabb)) {
            while (remainingXpDrain > 0
                    && getStoredMillibuckets() < capacity
                    && (player.experienceLevel > 0 || player.experienceProgress > 0.0F)) {
                player.giveExperiencePoints(-1);
                addStoredMillibuckets(zombie.xpToMb());
                remainingXpDrain--;
            }

            if (remainingXpDrain <= 0 || getStoredMillibuckets() >= capacity) {
                break;
            }
        }
    }
}
