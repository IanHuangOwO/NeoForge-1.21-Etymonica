package org.iansaididontcare.etymonica.block.entity.jar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.iansaididontcare.etymonica.block.entity.AbstractJarBlockEntity;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.registry.jar.api.JarTypeStats;
import org.iansaididontcare.etymonica.registry.jar.data.JarData;

public class JarBlockEntity extends AbstractJarBlockEntity {

    public JarBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.JAR_BE.get(), pos, blockState);
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
        // Jar does not generate or drain XP.
    }
}
