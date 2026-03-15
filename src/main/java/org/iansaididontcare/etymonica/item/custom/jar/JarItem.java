package org.iansaididontcare.etymonica.item.custom.jar;

import net.minecraft.world.level.block.Block;
import org.iansaididontcare.etymonica.item.custom.AbstractJarItem;

public class JarItem extends AbstractJarItem {
    public JarItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected String getTooltipKey() {
        return "tooltip.etymonica.jar.storage";
    }
}
